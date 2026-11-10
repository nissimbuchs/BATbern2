package ch.batbern.events.service;

import ch.batbern.events.client.UserApiClient;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.NewsletterRecipient;
import ch.batbern.events.domain.NewsletterRecipientId;
import ch.batbern.events.domain.NewsletterSend;
import ch.batbern.events.domain.Registration;
import ch.batbern.events.exception.DuplicateNewsletterSendException;
import ch.batbern.events.exception.SlidesOnlineAlreadySentException;
import ch.batbern.events.mailcenter.render.MailContent;
import ch.batbern.events.mailcenter.render.MailPipeline;
import ch.batbern.events.mailcenter.render.OwnText;
import ch.batbern.events.mailcenter.variables.MailVariable;
import ch.batbern.events.repository.NewsletterRecipientRepository;
import ch.batbern.events.repository.NewsletterSendRepository;
import ch.batbern.events.repository.NewsletterSubscriberRepository;
import ch.batbern.events.repository.RegistrationRepository;
import ch.batbern.shared.mail.MailContext;
import ch.batbern.shared.mail.MailTrigger;
import ch.batbern.shared.service.EmailService;
import ch.batbern.shared.utils.LoggingUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Story 7.3 — dedicated sibling send for the "The Slides Are Online" mail.
 *
 * <p>This is intentionally <b>separate</b> from {@link NewsletterEmailService}: that service
 * blasts the global {@code newsletter_subscribers} pool, whereas this one targets an event's
 * <b>active registrants</b> ({@code registered}/{@code confirmed}) with a one-shot, event-triggered
 * mail. Keeping it standalone means the slides-online send has its own audit, its own guard, and
 * its own metrics, and can never interfere with (or be blocked by) a subscriber-newsletter send.
 *
 * <p>It does, however, <b>reuse</b> the proven send infrastructure:
 * <ul>
 *   <li>the {@code newsletter_sends} / {@code newsletter_recipients} audit tables, discriminated
 *       by {@code template_key='slides-online'};</li>
 *   <li>the {@link EmailService} synchronous send path + 70&nbsp;ms SES throttle;</li>
 *   <li>per-recipient failure isolation (one transient SES error never aborts the rest);</li>
 *   <li>orphan recovery — {@link NewsletterEmailService#recoverOrphanedSends()} already sweeps ALL
 *       {@code IN_PROGRESS}/{@code PENDING} sends regardless of template, so slides-online sends are
 *       recovered too.</li>
 * </ul>
 *
 * <h2>Per-recipient locale (AC4)</h2>
 * The template language is the attendee's web-language preference: {@code de*} → German,
 * {@code en} → English, <b>anything else / unknown → German fallback</b> (deliberately German, not
 * English, for this feature).
 *
 * <h2>Double-send guard (AC5)</h2>
 * Rejects with 409 if a slides-online send is already {@code IN_PROGRESS} for the event
 * ({@link DuplicateNewsletterSendException}) or if a terminal one already exists
 * ({@link SlidesOnlineAlreadySentException}).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SlidesOnlineEmailService {

    /**
     * Registrant statuses that receive the mail. Uses the canonical "confirmed/attending" set
     * ({@code registered} + {@code confirmed} + {@code attended}) — NOT just registered/confirmed:
     * a registrant notice (e.g. slides-online) is sent POST-event (the seeded task is due event+1d),
     * by which time real registrants of a completed event are {@code attended}. Excludes
     * {@code waitlist} (never got a seat) and {@code cancelled}.
     */
    static final List<String> ACTIVE_REGISTRANT_STATUSES = Registration.CONFIRMED_STATUSES;

    /** The statuses an organizer may choose for own text to registrants (API values). */
    public static final List<String> CHOOSABLE_REGISTRANT_STATUSES =
            List.of("REGISTERED", "CONFIRMED", "ATTENDED", "NO_SHOW");

    /** Chosen API statuses as stored on {@code registrations}; none chosen = the default audience. */
    public static List<String> registrantStatuses(List<String> chosen) {
        return chosen == null || chosen.isEmpty() ? ACTIVE_REGISTRANT_STATUSES
                : chosen.stream().map(st -> st.toLowerCase(Locale.ROOT)).toList();
    }

    static final String STATUS_PENDING = "PENDING";
    static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    static final String STATUS_COMPLETED = "COMPLETED";
    static final String STATUS_PARTIAL = "PARTIAL";
    static final String STATUS_FAILED = "FAILED";

    private final EmailService emailService;
    private final EmailTemplateService emailTemplateService;
    private final MailPipeline mailPipeline;
    private final NewsletterSendRepository sendRepository;
    private final NewsletterRecipientRepository recipientRepository;
    private final NewsletterSubscriberRepository subscriberRepository;
    private final RegistrationRepository registrationRepository;
    private final UserApiClient userApiClient;

    /** Same SES-throttle property as the subscriber newsletter (~14 mails/s at 70 ms). */
    @Value("${newsletter.send.rate-delay-ms:70}")
    private long sendRateDelayMs;

    @Value("${batbern.ses.configuration-set-name:#{null}}")
    private String configurationSetName;

    @Value("${app.base-url:https://batbern.ch}")
    private String baseUrl;

    /**
     * Self-reference via {@code @Lazy} so the call to the {@code @Async} worker goes through the
     * Spring proxy (a direct {@code this.xxx()} call would bypass the proxy and run synchronously).
     */
    private SlidesOnlineEmailService self;

    @Autowired
    public void setSelf(@Lazy SlidesOnlineEmailService self) {
        this.self = self;
    }

    // ── Public entry point ─────────────────────────────────────────────────────

    /**
     * Initiate a registrant-notice send for an event's active registrants using any
     * {@code REGISTRANT_NOTICE}-category template (e.g. {@code slides-online}).
     *
     * <p>Creates the audit row (status {@code PENDING}), launches the {@code @Async} worker, and
     * returns immediately. Guards against double-send per (event, templateKey).
     *
     * @throws IllegalArgumentException (400) if the template is not a REGISTRANT_NOTICE template
     * @throws DuplicateNewsletterSendException (409) if such a send is already IN_PROGRESS
     * @throws SlidesOnlineAlreadySentException (409) if such a send already completed for the event
     */
    public QueuedSend sendRegistrantNotice(Event event, String templateKey, String sentByUsername) {
        requireRegistrantNoticeTemplate(templateKey);
        UUID eventId = event.getId();

        // NOTE (PR #788 review item 3): the two guards below are read-then-act without an
        // enclosing transaction, so two near-simultaneous requests could both pass before either
        // writes the audit row (double-send). This is pre-existing behaviour inherited from
        // the former slides-online endpoint (removed by Story 17.1); the practical risk is low
        // (a single organizer clicking a UI button).
        // The robust fix is a partial unique index on (event_id, template_key) WHERE status IN
        // (PENDING, IN_PROGRESS, COMPLETED, PARTIAL) — tracked as a hardening backlog item, not
        // done here to avoid a Flyway migration in this frontend-focused PR.
        // AC5: in-progress guard, scoped to (event, templateKey).
        sendRepository.findFirstByEventIdAndTemplateKeyAndStatus(eventId, templateKey, STATUS_IN_PROGRESS)
                .ifPresent(active -> {
                    throw new DuplicateNewsletterSendException(
                            "A '" + templateKey + "' send is already in progress for event "
                            + event.getEventCode() + " (sendId=" + active.getId() + ")");
                });

        // AC5: a completed send of this template must not re-fire (one-shot, event-triggered).
        if (sendRepository.existsByEventIdAndTemplateKeyAndStatusIn(
                eventId, templateKey, List.of(STATUS_COMPLETED, STATUS_PARTIAL))) {
            throw new SlidesOnlineAlreadySentException(
                    "The '" + templateKey + "' mail has already been sent for event "
                    + event.getEventCode());
        }

        List<Registration> registrants =
                registrationRepository.findByEventIdAndStatusIn(eventId, ACTIVE_REGISTRANT_STATUSES);

        NewsletterSend saved = createSendAuditRecord(event, templateKey, sentByUsername, registrants.size());

        // Launch background job via the proxy so @Async is honoured.
        self.executeSlidesOnlineSendAsync(saved.getId(), event, templateKey);

        log.info("Registrant-notice send queued: sendId={}, event={}, template={}, registrants={}",
                saved.getId(), event.getEventCode(), templateKey, registrants.size());

        return new QueuedSend(saved.getId(), registrants.size());
    }

    /**
     * Story 16.5 AC9: own text to the registrants in {@code statuses}. Any own-text send in progress
     * is a 409; the one-shot-by-subject question is asked by the composer before this is called.
     * Everyone gets the text as written, with the layout in {@code locale}.
     */
    public QueuedSend sendRegistrantOwnText(Event event, String ownSubject, String ownBodyHtml,
            String locale, String sentByUsername, List<String> statuses) {
        UUID eventId = event.getId();
        sendRepository.findFirstByEventIdAndTemplateKeyAndStatus(eventId, OwnText.REGISTRANTS_ROW_KEY,
                STATUS_IN_PROGRESS).ifPresent(active -> {
                    throw new DuplicateNewsletterSendException("An own-text send to the registrants of "
                            + event.getEventCode() + " is in progress (sendId=" + active.getId() + ")");
                });
        List<Registration> registrants = registrationRepository.findByEventIdAndStatusIn(eventId, statuses);
        NewsletterSend saved = sendRepository.save(NewsletterSend.builder()
                .eventId(eventId)
                .templateKey(OwnText.REGISTRANTS_ROW_KEY)
                .reminder(false)
                .locale("en".equals(locale) ? "en" : "de")
                .sentAt(Instant.now())
                .sentByUsername(sentByUsername)
                .recipientCount(registrants.size())
                .status(STATUS_PENDING)
                .testMode(false)
                .contentSource("OWN_TEXT")
                .ownSubject(ownSubject)
                .ownBodyHtml(ownBodyHtml)
                .registrantStatuses(statuses.equals(ACTIVE_REGISTRANT_STATUSES) ? null : String.join(",", statuses))
                .build());
        self.executeSlidesOnlineSendAsync(saved.getId(), event, OwnText.REGISTRANTS_ROW_KEY);
        log.info("Registrant own-text send queued: sendId={}, event={}, registrants={}",
                saved.getId(), event.getEventCode(), registrants.size());
        return new QueuedSend(saved.getId(), registrants.size());
    }

    /** When own text with this subject last went to this event's registrants (COMPLETED or PARTIAL). */
    public Optional<Instant> ownTextAlreadySent(Event event, String ownSubject) {
        return sendRepository.findFirstByEventIdAndTemplateKeyAndOwnSubjectAndStatusInOrderBySentAtDesc(
                        event.getId(), OwnText.REGISTRANTS_ROW_KEY, ownSubject,
                        List.of(STATUS_COMPLETED, STATUS_PARTIAL))
                .map(NewsletterSend::getSentAt);
    }

    /** Own text as one registrant language renders it: shared values only (see {@link #renderBase}). */
    public RenderedMail renderOwnBase(Event event, MailContent content) {
        ch.batbern.events.mailcenter.render.RenderedMail mail =
                mailPipeline.render(content, buildVariables(event, content.locale()));
        return new RenderedMail(mail.subject(), mail.html());
    }

    /**
     * Guard: only templates categorised {@code REGISTRANT_NOTICE} may be sent to registrants.
     * Prevents a subscriber-newsletter template from being blasted to registrants by mistake
     * (the symmetric counterpart of {@code NewsletterEmailService#rejectRegistrantTemplate}).
     */
    private void requireRegistrantNoticeTemplate(String templateKey) {
        if (!isRegistrantNoticeTemplate(templateKey)) {
            throw new IllegalArgumentException(
                    "Template '" + templateKey + "' is not a registrant-notice template "
                    + "(category REGISTRANT_NOTICE) and cannot be sent to registrants.");
        }
    }

    // ── Async worker ───────────────────────────────────────────────────────────

    /**
     * Background send job — runs in a single {@code @Async} thread. Delegates to the synchronous
     * {@link #processSend(UUID, Event)} core (kept package-private and non-async so tests can drive
     * it deterministically without a thread-pool race).
     */
    @Async
    public void executeSlidesOnlineSendAsync(UUID sendId, Event event, String templateKey) {
        processSend(sendId, event, templateKey);
    }

    private String sentBy(UUID sendId) {
        return sendRepository.findById(sendId).map(NewsletterSend::getSentByUsername).orElse(null);
    }

    /**
     * Synchronous send core: resolve active registrants, skip opt-outs, send the locale-correct
     * template to each, record per-recipient audit, isolate per-recipient failures, throttle for SES.
     */
    void processSend(UUID sendId, Event event, String templateKey) {
        markInProgress(sendId);
        // Story 16.1a: the whole send is one recorded mail, linked to its newsletter_sends row.
        // Story 16.5: an own-text send renders the row's text, in the row's language, for everyone.
        Optional<NewsletterSend> ownRow = sendRepository.findById(sendId)
                .filter(row -> "OWN_TEXT".equals(row.getContentSource()));
        MailContext sendContext = ownRow.isPresent()
                ? MailContext.ownText(MailTrigger.forSend(sendId, sentBy(sendId)), event.getEventCode(),
                        MailContext.Audience.REGISTRANTS).forNewsletterSend(sendId, false)
                : mailContextFor(sendId, event.getEventCode(), templateKey, sentBy(sendId));

        // Render once per locale (body + subject are identical for all recipients of a locale).
        Map<String, RenderedMail> renderedByLocale = new HashMap<>();

        int sentCount = 0;
        int failedCount = 0;

        try {
            List<String> statuses = ownRow.map(NewsletterSend::getRegistrantStatuses)
                    .map(stored -> List.of(stored.split(",")))
                    .orElse(ACTIVE_REGISTRANT_STATUSES);
            for (RegistrantRecipient recipient : resolveRecipients(event, statuses).recipients()) {
                Registration registration = recipient.registration();
                String email = recipient.email();
                String locale = ownRow.map(NewsletterSend::getLocale).orElseGet(() -> recipientLocale(registration));
                RenderedMail mail = renderedByLocale.computeIfAbsent(locale, l -> ownRow
                        .map(row -> renderOwnBase(event, OwnText.content(row.getOwnSubject(), row.getOwnBodyHtml(), l)))
                        .orElseGet(() -> renderBase(event, templateKey, l)));
                RenderedMail personalised = personalise(mail, registration, false);

                String deliveryStatus = "sent";
                try {
                    emailService.sendHtmlEmailSync(sendContext.withSecrets(registration.getRegistrationCode()), email,
                            null, personalised.subject(), personalised.html(), configurationSetName, null);
                    sentCount++;
                } catch (Exception e) {
                    // AC6: isolate a transient SES failure for one recipient.
                    log.error("Slides-online send failed for {}", LoggingUtils.maskEmail(email), e);
                    deliveryStatus = "failed";
                    failedCount++;
                }
                recordRecipient(sendId, email, deliveryStatus);
                sleepQuietly(sendRateDelayMs);
            }

            String finalStatus = computeFinalStatus(sentCount, failedCount);
            markCompleted(sendId, sentCount, failedCount, finalStatus);
            log.info("Slides-online send completed: sendId={}, sent={}, failed={}, status={}",
                    sendId, sentCount, failedCount, finalStatus);

        } catch (Exception e) {
            log.error("Slides-online send job aborted unexpectedly: sendId={}", sendId, e);
            markCompleted(sendId, sentCount, failedCount, STATUS_FAILED);
        }
    }

    // ── Recipient resolution helpers ────────────────────────────────────────────

    /** One registrant who gets the mail. */
    public record RegistrantRecipient(Registration registration, String email, String displayName) {
    }

    /** One registrant who does not get it, and why ({@code NO_EMAIL}, {@code DUPLICATE}, {@code OPTED_OUT}). */
    public record SkippedRegistrant(String displayName, String reason) {
    }

    /** The audience of a registrant notice in send order (Story 16.8: the composer shows the same list). */
    public record RegistrantAudience(List<RegistrantRecipient> recipients, List<SkippedRegistrant> skipped) {
    }

    /**
     * Active registrants with a resolvable address, each address once, without global newsletter
     * opt-outs (AC3). The send and the composer's preview walk this same list.
     */
    public RegistrantAudience resolveRecipients(Event event) {
        return resolveRecipients(event, ACTIVE_REGISTRANT_STATUSES);
    }

    /** As {@link #resolveRecipients(Event)}, for registrations in {@code statuses} (stored values). */
    public RegistrantAudience resolveRecipients(Event event, List<String> statuses) {
        List<RegistrantRecipient> recipients = new java.util.ArrayList<>();
        List<SkippedRegistrant> skipped = new java.util.ArrayList<>();
        Set<String> seenEmails = new HashSet<>();
        for (Registration registration
                : registrationRepository.findByEventIdAndStatusIn(event.getId(), statuses)) {
            String displayName = displayName(registration);
            String email = resolveEmail(registration);
            if (email == null || email.isBlank()) {
                log.warn("Slides-online: skipping registration {} — no resolvable email",
                        registration.getRegistrationCode());
                skipped.add(new SkippedRegistrant(displayName, "NO_EMAIL"));
                continue;
            }
            // Dedupe: the same person could hold rows under different usernames/emails-case.
            if (!seenEmails.add(email.toLowerCase(Locale.ROOT))) {
                skipped.add(new SkippedRegistrant(displayName, "DUPLICATE"));
                continue;
            }
            // AC3: honour the global newsletter opt-out (registrations have no own flag).
            if (isOptedOut(email)) {
                log.debug("Slides-online: skipping {} — global newsletter opt-out", LoggingUtils.maskEmail(email));
                skipped.add(new SkippedRegistrant(displayName, "OPTED_OUT"));
                continue;
            }
            recipients.add(new RegistrantRecipient(registration, email, displayName));
        }
        return new RegistrantAudience(recipients, skipped);
    }

    private static String displayName(Registration registration) {
        String name = (nullToEmpty(registration.getAttendeeFirstName()) + " "
                + nullToEmpty(registration.getAttendeeLastName())).trim();
        return name.isEmpty() ? nullToEmpty(registration.getAttendeeUsername()) : name;
    }

    /** The registrant's language (AC4). */
    public String recipientLocale(Registration registration) {
        return resolveLocale(registration.getAttendeeUsername());
    }

    /** Whether the template is a registrant notice (category {@code REGISTRANT_NOTICE}) in DE or EN. */
    public boolean isRegistrantNoticeTemplate(String templateKey) {
        return templateKey != null && java.util.stream.Stream.of("de", "en")
                .map(l -> emailTemplateService.findByKeyAndLocale(templateKey, l))
                .flatMap(Optional::stream)
                .anyMatch(t -> "REGISTRANT_NOTICE".equalsIgnoreCase(t.getCategory()));
    }

    /** Story 16.1a: one recorded mail per registrant-notice send, linked to its audit row. */
    public static MailContext mailContextFor(UUID sendId, String eventCode, String templateKey, String sentBy) {
        return MailContext.of(MailTrigger.forSend(sendId, sentBy), eventCode, MailContext.Audience.REGISTRANTS,
                templateKey).forNewsletterSend(sendId, false);
    }

    private String resolveEmail(Registration registration) {
        if (registration.getAttendeeEmail() != null && !registration.getAttendeeEmail().isBlank()) {
            return registration.getAttendeeEmail();
        }
        try {
            return userApiClient.getUserByUsername(registration.getAttendeeUsername()).getEmail();
        } catch (Exception e) {
            log.warn("Slides-online: could not resolve email for username {}: {}",
                    registration.getAttendeeUsername(), e.getMessage());
            return null;
        }
    }

    /** True when the email carries a global newsletter opt-out (unsubscribed or suppressed). */
    private boolean isOptedOut(String email) {
        return subscriberRepository.findByEmailIgnoreCase(email)
                .map(s -> s.getUnsubscribedAt() != null || s.getSuppressedAt() != null)
                .orElse(false);
    }

    /**
     * AC4: {@code de*} → {@code de}, {@code en} → {@code en}, everything else / unknown → {@code de}
     * (German fallback — intentionally German, not English, for this feature).
     */
    String resolveLocale(String username) {
        String pref = userApiClient.getPreferredLanguage(username);
        if (pref == null) {
            return "de";
        }
        String lower = pref.toLowerCase(Locale.ROOT);
        if (lower.startsWith("de")) {
            return "de";
        }
        if (lower.equals("en")) {
            return "en";
        }
        return "de";
    }

    // ── Rendering ────────────────────────────────────────────────────────────────

    /**
     * The event-level part of a notice in one language, the same for every registrant of that
     * language (Story 16.8: send and composer preview both start here).
     */
    public RenderedMail renderBase(Event event, String templateKey, String locale) {
        Map<String, String> vars = buildVariables(event, locale);

        MailContent content = mailPipeline.storedTemplate(templateKey, locale);
        if (content.bodyHtml().isBlank()) {
            log.warn("Registrant-notice template '{}' locale '{}' not found in DB", templateKey, locale);
            content = new MailContent(templateKey, locale, content.subject(), "<p>The slides are online.</p>",
                    content.layoutKey(), content.contentSource());
        }
        ch.batbern.events.mailcenter.render.RenderedMail mail = mailPipeline.render(content, vars);
        String subject = mail.subject() != null ? mail.subject()
                : ("de".equals(locale) ? "Die Folien sind online — " : "The slides are online — ")
                        + event.getTitle();

        return new RenderedMail(subject, mail.html());
    }

    /**
     * The event-level values, the same for every registrant of a language. Per-registrant values
     * (cancel link, name) are NOT here: this render is shared, see {@link #personalise}.
     */
    private Map<String, String> buildVariables(Event event, String locale) {
        return mailPipeline.variables().event(event, locale)
                .put(MailVariable.DASHBOARD_LINK, baseUrl)
                .asMap();
    }

    /**
     * Per-registrant personalisation (Way-1 "deregistration call"): the one-click self-service
     * cancel link and the recipient name, substituted on the per-locale render. They are not in
     * {@link #buildVariables}: that map feeds the per-locale cache, and a token there would leak one
     * registrant's link to everyone. Unmatched placeholders stay untouched, so templates without
     * them are unaffected. With {@code test} the cancel link is a dummy (Story 16.8 AC5).
     */
    public RenderedMail personalise(RenderedMail base, Registration registration, boolean test) {
        Map<String, String> perRecipientVars = perRecipientVariables(registration, test);
        return new RenderedMail(emailService.replaceVariables(base.subject(), perRecipientVars),
                emailService.replaceVariables(base.html(), perRecipientVars));
    }

    /**
     * Every variable one registrant's notice is rendered with: the shared per-locale values plus
     * the registrant's own (Story 16.11 AC9). With {@code test} the cancel link is a dummy.
     */
    public Map<String, String> variables(Event event, String locale, Registration registration, boolean test) {
        Map<String, String> vars = new HashMap<>(buildVariables(event, locale));
        vars.putAll(perRecipientVariables(registration, test));
        return vars;
    }

    private Map<String, String> perRecipientVariables(Registration registration, boolean test) {
        Map<String, String> vars = buildPerRecipientVariables(registration);
        if (test) {
            vars.put(MailVariable.DEREGISTRATION_URL.placeholder(), baseUrl + "/deregister?token=TEST");
        }
        return vars;
    }

    /**
     * Per-recipient placeholders layered on top of the per-locale render (see processSend).
     * <ul>
     *   <li>{@code deregistrationUrl} — the registrant's one-click self-service cancel link
     *       ({@code {baseUrl}/deregister?token={token}}, Story 10.12). Lets a "way-1" deregistration
     *       call ask each registrant to free their seat with a single click. Falls back to the public
     *       events page if a legacy row somehow has no token.</li>
     *   <li>{@code recipientName} / {@code attendeeFirstName} — personal greeting tokens.</li>
     * </ul>
     */
    private Map<String, String> buildPerRecipientVariables(Registration registration) {
        Map<String, String> vars = new HashMap<>();
        UUID token = registration.getDeregistrationToken();
        vars.put("deregistrationUrl", token != null
                ? baseUrl + "/deregister?token=" + token
                : baseUrl + "/events");
        String fullName = (nullToEmpty(registration.getAttendeeFirstName()) + " "
                + nullToEmpty(registration.getAttendeeLastName())).trim();
        vars.put("recipientName", fullName);
        vars.put("attendeeFirstName", nullToEmpty(registration.getAttendeeFirstName()));
        return vars;
    }

    // ── @Transactional audit helpers (short, autonomous transactions) ─────────────

    @Transactional
    protected NewsletterSend createSendAuditRecord(Event event, String templateKey,
                                                   String sentByUsername, int recipientCount) {
        NewsletterSend send = NewsletterSend.builder()
                .eventId(event.getId())
                .templateKey(templateKey)
                .reminder(false)
                .locale("de") // nominal — actual locale is resolved per recipient (AC4)
                .sentAt(Instant.now())
                .sentByUsername(sentByUsername)
                .recipientCount(recipientCount)
                .status(STATUS_PENDING)
                .testMode(false)
                .build();
        return sendRepository.save(send);
    }

    @Transactional
    protected void markInProgress(UUID sendId) {
        sendRepository.findById(sendId).ifPresent(send -> {
            send.setStatus(STATUS_IN_PROGRESS);
            send.setStartedAt(Instant.now());
            sendRepository.save(send);
        });
    }

    @Transactional
    protected void markCompleted(UUID sendId, int sentCount, int failedCount, String finalStatus) {
        sendRepository.findById(sendId).ifPresent(send -> {
            send.setSentCount(sentCount);
            send.setFailedCount(failedCount);
            send.setStatus(finalStatus);
            send.setCompletedAt(Instant.now());
            sendRepository.save(send);
        });
    }

    @Transactional
    protected void recordRecipient(UUID sendId, String email, String deliveryStatus) {
        NewsletterRecipient recipient = NewsletterRecipient.builder()
                .id(new NewsletterRecipientId(sendId, email))
                .deliveryStatus(deliveryStatus)
                .build();
        recipientRepository.save(recipient);
    }

    // ── Misc helpers ───────────────────────────────────────────────────────────

    private String computeFinalStatus(int sentCount, int failedCount) {
        if (failedCount == 0) {
            return STATUS_COMPLETED;
        }
        if (sentCount == 0) {
            return STATUS_FAILED;
        }
        return STATUS_PARTIAL;
    }

    private void sleepQuietly(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    /** Rendered subject + HTML for one locale, computed once and reused across recipients. */
    public record RenderedMail(String subject, String html) {
    }
}
