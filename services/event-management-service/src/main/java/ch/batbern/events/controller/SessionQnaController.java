package ch.batbern.events.controller;

import ch.batbern.events.sessions.api.generated.SessionQnAApi;
import ch.batbern.events.sessions.dto.generated.QnaPostRequest;
import ch.batbern.events.sessions.dto.generated.QnaPostResponse;
import ch.batbern.events.sessions.dto.generated.QnaWindowResponse;
import ch.batbern.events.security.SecurityContextHelper;
import ch.batbern.events.service.SessionQnaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Per-session Q&A endpoints (Story 7.5 "The Apéro Continues"). The THREAD is per session; the
 * window's lifecycle (open/close/extend) and config live at the EVENT level now — see
 * {@code EventQnaController} + the event Settings tab (Story 7.5 rework).
 *
 * <p>{@code implements} the generated {@link SessionQnAApi} interface (event-sessions spec,
 * ADR-006 contract-first); the class-level {@code @RequestMapping("/api/v1")} supplies the
 * version prefix. Method-level {@code @PreAuthorize} (mixed auth) stays on the overrides:
 * <ul>
 *   <li>{@code GET .../qna} — PUBLIC: read the open or frozen thread (anonymous can read, AC3).</li>
 *   <li>{@code POST .../qna/posts} — AUTHENTICATED: any logged-in user may post (AC2);
 *       anonymous → 401 at the gateway. Rejected if the window is frozen (409, AC5).</li>
 *   <li>{@code DELETE .../qna/posts/{id}} — ORGANIZER only (AC4): takedown (soft-delete tombstone).</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
public class SessionQnaController implements SessionQnAApi {

    private final SessionQnaService qnaService;
    private final SecurityContextHelper securityContextHelper;

    /** Public: read the Q&A thread for a session (open or frozen). */
    @Override
    public ResponseEntity<QnaWindowResponse> getSessionQna(String eventCode, String sessionSlug) {
        return ResponseEntity.ok(qnaService.getThread(eventCode, sessionSlug));
    }

    /** Authenticated: post a question or answer. */
    @Override
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<QnaPostResponse> postSessionQna(
            String eventCode,
            String sessionSlug,
            QnaPostRequest request) {
        String username = securityContextHelper.getCurrentUsername();
        QnaPostResponse post = qnaService.addPost(
                eventCode, sessionSlug, request.getBody(), request.getParentPostId(), username);
        return ResponseEntity.status(HttpStatus.CREATED).body(post);
    }

    /** Organizer: take down a post (soft-delete tombstone). */
    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<Void> removeSessionQnaPost(String eventCode, String sessionSlug, UUID postId) {
        qnaService.removePost(eventCode, sessionSlug, postId);
        return ResponseEntity.noContent().build();
    }
}
