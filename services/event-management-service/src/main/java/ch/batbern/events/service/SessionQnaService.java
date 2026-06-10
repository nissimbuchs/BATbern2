package ch.batbern.events.service;

import ch.batbern.events.domain.Event;
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
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

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

    /** Default window length in days (organizer-extendable). AC1: 14 days. */
    @Value("${qna.window.default-days:14}")
    private int defaultWindowDays;

    private final SessionQnaWindowRepository windowRepository;
    private final SessionQnaPostRepository postRepository;
    private final SessionRepository sessionRepository;
    private final EventRepository eventRepository;

    /**
     * Open a Q&A window for every session of a just-completed event (AC1). Idempotent: sessions
     * that already have a window are skipped, so a re-fired completion event creates no duplicates.
     */
    @Transactional
    public void openWindowsForCompletedEvent(String eventCode) {
        Event event = eventRepository.findByEventCode(eventCode)
                .orElseThrow(() -> new EntityNotFoundException("Event not found: " + eventCode));

        List<Session> sessions = sessionRepository.findByEventId(event.getId());
        Instant closesAt = Instant.now().plus(defaultWindowDays, ChronoUnit.DAYS);
        int opened = 0;
        for (Session session : sessions) {
            if (windowRepository.existsBySessionId(session.getId())) {
                continue;
            }
            windowRepository.save(SessionQnaWindow.builder()
                    .sessionId(session.getId())
                    .eventCode(eventCode)
                    .status(QnaWindowStatus.OPEN)
                    .opensAt(Instant.now())
                    .closesAt(closesAt)
                    .build());
            opened++;
        }
        log.info("Opened {} Q&A window(s) for completed event {} ({} sessions total)",
                opened, eventCode, sessions.size());
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
        }
        SessionQnaPost saved = postRepository.save(SessionQnaPost.builder()
                .windowId(window.getId())
                .parentPostId(parentPostId)
                .postedByUsername(username)
                .body(body)
                .build());
        log.info("Q&A post by {} on session {} (event {})", username, sessionSlug, eventCode);
        return toPostResponse(saved);
    }

    /**
     * Organizer adjusts the window (AC4): extend ({@code closesAt} → reopen to that time) or close
     * early ({@code close = true} → freeze now).
     */
    @Transactional
    public QnaWindowResponse patchWindow(String eventCode, String sessionSlug,
                                         Instant closesAt, Boolean close) {
        SessionQnaWindow window = loadWindow(eventCode, sessionSlug);
        if (Boolean.TRUE.equals(close)) {
            window.setStatus(QnaWindowStatus.FROZEN);
            window.setClosesAt(Instant.now());
        } else if (closesAt != null) {
            window.setClosesAt(closesAt);
            window.setStatus(QnaWindowStatus.OPEN);
        } else {
            throw new IllegalArgumentException("Provide either a new closesAt (extend) or close=true.");
        }
        windowRepository.save(window);
        log.info("Organizer adjusted Q&A window for session {} (event {}): status={}, closesAt={}",
                sessionSlug, eventCode, window.getStatus(), window.getClosesAt());
        return toWindowResponse(window);
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
        List<QnaPostResponse> posts = postRepository
                .findByWindowIdOrderByCreatedAtAsc(window.getId())
                .stream()
                .map(this::toPostResponse)
                .toList();
        return new QnaWindowResponse(window.getStatus().name(), window.getOpensAt(),
                window.getClosesAt(), posts);
    }

    private QnaPostResponse toPostResponse(SessionQnaPost post) {
        boolean removed = post.isRemoved();
        return new QnaPostResponse(
                post.getId(),
                post.getParentPostId(),
                removed ? null : post.getPostedByUsername(),
                removed ? null : post.getBody(),
                removed,
                post.getCreatedAt());
    }
}
