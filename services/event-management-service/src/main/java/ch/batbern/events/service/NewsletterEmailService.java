package ch.batbern.events.service;

import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.NewsletterRecipient;
import ch.batbern.events.domain.NewsletterRecipientId;
import ch.batbern.events.domain.NewsletterSend;
import ch.batbern.events.domain.NewsletterSubscriber;
import ch.batbern.events.domain.Session;
import ch.batbern.events.dto.NewsletterPreviewResponse;
import ch.batbern.events.dto.NewsletterSendResponse;
import ch.batbern.events.dto.NewsletterSendStatusResponse;
import ch.batbern.events.dto.SessionSpeakerResponse;
import ch.batbern.events.exception.DuplicateNewsletterSendException;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.NewsletterRecipientRepository;
import ch.batbern.events.repository.NewsletterSendRepository;
import ch.batbern.events.repository.NewsletterSubscriberRepository;
import ch.batbern.events.repository.SessionRepository;
import ch.batbern.shared.service.EmailService;
import ch.batbern.shared.types.EventWorkflowState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.lang.Nullable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for building and sending newsletter emails (Story 10.7 — AC8, AC10).
 *
 * <h2>Send Architecture (robustness for 3000+ subscribers)</h2>
 * <ol>
 *   <li>{@link #sendNewsletter} creates a {@code newsletter_sends} row (status=PENDING),
 *       launches {@link #executeNewsletterSendAsync} in a background thread,
 *       and returns immediately with the send ID and PENDING status.</li>
 *   <li>{@link #executeNewsletterSendAsync} runs in a single {@code @Async} thread,
 *       processes subscribers in pages of 50, calls
 *       {@link EmailService#sendHtmlEmailSync} (no inner thread-pool dispatch),
 *       and respects the SES default rate limit (~14/s) via a 70 ms sleep between emails.</li>
 *   <li>Progress is written to {@code newsletter_sends} after each page so the organizer
 *       can poll {@code GET /sends/{sendId}/status} and see a live progress bar.</li>
 *   <li>Terminal status: COMPLETED (no failures) | PARTIAL (some failed) | FAILED (all failed).</li>
 * </ol>
 *
 * <h2>Duplicate-send prevention</h2>
 * {@link #sendNewsletter} throws {@link DuplicateNewsletterSendException} (409) when a
 * send is already IN_PROGRESS for the same event.
 *
 * <h2>Retry</h2>
 * {@link #retryFailedRecipients} re-sends only to subscribers with
 * {@code delivery_status='failed'} in {@code newsletter_recipients}, updating the
 * existing send row in place.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NewsletterEmailService {

    // ── Constants ─────────────────────────────────────────────────────────────

    private static final String DEFAULT_TEMPLATE_KEY = "newsletter-event";
    private static final String LAYOUT_KEY = "batbern-default";
    private static final int SEND_PAGE_SIZE = 50;
    /**
     * Delay between individual email sends, in milliseconds.
     * <p>
     * Production default: 70 ms (~14 emails/s — respects AWS SES default sending rate).<br>
     * Local dev override: 50 ms (set via {@code newsletter.send.rate-delay-ms} in the
     * {@code local} profile) — intentionally slow enough to make the progress bar
     * visible and to allow service-kill/resume tests without hitting a real SES rate limit.
     */
    @Value("${newsletter.send.rate-delay-ms:70}")
    private long sendRateDelayMs;

    private static final Set<String> STRUCTURAL_SESSION_TYPES = Set.of("moderation", "break", "lunch");
    private static final Set<EventWorkflowState> SPEAKERS_VISIBLE_STATES = EnumSet.of(
            EventWorkflowState.AGENDA_PUBLISHED,
            EventWorkflowState.EVENT_LIVE,
            EventWorkflowState.EVENT_COMPLETED,
            EventWorkflowState.ARCHIVED
    );

    static final String STATUS_PENDING = "PENDING";
    static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    static final String STATUS_COMPLETED = "COMPLETED";
    static final String STATUS_PARTIAL = "PARTIAL";
    static final String STATUS_FAILED = "FAILED";

    // ── Dependencies ──────────────────────────────────────────────────────────

    private final EmailService emailService;
    private final EmailTemplateService emailTemplateService;
    private final NewsletterSubscriberService subscriberService;
    private final NewsletterSubscriberRepository subscriberRepository;
    private final NewsletterSendRepository sendRepository;
    private final NewsletterRecipientRepository recipientRepository;
    private final SessionRepository sessionRepository;
    private final SessionUserService sessionUserService;
    private final EventRepository eventRepository;

    @Value("${app.base-url:https://batbern.ch}")
    private String baseUrl;

    /**
     * Self-reference via {@code @Lazy} so that calls to {@code @Async} methods go through
     * the Spring proxy (direct {@code this.xxx()} calls bypass the proxy and run synchronously).
     */
    private NewsletterEmailService self;

    @Autowired
    public void setSelf(@Lazy NewsletterEmailService self) {
        this.self = self;
    }

    // ── Startup recovery ──────────────────────────────────────────────────────

    /**
     * On startup, marks any orphaned IN_PROGRESS or PENDING sends as PARTIAL/FAILED.
     *
     * <p>If the service is killed mid-send (e.g. Fargate Spot interruption), the
     * {@code newsletter_sends} row is left in {@code IN_PROGRESS}. Without recovery the
     * row would stay that way forever and the Retry button would never appear.
     *
     * <p>After this runs, orphaned sends show up with status PARTIAL (if some emails were
     * already sent) or FAILED (if none were sent), and the organizer can click Retry.
     */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void recoverOrphanedSends() {
        List<NewsletterSend> orphans = sendRepository.findByStatusIn(
                List.of(STATUS_IN_PROGRESS, STATUS_PENDING));
        if (orphans.isEmpty()) {
            return;
        }
        log.warn("Recovering {} orphaned newsletter send(s) left in IN_PROGRESS/PENDING "
                + "by a previous service instance", orphans.size());
        Instant now = Instant.now();
        for (NewsletterSend send : orphans) {
            String recoveredStatus = send.getSentCount() > 0 ? STATUS_PARTIAL : STATUS_FAILED;
            send.setStatus(recoveredStatus);
            send.setCompletedAt(now);
            sendRepository.save(send);
            log.warn("Orphaned send {} → {} (sentCount={}, failedCount={})",
                    send.getId(), recoveredStatus, send.getSentCount(), send.getFailedCount());
        }
    }

    // ── Preview (no send) ─────────────────────────────────────────────────────

    /**
     * Builds a preview of the newsletter email without sending.
     */
    @Transactional(readOnly = true)
    public NewsletterPreviewResponse preview(Event event, boolean isReminder, String locale,
                                              @Nullable String templateKey) {
        String effectiveKey = resolveTemplateKey(templateKey);
        Map<String, String> vars = buildVariables(event, locale, isReminder,
                baseUrl + "/unsubscribe?token=PREVIEW");
        String contentHtml = renderContent(locale, vars, effectiveKey);
        String mergedHtml = emailService.replaceVariables(
                emailTemplateService.mergeWithLayout(contentHtml, LAYOUT_KEY, locale), vars);
        String subject = buildSubject(event, isReminder, locale, vars, effectiveKey);
        int count = (int) subscriberService.getActiveCount();
        return NewsletterPreviewResponse.builder()
                .subject(subject)
                .htmlPreview(mergedHtml)
                .recipientCount(count)
                .build();
    }

    // ── Send (fire-and-forget) ────────────────────────────────────────────────

    /**
     * Initiates a newsletter send for all active subscribers.
     *
     * <p>Creates an audit record with {@code status=PENDING}, launches an {@code @Async}
     * background job, and returns immediately. The organizer can poll the status endpoint
     * to track progress.
     *
     * @throws DuplicateNewsletterSendException (HTTP 409) if a send is already IN_PROGRESS
     */
    public NewsletterSendResponse sendNewsletter(Event event, boolean isReminder,
                                                  String locale, String sentByUsername,
                                                  @Nullable String templateKey) {
        String effectiveKey = resolveTemplateKey(templateKey);

        // Duplicate-send prevention: reject if a send is already in progress for this event.
        sendRepository.findFirstByEventIdAndStatus(event.getId(), STATUS_IN_PROGRESS)
                .ifPresent(active -> {
                    throw new DuplicateNewsletterSendException(
                            "A newsletter send is already in progress for event "
                            + event.getEventCode() + " (sendId=" + active.getId() + ")");
                });

        long totalCount = subscriberService.getActiveCount();

        // Persist PENDING audit record first (committed immediately in own transaction).
        NewsletterSend saved = createSendAuditRecord(event, isReminder, locale, sentByUsername,
                (int) totalCount, effectiveKey);

        // Launch background send job — returns immediately.
        // Must call via `self` proxy so @Async is honoured (direct this.xxx() bypasses the proxy).
        self.executeNewsletterSendAsync(saved.getId(), event, isReminder, locale, effectiveKey);

        log.info("Newsletter send job queued: sendId={}, event={}, recipients={}",
                saved.getId(), event.getEventCode(), totalCount);

        return toResponse(saved);
    }

    /**
     * Background send job — runs in a single {@code @Async} thread.
     *
     * <p>Processes subscribers in pages of {@value #SEND_PAGE_SIZE}, sends each email
     * synchronously (no inner thread-pool dispatch), and updates progress counters in DB
     * after each page. Sleeps {@code sendRateDelayMs} ms between emails to respect
     * the SES default sending rate.
     */
    @Async
    public void executeNewsletterSendAsync(UUID sendId, Event event, boolean isReminder,
                                            String locale, String effectiveKey) {
        markInProgress(sendId);

        Map<String, String> baseVars = buildVariables(event, locale, isReminder, "");
        String subject = buildSubject(event, isReminder, locale, baseVars, effectiveKey);

        int sentCount = 0;
        int failedCount = 0;

        try {
            int pageNumber = 0;
            Page<NewsletterSubscriber> page;

            do {
                page = subscriberRepository.findByUnsubscribedAtIsNull(
                        PageRequest.of(pageNumber, SEND_PAGE_SIZE));

                for (NewsletterSubscriber subscriber : page.getContent()) {
                    String deliveryStatus = "sent";
                    try {
                        String unsubLink = baseUrl + "/unsubscribe?token="
                                + subscriber.getUnsubscribeToken();
                        Map<String, String> recipientVars = new HashMap<>(baseVars);
                        recipientVars.put("unsubscribeLink", unsubLink);
                        String contentHtml = renderContent(locale, recipientVars, effectiveKey);
                        String mergedHtml = emailService.replaceVariables(
                                emailTemplateService.mergeWithLayout(contentHtml, LAYOUT_KEY, locale),
                                recipientVars);
                        emailService.sendHtmlEmailSync(subscriber.getEmail(), subject, mergedHtml);
                        sentCount++;
                    } catch (Exception e) {
                        log.error("Newsletter send failed for {}: {}", subscriber.getEmail(), e.getMessage());
                        deliveryStatus = "failed";
                        failedCount++;
                    }
                    recordRecipient(sendId, subscriber.getEmail(), deliveryStatus);
                    sleepQuietly(sendRateDelayMs);
                }

                // Persist mid-send progress so the status endpoint reflects live counts.
                updateSendProgress(sendId, sentCount, failedCount);
                pageNumber++;

            } while (page.hasNext());

            String finalStatus = computeFinalStatus(sentCount, failedCount);
            markCompleted(sendId, sentCount, failedCount, finalStatus);

            log.info("Newsletter send completed: sendId={}, sent={}, failed={}, status={}",
                    sendId, sentCount, failedCount, finalStatus);

        } catch (Exception e) {
            log.error("Newsletter send job aborted unexpectedly: sendId={}", sendId, e);
            markCompleted(sendId, sentCount, failedCount, STATUS_FAILED);
        }
    }

    // ── Retry failed recipients ───────────────────────────────────────────────

    /**
     * Re-sends only to recipients that previously failed for the given send.
     *
     * <p>Updates the existing {@code newsletter_sends} row in place so that the send
     * history table shows one clean final row rather than a confusing duplicate.
     *
     * @throws IllegalStateException if the send is COMPLETED or already IN_PROGRESS/PENDING
     */
    public NewsletterSendResponse retryFailedRecipients(NewsletterSend send, Event event,
                                                         String sentByUsername) {
        if (STATUS_COMPLETED.equals(send.getStatus())) {
            throw new IllegalStateException(
                    "Send " + send.getId() + " is already COMPLETED — nothing to retry");
        }
        if (STATUS_IN_PROGRESS.equals(send.getStatus()) || STATUS_PENDING.equals(send.getStatus())) {
            throw new IllegalStateException(
                    "Send " + send.getId() + " is already " + send.getStatus());
        }

        String effectiveKey = resolveTemplateKey(send.getTemplateKey());
        self.executeRetryAsync(send.getId(), event, send.isReminder(), send.getLocale(), effectiveKey);

        log.info("Newsletter retry job queued: sendId={}, event={}",
                send.getId(), event.getEventCode());

        NewsletterSend reloaded = sendRepository.findById(send.getId()).orElse(send);
        return toResponse(reloaded);
    }

    @Async
    public void executeRetryAsync(UUID sendId, Event event, boolean isReminder,
                                   String locale, String effectiveKey) {
        markInProgress(sendId);

        Map<String, String> baseVars = buildVariables(event, locale, isReminder, "");
        String subject = buildSubject(event, isReminder, locale, baseVars, effectiveKey);

        // Recipients that previously failed (explicit delivery failure).
        List<NewsletterRecipient> failedRecipients =
                recipientRepository.findByIdSendIdAndDeliveryStatus(sendId, "failed");

        // Subscribers with no recipient record at all — the service was killed before
        // it reached them. After orphan recovery these are the majority of "unsent" recipients.
        List<NewsletterSubscriber> uncontactedSubscribers =
                subscriberRepository.findActiveSubscribersNotInSend(sendId);

        log.info("Newsletter retry: sendId={}, failed={}, uncontacted={}",
                sendId, failedRecipients.size(), uncontactedSubscribers.size());

        int newlySent = 0;
        int newlyFailed = 0;
        int processedSinceLastFlush = 0;
        int lastFlushedSent = 0;
        try {
            // ── 1. Re-send to previously-failed recipients ─────────────────────
            for (NewsletterRecipient failed : failedRecipients) {
                String email = failed.getId().getEmail();
                String deliveryStatus = "sent";
                try {
                    String unsubToken = subscriberRepository.findByEmail(email)
                            .map(NewsletterSubscriber::getUnsubscribeToken)
                            .orElse("");
                    String unsubLink = baseUrl + "/unsubscribe?token=" + unsubToken;
                    Map<String, String> recipientVars = new HashMap<>(baseVars);
                    recipientVars.put("unsubscribeLink", unsubLink);
                    String contentHtml = renderContent(locale, recipientVars, effectiveKey);
                    String mergedHtml = emailService.replaceVariables(
                            emailTemplateService.mergeWithLayout(contentHtml, LAYOUT_KEY, locale),
                            recipientVars);
                    emailService.sendHtmlEmailSync(email, subject, mergedHtml);
                    newlySent++;
                } catch (Exception e) {
                    log.error("Newsletter retry (failed) failed for {}: {}", email, e.getMessage());
                    deliveryStatus = "failed";
                    newlyFailed++;
                }
                updateRecipientStatus(sendId, email, deliveryStatus);
                sleepQuietly(sendRateDelayMs);
                if (++processedSinceLastFlush >= SEND_PAGE_SIZE) {
                    updateRetrySentCount(sendId, newlySent - lastFlushedSent);
                    lastFlushedSent = newlySent;
                    processedSinceLastFlush = 0;
                }
            }

            // ── 2. Send to subscribers that were never contacted (orphan resume) ─
            for (NewsletterSubscriber subscriber : uncontactedSubscribers) {
                String email = subscriber.getEmail();
                String deliveryStatus = "sent";
                try {
                    String unsubLink = baseUrl + "/unsubscribe?token="
                            + subscriber.getUnsubscribeToken();
                    Map<String, String> recipientVars = new HashMap<>(baseVars);
                    recipientVars.put("unsubscribeLink", unsubLink);
                    String contentHtml = renderContent(locale, recipientVars, effectiveKey);
                    String mergedHtml = emailService.replaceVariables(
                            emailTemplateService.mergeWithLayout(contentHtml, LAYOUT_KEY, locale),
                            recipientVars);
                    emailService.sendHtmlEmailSync(email, subject, mergedHtml);
                    newlySent++;
                } catch (Exception e) {
                    log.error("Newsletter retry (uncontacted) failed for {}: {}", email, e.getMessage());
                    deliveryStatus = "failed";
                    newlyFailed++;
                }
                recordRecipient(sendId, email, deliveryStatus);
                sleepQuietly(sendRateDelayMs);
                if (++processedSinceLastFlush >= SEND_PAGE_SIZE) {
                    updateRetrySentCount(sendId, newlySent - lastFlushedSent);
                    lastFlushedSent = newlySent;
                    processedSinceLastFlush = 0;
                }
            }

            // Final flush of any remaining progress since last batch.
            updateRetrySentCount(sendId, newlySent - lastFlushedSent);

            // Determine final status based on remaining failures.
            long remainingFailed =
                    recipientRepository.findByIdSendIdAndDeliveryStatus(sendId, "failed").size();
            String finalStatus = remainingFailed == 0 ? STATUS_COMPLETED : STATUS_PARTIAL;
            markCompleted(sendId, -1, (int) remainingFailed, finalStatus);

            log.info("Newsletter retry completed: sendId={}, newlySent={}, remainingFailed={}, status={}",
                    sendId, newlySent, remainingFailed, finalStatus);

        } catch (Exception e) {
            log.error("Newsletter retry aborted: sendId={}", sendId, e);
            markCompleted(sendId, -1, -1, STATUS_PARTIAL);
        }
    }

    // ── Status mapping ────────────────────────────────────────────────────────

    /** Maps a send entity to the polling status DTO. */
    public NewsletterSendStatusResponse toStatusResponse(NewsletterSend send) {
        int total = send.getRecipientCount() != null ? send.getRecipientCount() : 0;
        int done = send.getSentCount() + send.getFailedCount();
        int pct = total > 0 ? Math.min(100, done * 100 / total) : 0;
        return NewsletterSendStatusResponse.builder()
                .id(send.getId())
                .status(send.getStatus())
                .sentCount(send.getSentCount())
                .failedCount(send.getFailedCount())
                .totalCount(total)
                .percentComplete(pct)
                .startedAt(send.getStartedAt())
                .completedAt(send.getCompletedAt())
                .build();
    }

    /** Maps a NewsletterSend entity to its response DTO. */
    public NewsletterSendResponse toResponse(NewsletterSend send) {
        return NewsletterSendResponse.builder()
                .id(send.getId())
                .sentAt(send.getSentAt())
                .reminder(send.isReminder())
                .locale(send.getLocale())
                .recipientCount(send.getRecipientCount() != null ? send.getRecipientCount() : 0)
                .sentByUsername(send.getSentByUsername())
                .status(send.getStatus())
                .sentCount(send.getSentCount())
                .failedCount(send.getFailedCount())
                .startedAt(send.getStartedAt())
                .completedAt(send.getCompletedAt())
                .build();
    }

    // ── Variable building ─────────────────────────────────────────────────────

    Map<String, String> buildVariables(Event event, String locale, boolean isReminder,
                                        String unsubscribeLink) {
        boolean isDe = "de".equals(locale);
        Locale javaLocale = isDe ? Locale.GERMAN : Locale.ENGLISH;

        Map<String, String> vars = new HashMap<>();
        vars.put("reminderPrefix", buildReminderPrefix(isReminder, isDe));
        vars.put("eventNumber", String.valueOf(event.getEventNumber()));
        vars.put("eventType", localizeEventType(event.getEventType(), isDe));
        vars.put("eventTitle", event.getTitle());
        vars.put("eventDate", formatEventDate(event, javaLocale));
        vars.put("eventTime", formatEventTime(event, isDe));
        vars.put("venue", event.getVenueName());
        vars.put("venueDirectionsUrl", "");
        vars.put("conferenceLanguage", isDe ? "Deutsch / Englisch" : "German / English");
        vars.put("speakersSection", buildSpeakersSection(event, isDe));
        vars.put("currentYear", String.valueOf(java.time.Year.now().getValue()));
        vars.put("eventDetailLink", baseUrl + "/events/" + event.getEventCode());
        vars.put("registrationLink", baseUrl + "/register/" + event.getEventCode());
        vars.put("upcomingEventsSection", buildUpcomingEventsSection(event.getId(), isDe));
        vars.put("unsubscribeLink", unsubscribeLink);
        vars.put("preferencesLink", baseUrl + "/account");
        return vars;
    }

    // ── @Transactional helpers (each in its own short transaction) ────────────

    @Transactional
    protected NewsletterSend createSendAuditRecord(Event event, boolean isReminder, String locale,
                                                   String sentByUsername, int recipientCount,
                                                   String templateKey) {
        NewsletterSend send = NewsletterSend.builder()
                .eventId(event.getId())
                .templateKey(templateKey)
                .reminder(isReminder)
                .locale(locale)
                .sentAt(Instant.now())
                .sentByUsername(sentByUsername)
                .recipientCount(recipientCount)
                .status(STATUS_PENDING)
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
    protected void updateSendProgress(UUID sendId, int sentCount, int failedCount) {
        sendRepository.findById(sendId).ifPresent(send -> {
            send.setSentCount(sentCount);
            send.setFailedCount(failedCount);
            sendRepository.save(send);
        });
    }

    @Transactional
    protected void updateRetrySentCount(UUID sendId, int addedSentCount) {
        sendRepository.findById(sendId).ifPresent(send -> {
            send.setSentCount(send.getSentCount() + addedSentCount);
            sendRepository.save(send);
        });
    }

    @Transactional
    protected void markCompleted(UUID sendId, int sentCount, int failedCount, String finalStatus) {
        sendRepository.findById(sendId).ifPresent(send -> {
            if (sentCount >= 0) {
                send.setSentCount(sentCount);
            }
            if (failedCount >= 0) {
                send.setFailedCount(failedCount);
            }
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

    @Transactional
    protected void updateRecipientStatus(UUID sendId, String email, String deliveryStatus) {
        recipientRepository.findById(new NewsletterRecipientId(sendId, email)).ifPresent(r -> {
            r.setDeliveryStatus(deliveryStatus);
            recipientRepository.save(r);
        });
    }

    // ── Speaker / upcoming events section builders ────────────────────────────

    String buildSpeakersSection(Event event, boolean isDe) {
        if (event.getWorkflowState() == null
                || !SPEAKERS_VISIBLE_STATES.contains(event.getWorkflowState())) {
            return "";
        }
        List<Session> sessions = sessionRepository.findByEventIdWithSpeakers(event.getId());
        if (sessions.isEmpty()) {
            return "";
        }

        String thStyle = "padding:6px 10px;text-align:left;font-size:11px;font-weight:600;"
                + "text-transform:uppercase;letter-spacing:1px;color:#71717A;"
                + "border-bottom:1px solid #E4E4E7;";
        String tdStyle = "padding:8px 10px;font-size:14px;color:#3F3F46;"
                + "border-bottom:1px solid #F4F4F5;vertical-align:top;";
        String tdMutedStyle = "padding:8px 10px;font-size:14px;color:#71717A;"
                + "border-bottom:1px solid #F4F4F5;vertical-align:top;";

        StringBuilder sb = new StringBuilder();
        sb.append("<table style=\"border-collapse:collapse;width:100%;margin-top:8px;\">")
                .append("<thead><tr>")
                .append("<th style=\"").append(thStyle).append("\">")
                .append(isDe ? "Vortrag" : "Talk").append("</th>")
                .append("<th style=\"").append(thStyle).append("\">")
                .append(isDe ? "Sprecher·in" : "Speaker").append("</th>")
                .append("</tr></thead><tbody>");

        boolean hasRows = false;
        for (Session session : sessions) {
            if (STRUCTURAL_SESSION_TYPES.contains(session.getSessionType())) {
                continue;
            }

            List<SessionSpeakerResponse> speakers =
                    sessionUserService.getSessionSpeakers(session.getId());
            if (speakers.isEmpty()) {
                continue;
            }

            String title = speakers.stream()
                    .map(SessionSpeakerResponse::getPresentationTitle)
                    .filter(t -> t != null && !t.isBlank())
                    .findFirst()
                    .orElse(session.getTitle());

            String speakerNames = speakers.stream()
                    .map(sp -> {
                        String fn = sp.getFirstName() != null ? sp.getFirstName() : "";
                        String ln = sp.getLastName() != null ? sp.getLastName() : "";
                        String name = (fn + " " + ln).trim();
                        if (name.isBlank()) {
                            name = sp.getUsername();
                        }
                        String company = sp.getCompany() != null ? sp.getCompany().trim() : "";
                        return company.isBlank() ? name : name + ", " + company;
                    })
                    .collect(Collectors.joining("; "));

            sb.append("<tr>")
                    .append("<td style=\"").append(tdStyle).append("\">")
                    .append(escapeHtml(title)).append("</td>")
                    .append("<td style=\"").append(tdMutedStyle).append("\">")
                    .append(escapeHtml(speakerNames)).append("</td>")
                    .append("</tr>");
            hasRows = true;
        }

        if (!hasRows) {
            return "";
        }
        sb.append("</tbody></table>");
        return sb.toString();
    }

    String buildUpcomingEventsSection(UUID excludeEventId, boolean isDe) {
        Instant now = Instant.now();
        List<Event> future = eventRepository.findByDateAfter(now).stream()
                .filter(e -> !e.getId().equals(excludeEventId))
                .filter(e -> e.getWorkflowState() != EventWorkflowState.ARCHIVED)
                .toList();
        if (future.isEmpty()) {
            return "";
        }
        Locale locale = isDe ? Locale.GERMAN : Locale.ENGLISH;
        DateTimeFormatter dateFormatter = DateTimeFormatter
                .ofPattern("EEEE, dd.MM.yyyy", locale)
                .withZone(ZoneId.of("Europe/Zurich"));
        StringBuilder sb = new StringBuilder();
        String headerStyle = "margin: 0 0 8px; font-size: 14px; font-weight: 600; color: #18181B;";
        String headerHtml = isDe
                ? "<p style=\"" + headerStyle + "\">Weitere BAT-Termine:</p>"
                : "<p style=\"" + headerStyle + "\">Upcoming events:</p>";
        sb.append(headerHtml);
        sb.append("<table style=\"border-collapse:collapse;width:100%;margin:0 0 16px;\">");
        for (Event e : future) {
            String code = e.getEventCode();
            String date = e.getDate() != null ? dateFormatter.format(e.getDate()) : "";
            String title = e.getTitle() != null ? e.getTitle() : (isDe ? "Thema noch offen" : "Topic TBD");
            String tdStyle = "padding:5px 10px;border:1px solid #E4E4E7;font-size:13px;";
            sb.append("<tr>")
                    .append("<td style=\"").append(tdStyle).append("\">")
                    .append(escapeHtml(code)).append("</td>")
                    .append("<td style=\"").append(tdStyle).append("\">")
                    .append(escapeHtml(date)).append("</td>")
                    .append("<td style=\"").append(tdStyle).append("font-style:italic;\">")
                    .append(escapeHtml(title)).append("</td>")
                    .append("</tr>");
        }
        sb.append("</table>");
        return sb.toString();
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private String resolveTemplateKey(@Nullable String templateKey) {
        return (templateKey != null && !templateKey.isBlank()) ? templateKey : DEFAULT_TEMPLATE_KEY;
    }

    private String renderContent(String locale, Map<String, String> vars, String templateKey) {
        Optional<ch.batbern.events.domain.EmailTemplate> templateOpt =
                emailTemplateService.findByKeyAndLocale(templateKey, locale);
        if (templateOpt.isEmpty()) {
            log.warn("Newsletter template '{}' locale '{}' not found in DB", templateKey, locale);
            return "<p>Newsletter template not available.</p>";
        }
        return emailService.replaceVariables(templateOpt.get().getHtmlBody(), vars);
    }

    private String buildSubject(Event event, boolean isReminder, String locale,
                                Map<String, String> vars, String templateKey) {
        Optional<String> subjectTemplate = emailTemplateService.resolveSubject(templateKey, locale);
        String subject = subjectTemplate.orElseGet(() ->
                (isReminder ? vars.get("reminderPrefix") : "") + event.getTitle() + " — BATbern");
        return emailService.replaceVariables(subject, vars);
    }

    private String buildReminderPrefix(boolean isReminder, boolean isDe) {
        if (!isReminder) {
            return "";
        }
        return isDe ? "Erinnerung: " : "Reminder: ";
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
        DateTimeFormatter formatter = DateTimeFormatter
                .ofPattern("EEEE, d. MMMM yyyy", locale)
                .withZone(ZoneId.of("Europe/Zurich"));
        return formatter.format(event.getDate());
    }

    private String formatEventTime(Event event, boolean isDe) {
        return isDe ? "ab 16:00 Uhr" : "from 4:00 PM";
    }

    private String computeFinalStatus(int sentCount, int failedCount) {
        if (failedCount == 0) {
            return STATUS_COMPLETED;
        }
        if (sentCount == 0) {
            return STATUS_FAILED;
        }
        return STATUS_PARTIAL;
    }

    private static String escapeHtml(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;");
    }

    @SuppressWarnings("java:S2142") // intentional sleep for rate limiting
    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
