package ch.batbern.events.service;

import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.QnaWindowStatus;
import ch.batbern.events.domain.Session;
import ch.batbern.events.domain.SessionQnaNotification;
import ch.batbern.events.domain.SessionQnaPost;
import ch.batbern.events.domain.SessionQnaWindow;
import ch.batbern.events.domain.SessionUser;
import ch.batbern.events.client.UserApiClient;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.SessionQnaNotificationRepository;
import ch.batbern.events.repository.SessionQnaPostRepository;
import ch.batbern.events.repository.SessionQnaWindowRepository;
import ch.batbern.events.repository.SessionRepository;
import ch.batbern.events.repository.SessionUserRepository;
import ch.batbern.shared.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.Year;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Story 15.7 — emails a session's speaker / co-speakers / moderator when new top-level questions
 * arrive in their Q&A, at a cadence each recipient chose ({@code qnaNotificationFrequency}:
 * {@code live} | {@code daily} | {@code off}).
 *
 * <p><b>One scheduled flush, per-recipient cadence.</b> A single 5-minute job scans the OPEN Q&A
 * windows; for each recipient it derives a throttle from their preference ({@code live} = 15 min,
 * {@code daily} = 24 h, {@code off} = skip) and sends at most one "N new question(s)" digest per
 * window per throttle window. The per-(window, recipient) {@link SessionQnaNotification} row holds
 * the throttle anchor ({@code lastNotifiedAt}) and a high-water mark ({@code notifiedThrough}) so
 * each email counts only genuinely new questions.
 *
 * <p><b>Freeze wins everywhere</b> (AC8): only {@code OPEN} windows are scanned — a FROZEN window
 * never produces a digest, for any cadence.
 *
 * <p><b>Exclusions</b> (AC9): replies (non-top-level) never notify; a self-post never notifies its
 * own author (but still notifies the other recipients); PANELIST is not a recipient.
 *
 * <p>Recipient email + locale + the cadence preference are resolved at send time from CUMS via the
 * cached {@link UserApiClient} (ADR-004); nothing is duplicated on the notification row. Per-recipient
 * failures are isolated so one bad lookup/send never aborts the rest of the flush. In the {@code test}
 * profile {@link EmailService} is a no-op (no SES client), satisfying the staging-safe requirement.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class QnaNotificationService {

    static final String TEMPLATE_KEY = "qna-new-questions";
    private static final String LAYOUT_KEY = "batbern-default";

    /**
     * Session-scoped Q&A recipients: the session's own presenters. PANELIST is intentionally
     * excluded (AC9). The event MODERATOR is added separately (event-scoped, not per-session) —
     * see {@link #processWindow}.
     */
    private static final Set<SessionUser.SpeakerRole> SESSION_SPEAKER_ROLES = Set.of(
            SessionUser.SpeakerRole.PRIMARY_SPEAKER,
            SessionUser.SpeakerRole.CO_SPEAKER);

    private final SessionQnaWindowRepository windowRepository;
    private final SessionQnaPostRepository postRepository;
    private final SessionQnaNotificationRepository notificationRepository;
    private final SessionUserRepository sessionUserRepository;
    private final SessionRepository sessionRepository;
    private final EventRepository eventRepository;
    private final UserApiClient userApiClient;
    private final EmailService emailService;
    private final EmailTemplateService emailTemplateService;
    private final QnaNotificationStateWriter stateWriter;

    @Value("${app.base-url:https://batbern.ch}")
    private String baseUrl;

    @Value("${qna.notify.live-window-minutes:15}")
    private long liveWindowMinutes;

    @Value("${qna.notify.daily-window-hours:24}")
    private long dailyWindowHours;

    /**
     * Scan OPEN Q&A windows and send pending digests. Runs every 5 min; ShedLock ensures a single
     * instance runs it across the cluster.
     *
     * <p><b>Deliberately not {@code @Transactional}.</b> Each recipient's email is sent and its
     * throttle/water-mark row is then committed in its own transaction (via
     * {@link QnaNotificationStateWriter}). Wrapping the whole scan in one transaction would let a
     * later failure roll back an already-sent recipient's water mark and re-send the digest on the
     * next flush. Reads here run in Spring Data's default per-call transactions.
     */
    @Scheduled(cron = "${qna.scheduled.notify.cron:0 */5 * * * *}")
    @SchedulerLock(name = "qnaDigestFlush", lockAtMostFor = "15m", lockAtLeastFor = "30s")
    public void flushPending() {
        List<SessionQnaWindow> openWindows = windowRepository.findByStatus(QnaWindowStatus.OPEN);
        if (openWindows.isEmpty()) {
            log.debug("qnaDigestFlush: no open Q&A windows");
            return;
        }
        int sent = 0;
        for (SessionQnaWindow window : openWindows) {
            try {
                sent += processWindow(window);
            } catch (Exception e) {
                // Per-window isolation — one window's failure must not abort the rest.
                log.error("qnaDigestFlush: window {} (event {}) failed: {}",
                        window.getId(), window.getEventCode(), e.getMessage(), e);
            }
        }
        if (sent > 0) {
            log.info("qnaDigestFlush: sent {} Q&A digest email(s)", sent);
        }
    }

    private int processWindow(SessionQnaWindow window) {
        UUID sessionId = window.getSessionId();
        Session session = sessionRepository.findById(sessionId).orElse(null);
        if (session == null) {
            log.warn("qnaDigestFlush: no session {} for window {}", sessionId, window.getId());
            return 0;
        }
        Event event = eventRepository.findByEventCode(window.getEventCode()).orElse(null);

        // Recipients = this session's own presenters (PRIMARY_SPEAKER / CO_SPEAKER) +
        // the moderator(s) of the WHOLE event (event-scoped, not the per-session moderator).
        Set<String> recipients = new LinkedHashSet<>();
        for (SessionUser su : sessionUserRepository.findBySessionId(sessionId)) {
            if (su.getUsername() != null && SESSION_SPEAKER_ROLES.contains(su.getSpeakerRole())) {
                recipients.add(su.getUsername());
            }
        }
        recipients.addAll(sessionUserRepository.findModeratorUsernamesByEventCode(window.getEventCode()));

        int sent = 0;
        for (String recipient : recipients) {
            try {
                if (notifyRecipient(window, session, event, recipient)) {
                    sent++;
                }
            } catch (Exception e) {
                // Per-recipient isolation — one recipient's failure must not abort the others.
                log.error("qnaDigestFlush: recipient {} on window {} failed: {}",
                        recipient, window.getId(), e.getMessage(), e);
            }
        }
        return sent;
    }

    private boolean notifyRecipient(SessionQnaWindow window, Session session, Event event,
                                    String recipient) {
        String freq = Optional.ofNullable(userApiClient.getQnaNotificationFrequency(recipient))
                .orElse("live");
        if ("off".equals(freq)) {
            return false;
        }
        Duration throttle = "daily".equals(freq)
                ? Duration.ofHours(dailyWindowHours)
                : Duration.ofMinutes(liveWindowMinutes);

        SessionQnaNotification state = notificationRepository
                .findByIdWindowIdAndIdRecipientUsername(window.getId(), recipient)
                .orElse(null);
        Instant now = Instant.now();
        if (state != null && state.getLastNotifiedAt() != null
                && Duration.between(state.getLastNotifiedAt(), now).compareTo(throttle) < 0) {
            return false; // throttled — within this recipient's cadence window
        }

        Instant since = (state != null && state.getNotifiedThrough() != null)
                ? state.getNotifiedThrough() : Instant.EPOCH;
        List<SessionQnaPost> newPosts = postRepository.findNewTopLevelQuestionsForRecipient(
                window.getId(), since, recipient);
        if (newPosts.isEmpty()) {
            return false;
        }
        int count = newPosts.size();
        Instant newWatermark = newPosts.get(newPosts.size() - 1).getCreatedAt(); // oldest-first

        String email = resolveEmail(recipient);
        if (email == null || email.isBlank()) {
            log.warn("qnaDigestFlush: no email for recipient {} — skipping", recipient);
            return false;
        }
        String locale = localeFor(userApiClient.getPreferredLanguage(recipient));

        RenderedMail mail = renderMail(window, session, event, recipient, count, locale);
        emailService.sendHtmlEmailSync(email, mail.subject(), mail.html());

        // Commit the throttle/water-mark in its own transaction, immediately after the send, so a
        // later recipient's failure can't roll it back and trigger a duplicate digest next flush.
        stateWriter.recordSent(window.getId(), recipient, now, newWatermark);

        log.info("qnaDigestFlush: notified {} of {} new question(s) on session {} (event {})",
                recipient, count, session.getSessionSlug(), window.getEventCode());
        return true;
    }

    private String resolveEmail(String recipient) {
        try {
            return userApiClient.getUserByUsername(recipient).getEmail();
        } catch (Exception e) {
            log.warn("qnaDigestFlush: could not resolve email for {} ({})", recipient, e.getMessage());
            return null;
        }
    }

    private String resolveFirstName(String recipient) {
        try {
            String first = userApiClient.getUserByUsername(recipient).getFirstName();
            return (first != null && !first.isBlank()) ? first : recipient;
        } catch (Exception e) {
            return recipient;
        }
    }

    /** DE+EN only (CLAUDE.md email rule): {@code de*} → German, anything else → English. */
    private String localeFor(String preferredLanguage) {
        if (preferredLanguage != null
                && preferredLanguage.toLowerCase(Locale.ROOT).startsWith("de")) {
            return "de";
        }
        return "en";
    }

    private RenderedMail renderMail(SessionQnaWindow window, Session session, Event event,
                                    String recipient, int count, String locale) {
        Map<String, String> vars = buildVariables(window, session, event, recipient, count);

        String contentHtml = emailTemplateService.findByKeyAndLocale(TEMPLATE_KEY, locale)
                .map(t -> emailService.replaceVariables(t.getHtmlBody(), vars))
                .orElseGet(() -> {
                    log.warn("Q&A template '{}' locale '{}' not found in DB", TEMPLATE_KEY, locale);
                    return "<p>New questions in your session Q&amp;A.</p>";
                });
        String mergedHtml = emailService.replaceVariables(
                emailTemplateService.mergeWithLayout(contentHtml, LAYOUT_KEY, locale), vars);

        String subject = emailTemplateService.resolveSubject(TEMPLATE_KEY, locale)
                .map(s -> emailService.replaceVariables(s, vars))
                .orElseGet(() -> ("de".equals(locale)
                        ? "Neue Fragen in der Q&A — " : "New questions in your Q&A — ")
                        + nullToEmpty(session.getTitle()));

        return new RenderedMail(subject, mergedHtml);
    }

    private Map<String, String> buildVariables(SessionQnaWindow window, Session session,
                                               Event event, String recipient, int count) {
        String eventCode = window.getEventCode();
        Map<String, String> vars = new HashMap<>();
        vars.put("count", String.valueOf(count));
        vars.put("isSingle", count == 1 ? "1" : "");
        vars.put("isMultiple", count > 1 ? "1" : "");
        vars.put("sessionTitle", nullToEmpty(session.getTitle()));
        vars.put("eventTitle", event != null ? nullToEmpty(event.getTitle()) : "");
        vars.put("eventNumber",
                (event != null && event.getEventNumber() != null)
                        ? String.valueOf(event.getEventNumber()) : "");
        vars.put("recipientName", resolveFirstName(recipient));
        // Deep link to the public event page, which hosts the per-session Q&A thread.
        vars.put("qnaLink", baseUrl + "/events/" + eventCode);
        vars.put("currentYear", String.valueOf(Year.now().getValue()));
        // batbern-default layout variables (without these the merged email shows literal
        // {{logoUrl}} etc. — replaceVariables leaves unmatched placeholders untouched).
        vars.put("logoUrl", baseUrl + "/BATbern_white_logo.png");
        vars.put("eventUrl", baseUrl + "/events/" + eventCode);
        vars.put("dashboardLink", baseUrl);
        vars.put("supportUrl", baseUrl);
        return vars;
    }

    private static String nullToEmpty(String s) {
        return s != null ? s : "";
    }

    /** Rendered subject + HTML body for one digest email. */
    private record RenderedMail(String subject, String html) {
    }
}
