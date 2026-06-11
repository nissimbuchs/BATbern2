package ch.batbern.events.controller;

import ch.batbern.events.dto.QnaPostRequest;
import ch.batbern.events.dto.QnaPostResponse;
import ch.batbern.events.dto.QnaWindowResponse;
import ch.batbern.events.security.SecurityContextHelper;
import ch.batbern.events.service.SessionQnaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Per-session Q&A endpoints (Story 7.5 "The Apéro Continues"). The THREAD is per session; the
 * window's lifecycle (open/close/extend) and config live at the EVENT level now — see
 * {@code EventQnaController} + the event Settings tab (Story 7.5 rework).
 *
 * <p>Mixed auth (mirrors {@code SessionMaterialsController}):
 * <ul>
 *   <li>{@code GET .../qna} — PUBLIC: read the open or frozen thread (anonymous can read, AC3).</li>
 *   <li>{@code POST .../qna/posts} — AUTHENTICATED: any logged-in user may post (AC2);
 *       anonymous → 401 at the gateway. Rejected if the window is frozen (409, AC5).</li>
 *   <li>{@code DELETE .../qna/posts/{id}} — ORGANIZER only (AC4): takedown (soft-delete tombstone).</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/events/{eventCode}/sessions/{sessionSlug}/qna")
@RequiredArgsConstructor
@Slf4j
public class SessionQnaController {

    private final SessionQnaService qnaService;
    private final SecurityContextHelper securityContextHelper;

    /** Public: read the Q&A thread for a session (open or frozen). */
    @GetMapping
    public ResponseEntity<QnaWindowResponse> getThread(
            @PathVariable String eventCode,
            @PathVariable String sessionSlug) {
        return ResponseEntity.ok(qnaService.getThread(eventCode, sessionSlug));
    }

    /** Authenticated: post a question or answer. */
    @PostMapping("/posts")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<QnaPostResponse> addPost(
            @PathVariable String eventCode,
            @PathVariable String sessionSlug,
            @Valid @RequestBody QnaPostRequest request) {
        String username = securityContextHelper.getCurrentUsername();
        QnaPostResponse post = qnaService.addPost(
                eventCode, sessionSlug, request.getBody(), request.getParentPostId(), username);
        return ResponseEntity.status(HttpStatus.CREATED).body(post);
    }

    /** Organizer: take down a post (soft-delete tombstone). */
    @DeleteMapping("/posts/{postId}")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<Void> removePost(
            @PathVariable String eventCode,
            @PathVariable String sessionSlug,
            @PathVariable UUID postId) {
        qnaService.removePost(eventCode, sessionSlug, postId);
        return ResponseEntity.noContent().build();
    }
}
