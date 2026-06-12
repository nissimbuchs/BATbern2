package ch.batbern.events.service;

import ch.batbern.events.client.UserApiClient;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.NewsletterRecipient;
import ch.batbern.events.domain.NewsletterRecipientId;
import ch.batbern.events.domain.NewsletterSend;
import ch.batbern.events.domain.Registration;
import ch.batbern.events.dto.SlidesOnlineSendResponse;
import ch.batbern.events.exception.DuplicateNewsletterSendException;
import ch.batbern.events.exception.SlidesOnlineAlreadySentException;
import ch.batbern.events.repository.NewsletterRecipientRepository;
import ch.batbern.events.repository.NewsletterSendRepository;
import ch.batbern.events.repository.NewsletterSubscriberRepository;
import ch.batbern.events.repository.RegistrationRepository;
import ch.batbern.shared.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.Year;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
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

    static final String TEMPLATE_KEY = "slides-online";
    private static final String LAYOUT_KEY = "batbern-default";

    /** Active registrant statuses that receive the mail (AC2). */
    static final List<String> ACTIVE_REGISTRANT_STATUSES = List.of("registered", "confirmed");

    static final String STATUS_PENDING = "PENDING";
    static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    static final String STATUS_COMPLETED = "COMPLETED";
    static final String STATUS_PARTIAL = "PARTIAL";
    static final String STATUS_FAILED = "FAILED";

    private final EmailService emailService;
    private final EmailTemplateService emailTemplateService;
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
     * Initiate the slides-online send for an event's active registrants.
     *
     * <p>Creates the audit row (status {@code PENDING}), launches the {@code @Async} worker, and
     * returns immediately. Guards against double-send (in-progress + already-sent).
     *
     * @throws DuplicateNewsletterSendException (409) if a slides-online send is already IN_PROGRESS
     * @throws SlidesOnlineAlreadySentException (409) if a slides-online send already completed
     */
    public SlidesOnlineSendResponse sendSlidesOnline(Event event, String sentByUsername) {
        UUID eventId = event.getId();

        // AC5: reuse the in-progress guard, scoped to the slides-online template.
        sendRepository.findFirstByEventIdAndTemplateKeyAndStatus(eventId, TEMPLATE_KEY, STATUS_IN_PROGRESS)
                .ifPresent(active -> {
                    throw new DuplicateNewsletterSendException(
                            "A slides-online send is already in progress for event "
                            + event.getEventCode() + " (sendId=" + active.getId() + ")");
                });

        // AC5: a completed slides-online send must not re-fire (one-shot, event-triggered).
        if (sendRepository.existsByEventIdAndTemplateKeyAndStatusIn(
                eventId, TEMPLATE_KEY, List.of(STATUS_COMPLETED, STATUS_PARTIAL))) {
            throw new SlidesOnlineAlreadySentException(
                    "The slides-online mail has already been sent for event " + event.getEventCode());
        }

        List<Registration> registrants =
                registrationRepository.findByEventIdAndStatusIn(eventId, ACTIVE_REGISTRANT_STATUSES);

        NewsletterSend saved = createSendAuditRecord(event, sentByUsername, registrants.size());

        // Launch background job via the proxy so @Async is honoured.
        self.executeSlidesOnlineSendAsync(saved.getId(), event);

        log.info("Slides-online send queued: sendId={}, event={}, registrants={}",
                saved.getId(), event.getEventCode(), registrants.size());

        return SlidesOnlineSendResponse.builder()
                .sendId(saved.getId())
                .status(STATUS_PENDING)
                .recipientCount(registrants.size())
                .build();
    }

    // ── Async worker ───────────────────────────────────────────────────────────

    /**
     * Background send job — runs in a single {@code @Async} thread. Delegates to the synchronous
     * {@link #processSend(UUID, Event)} core (kept package-private and non-async so tests can drive
     * it deterministically without a thread-pool race).
     */
    @Async
    public void executeSlidesOnlineSendAsync(UUID sendId, Event event) {
        processSend(sendId, event);
    }

    /**
     * Synchronous send core: resolve active registrants, skip opt-outs, send the locale-correct
     * template to each, record per-recipient audit, isolate per-recipient failures, throttle for SES.
     */
    void processSend(UUID sendId, Event event) {
        markInProgress(sendId);

        // Render once per locale (body + subject are identical for all recipients of a locale).
        Map<String, RenderedMail> renderedByLocale = new HashMap<>();
        Set<String> seenEmails = new HashSet<>();

        int sentCount = 0;
        int failedCount = 0;

        try {
            List<Registration> registrants =
                    registrationRepository.findByEventIdAndStatusIn(event.getId(), ACTIVE_REGISTRANT_STATUSES);

            for (Registration registration : registrants) {
                String email = resolveEmail(registration);
                if (email == null || email.isBlank()) {
                    log.warn("Slides-online: skipping registration {} — no resolvable email",
                            registration.getRegistrationCode());
                    continue;
                }

                // Dedupe: the same person could hold rows under different usernames/emails-case.
                if (!seenEmails.add(email.toLowerCase(Locale.ROOT))) {
                    continue;
                }

                // AC3: honour the global newsletter opt-out (registrations have no own flag).
                if (isOptedOut(email)) {
                    log.debug("Slides-online: skipping {} — global newsletter opt-out", email);
                    continue;
                }

                String locale = resolveLocale(registration.getAttendeeUsername());
                RenderedMail mail = renderedByLocale.computeIfAbsent(locale, l -> renderMail(event, l));

                String deliveryStatus = "sent";
                try {
                    emailService.sendHtmlEmailSync(email, mail.subject(), mail.html(), configurationSetName);
                    sentCount++;
                } catch (Exception e) {
                    // AC6: isolate a transient SES failure for one recipient.
                    log.error("Slides-online send failed for {}", email, e);
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

    private String resolveEmail(Registration registration) {
        if (registration.getAttendeeEmail() != null && !registration.getAttendeeEmail().isBlank()) {
            return registration.getAttendeeEmail();
        }
        try {
            return userApiClient.getEmailByUsername(registration.getAttendeeUsername());
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

    private RenderedMail renderMail(Event event, String locale) {
        Map<String, String> vars = buildVariables(event, locale);

        Optional<ch.batbern.events.domain.EmailTemplate> templateOpt =
                emailTemplateService.findByKeyAndLocale(TEMPLATE_KEY, locale);
        String contentHtml = templateOpt
                .map(t -> emailService.replaceVariables(t.getHtmlBody(), vars))
                .orElseGet(() -> {
                    log.warn("Slides-online template '{}' locale '{}' not found in DB", TEMPLATE_KEY, locale);
                    return "<p>The slides are online.</p>";
                });
        String mergedHtml = emailService.replaceVariables(
                emailTemplateService.mergeWithLayout(contentHtml, LAYOUT_KEY, locale), vars);

        String subject = emailTemplateService.resolveSubject(TEMPLATE_KEY, locale)
                .map(s -> emailService.replaceVariables(s, vars))
                .orElseGet(() -> ("de".equals(locale) ? "Die Folien sind online — " : "The slides are online — ")
                        + event.getTitle());

        return new RenderedMail(subject, mergedHtml);
    }

    private Map<String, String> buildVariables(Event event, String locale) {
        boolean isDe = "de".equals(locale);
        Map<String, String> vars = new HashMap<>();
        vars.put("eventTitle", nullToEmpty(event.getTitle()));
        vars.put("eventNumber", String.valueOf(event.getEventNumber()));
        vars.put("eventType", localizeEventType(event.getEventType(), isDe));
        vars.put("eventDate", formatEventDate(event, isDe ? Locale.GERMAN : Locale.ENGLISH));
        vars.put("eventDetailLink", baseUrl + "/events/" + event.getEventCode());
        vars.put("currentYear", String.valueOf(Year.now().getValue()));
        return vars;
    }

    private String localizeEventType(ch.batbern.events.dto.generated.EventType eventType, boolean isDe) {
        if (eventType == null) {
            return isDe ? "Abend-BAT" : "Evening BAT";
        }
        return switch (eventType) {
            case EVENING -> isDe ? "Abend-BAT" : "Evening BAT";
            case FULL_DAY -> isDe ? "Ganztages-BAT" : "Full-Day BAT";
            case AFTERNOON -> isDe ? "Nachmittags-BAT" : "Afternoon BAT";
            default -> eventType.name().replace("_", " ");
        };
    }

    private String formatEventDate(Event event, Locale locale) {
        if (event.getDate() == null) {
            return "";
        }
        return DateTimeFormatter.ofPattern("EEEE, d. MMMM yyyy", locale)
                .withZone(ZoneId.of("Europe/Zurich"))
                .format(event.getDate());
    }

    // ── @Transactional audit helpers (short, autonomous transactions) ─────────────

    @Transactional
    protected NewsletterSend createSendAuditRecord(Event event, String sentByUsername, int recipientCount) {
        NewsletterSend send = NewsletterSend.builder()
                .eventId(event.getId())
                .templateKey(TEMPLATE_KEY)
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
    private record RenderedMail(String subject, String html) {}
}
