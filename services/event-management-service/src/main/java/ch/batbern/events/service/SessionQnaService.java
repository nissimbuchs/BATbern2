package ch.batbern.events.service;

import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.QnaOpenTrigger;
import ch.batbern.events.domain.QnaWindowStatus;
import ch.batbern.events.domain.Session;
import ch.batbern.events.domain.SessionQnaPost;
import ch.batbern.events.domain.SessionQnaWindow;
import ch.batbern.events.dto.QnaPostResponse;
import ch.batbern.events.dto.QnaWindowResponse;
import ch.batbern.events.exception.QnaWindowFrozenException;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.SessionQnaPostRepository;
import ch.batbern.events.repository.SessionQnaWindowRepository;
import ch.batbern.events.repository.SessionRepository;
import ch.batbern.events.repository.SessionUserRepository;
import ch.batbern.events.repository.QnaAuthorProjection;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for per-session Q&A (Story 7.5 "The Apéro Continues").
 *
 * <p>Windows open (one per session) when an event completes, accept posts from logged-in users for
 * a configurable default window, then freeze to a permanent read-only thread on the archive.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SessionQnaService {

    private static final int FALLBACK_WINDOW_DAYS = 14;

    private final SessionQnaWindowRepository windowRepository;
    private final SessionQnaPostRepository postRepository;
    private final SessionRepository sessionRepository;
    private final EventRepository eventRepository;
    private final SessionUserRepository sessionUserRepository;

    /**
     * Story 7.5 rework: open a Q&A window for every session of an event, but ONLY when the event's
     * configured {@code qnaOpenTrigger} matches {@code expectedTrigger} and Q&A is enabled. Called
     * from two places: the EVENT_COMPLETED listener (with {@code EVENT_COMPLETED}) and the
     * speakers-phase-published listener (with {@code SPEAKERS_PUBLISHED}). Idempotent — sessions
     * that already have a window are skipped, so a re-fired trigger creates no duplicates.
     *
     * <p>Windows close at {@code event date + qnaWindowDays} ("completion + N days"; for the
     * pre-event SPEAKERS_PUBLISHED trigger this keeps the window open through the event).
     */
    @Transactional
    public void openWindowsIfTrigger(String eventCode, QnaOpenTrigger expectedTrigger) {
        Event event = eventRepository.findByEventCode(eventCode)
                .orElseThrow(() -> new EntityNotFoundException("Event not found: " + eventCode));

        if (!Boolean.TRUE.equals(event.getQnaEnabled())) {
            log.debug("Q&A disabled for event {} — not opening windows", eventCode);
            return;
        }
        if (event.getQnaOpenTrigger() != expectedTrigger) {
            log.debug("Event {} Q&A trigger is {}, not {} — skipping",
                    eventCode, event.getQnaOpenTrigger(), expectedTrigger);
            return;
        }

        int opened = ensureWindowsOpen(event, defaultClosesAt(event), false);
        log.info("Opened {} Q&A window(s) for event {} (trigger {})", opened, eventCode, expectedTrigger);
    }

    /**
     * Story 7.5 rework follow-up: MANUALLY open (or reopen) the Q&A for an event — create a window
     * for every session that lacks one and (re)open every existing window. Independent of the
     * configured trigger, so an organizer can open the Q&A on demand, and crucially can open an
     * event whose trigger moment already passed (e.g. speakers were published before this feature
     * shipped, or the trigger was set to SPEAKERS_PUBLISHED after the lineup was already public —
     * the original cause of "No Q&A windows exist for event …" on a manual reopen).
     *
     * @param closesAt optional explicit close time; defaults to event date + qnaWindowDays.
     * @return number of session windows now open
     */
    @Transactional
    public int openWindowsManually(String eventCode, Instant closesAt) {
        Event event = eventRepository.findByEventCode(eventCode)
                .orElseThrow(() -> new EntityNotFoundException("Event not found: " + eventCode));
        Instant effectiveClose = closesAt != null ? closesAt : defaultClosesAt(event);
        int count = ensureWindowsOpen(event, effectiveClose, true);
        log.info("Organizer manually opened/reopened {} Q&A window(s) for event {} (closes {})",
                count, eventCode, effectiveClose);
        return count;
    }

    /** Default window close: event date (proxy for completion) + qnaWindowDays. */
    private Instant defaultClosesAt(Event event) {
        int windowDays = event.getQnaWindowDays() != null ? event.getQnaWindowDays() : FALLBACK_WINDOW_DAYS;
        Instant base = event.getDate() != null ? event.getDate() : Instant.now();
        return base.plus(windowDays, ChronoUnit.DAYS);
    }

    /**
     * Ensure every session of the event has an OPEN window closing at {@code closesAt}. Missing
     * windows are created; for existing ones, {@code reopenExisting} controls whether they are
     * (re)set to OPEN + the new close time (manual open) or left untouched (idempotent trigger open).
     *
     * @return number of session windows that are open afterwards
     */
    private int ensureWindowsOpen(Event event, Instant closesAt, boolean reopenExisting) {
        List<Session> sessions = sessionRepository.findByEventId(event.getId());
        int open = 0;
        for (Session session : sessions) {
            SessionQnaWindow existing = windowRepository.findBySessionId(session.getId()).orElse(null);
            if (existing == null) {
                windowRepository.save(SessionQnaWindow.builder()
                        .sessionId(session.getId())
                        .eventCode(event.getEventCode())
                        .status(QnaWindowStatus.OPEN)
                        .opensAt(Instant.now())
                        .closesAt(closesAt)
                        .build());
                open++;
            } else if (reopenExisting) {
                existing.setStatus(QnaWindowStatus.OPEN);
                existing.setClosesAt(closesAt);
                windowRepository.save(existing);
                open++;
            }
        }
        return open;
    }

    /** Public read of a session's Q&A thread (open or frozen). */
    @Transactional(readOnly = true)
    public QnaWindowResponse getThread(String eventCode, String sessionSlug) {
        SessionQnaWindow window = loadWindow(eventCode, sessionSlug);
        return toWindowResponse(window);
    }

    /** Post a question/answer (AC2). Rejected if the window has frozen (AC5). */
    @Transactional
    public QnaPostResponse addPost(String eventCode, String sessionSlug, String body,
                                   UUID parentPostId, String username) {
        SessionQnaWindow window = loadWindow(eventCode, sessionSlug);
        if (window.getStatus() == QnaWindowStatus.FROZEN) {
            throw new QnaWindowFrozenException(
                    "The Q&A for this session has closed — no further posts are accepted.");
        }
        if (parentPostId != null) {
            SessionQnaPost parent = postRepository.findById(parentPostId)
                    .orElseThrow(() -> new EntityNotFoundException("Parent post not found: " + parentPostId));
            if (!parent.getWindowId().equals(window.getId())) {
                throw new IllegalArgumentException("Parent post does not belong to this session's Q&A.");
            }
            // One-level threading only (question → answer). A reply-to-an-answer would be persisted
            // but never rendered (the thread view groups answers by their parent question id), so it
            // would silently vanish — reject it instead.
            if (parent.getParentPostId() != null) {
                throw new IllegalArgumentException(
                        "Replies are one level deep — reply to the question, not to an answer.");
            }
            // Don't allow replying to an organizer-removed (tombstoned) post.
            if (parent.isRemoved()) {
                throw new IllegalArgumentException("Cannot reply to a removed post.");
            }
        }
        SessionQnaPost saved = postRepository.save(SessionQnaPost.builder()
                .windowId(window.getId())
                .parentPostId(parentPostId)
                .postedByUsername(username)
                .body(body)
                .build());
        log.info("Q&A post by {} on session {} (event {})", username, sessionSlug, eventCode);
        return toPostResponse(saved, loadAuthorPortraits(List.of(username)));
    }

    /**
     * Story 7.5 rework — EVENT-LEVEL adjust (AC4): extend ({@code closesAt} → reopen all of the
     * event's windows to that time) or close early ({@code close = true} → freeze all now). There
     * is no per-session control anymore; the organizer manages the whole event's Q&A as a unit.
     *
     * @return the number of windows adjusted
     */
    @Transactional
    public int adjustWindows(String eventCode, Instant closesAt, Boolean close) {
        List<SessionQnaWindow> windows = windowRepository.findByEventCode(eventCode);
        if (windows.isEmpty()) {
            throw new EntityNotFoundException("No Q&A windows exist for event " + eventCode);
        }
        boolean closing = Boolean.TRUE.equals(close);
        if (!closing && closesAt == null) {
            throw new IllegalArgumentException("Provide either a new closesAt (extend) or close=true.");
        }
        for (SessionQnaWindow window : windows) {
            if (closing) {
                window.setStatus(QnaWindowStatus.FROZEN);
                window.setClosesAt(Instant.now());
            } else {
                window.setClosesAt(closesAt);
                window.setStatus(QnaWindowStatus.OPEN);
            }
        }
        windowRepository.saveAll(windows);
        log.info("Organizer adjusted {} Q&A window(s) for event {}: {}",
                windows.size(), eventCode, closing ? "closed" : "extended to " + closesAt);
        return windows.size();
    }

    /** Organizer takedown (AC4): soft-delete a post (tombstone), preserving thread structure. */
    @Transactional
    public void removePost(String eventCode, String sessionSlug, UUID postId) {
        SessionQnaWindow window = loadWindow(eventCode, sessionSlug);
        SessionQnaPost post = postRepository.findById(postId)
                .orElseThrow(() -> new EntityNotFoundException("Post not found: " + postId));
        if (!post.getWindowId().equals(window.getId())) {
            throw new IllegalArgumentException("Post does not belong to this session's Q&A.");
        }
        if (post.getRemovedAt() == null) {
            post.setRemovedAt(Instant.now());
            postRepository.save(post);
        }
        log.info("Organizer removed Q&A post {} on session {} (event {})", postId, sessionSlug, eventCode);
    }

    private SessionQnaWindow loadWindow(String eventCode, String sessionSlug) {
        Session session = sessionRepository.findByEventCodeAndSessionSlug(eventCode, sessionSlug)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Session not found: " + sessionSlug + " for event " + eventCode));
        return windowRepository.findBySessionId(session.getId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "No Q&A window for session " + sessionSlug));
    }

    private QnaWindowResponse toWindowResponse(SessionQnaWindow window) {
        List<SessionQnaPost> postEntities =
                postRepository.findByWindowIdOrderByCreatedAtAsc(window.getId());
        // Enrich each (non-removed) poster with display name + company logo in ONE batched,
        // anonymous-safe local DB query (the Q&A GET is public — no JWT for UserApiClient).
        Set<String> authorUsernames = postEntities.stream()
                .filter(p -> !p.isRemoved())
                .map(SessionQnaPost::getPostedByUsername)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<String, QnaAuthorProjection> portraits = loadAuthorPortraits(authorUsernames);
        List<QnaPostResponse> posts =
                postEntities.stream().map(p -> toPostResponse(p, portraits)).toList();
        return new QnaWindowResponse(window.getStatus().name(), window.getOpensAt(),
                window.getClosesAt(), posts);
    }

    /** Batch-load poster portraits keyed by username (empty map for no usernames). */
    private Map<String, QnaAuthorProjection> loadAuthorPortraits(Collection<String> usernames) {
        if (usernames.isEmpty()) {
            return Map.of();
        }
        return sessionUserRepository.findQnaAuthorPortraitsByUsernames(usernames).stream()
                .collect(Collectors.toMap(QnaAuthorProjection::getUsername, p -> p, (a, b) -> a));
    }

    private QnaPostResponse toPostResponse(SessionQnaPost post,
                                           Map<String, QnaAuthorProjection> portraits) {
        if (post.isRemoved()) {
            // Tombstone — null out identity + content (the UI shows "removed by organizer").
            return new QnaPostResponse(post.getId(), post.getParentPostId(),
                    null, null, null, null, null, null, true, post.getCreatedAt());
        }
        QnaAuthorProjection a = portraits.get(post.getPostedByUsername());
        // Honour the user's "show company" preference; absent projection → no enrichment.
        boolean showCompany = a != null && !Boolean.FALSE.equals(a.getShowCompany());
        return new QnaPostResponse(
                post.getId(),
                post.getParentPostId(),
                post.getPostedByUsername(),
                a != null ? a.getFirstName() : null,
                a != null ? a.getLastName() : null,
                showCompany ? a.getCompanyDisplayName() : null,
                showCompany ? a.getCompanyLogoUrl() : null,
                post.getBody(),
                false,
                post.getCreatedAt());
    }
}
