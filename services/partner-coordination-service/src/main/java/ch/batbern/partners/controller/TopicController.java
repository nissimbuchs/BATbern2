package ch.batbern.partners.controller;

import ch.batbern.partners.dto.TopicDTO;
import ch.batbern.partners.dto.TopicStatusUpdateRequest;
import ch.batbern.partners.dto.TopicSuggestionRequest;
import ch.batbern.partners.security.SecurityContextHelper;
import ch.batbern.partners.service.TopicService;
import io.micrometer.core.annotation.Timed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for Partner Topic Suggestions & Voting — Story 8.2.
 *
 * Endpoints:
 *   GET    /api/v1/partners/topics               → list topics (PARTNER + ORGANIZER)
 *   POST   /api/v1/partners/topics               → suggest topic (PARTNER)
 *   POST   /api/v1/partners/topics/{id}/vote     → cast vote (PARTNER)
 *   DELETE /api/v1/partners/topics/{id}/vote     → remove vote (PARTNER)
 *   PATCH  /api/v1/partners/topics/{id}/status   → update status (ORGANIZER)
 */
@RestController
@RequestMapping("/api/v1/partners/topics")
@RequiredArgsConstructor
@Slf4j
public class TopicController {

    private final TopicService topicService;
    private final SecurityContextHelper securityContextHelper;

    /**
     * GET /api/v1/partners/topics
     * AC1: list all topics sorted by vote count descending.
     * AC2: currentPartnerHasVoted flag set for PARTNER callers.
     */
    @GetMapping
    @PreAuthorize("hasRole('PARTNER') or hasRole('ORGANIZER')")
    @Timed("partner.topics.list")
    public ResponseEntity<List<TopicDTO>> listTopics() {
        String callerCompanyName = resolveCallerCompanyNameOrNull();
        log.debug("GET /partners/topics caller={}", callerCompanyName);
        return ResponseEntity.ok(topicService.getAllTopics(callerCompanyName));
    }

    /**
     * POST /api/v1/partners/topics
     * AC3: partner suggests a new topic.
     */
    @PostMapping
    @PreAuthorize("hasRole('PARTNER')")
    @Timed("partner.topics.suggest")
    public ResponseEntity<TopicDTO> suggestTopic(@RequestBody TopicSuggestionRequest request) {
        log.info("POST /partners/topics title={}", request.title());
        TopicDTO dto = topicService.suggestTopic(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    /**
     * POST /api/v1/partners/topics/{topicId}/vote
     * AC2: toggle vote on (idempotent).
     */
    @PostMapping("/{topicId}/vote")
    @PreAuthorize("hasRole('PARTNER')")
    @Timed("partner.topics.vote.cast")
    public ResponseEntity<Void> castVote(@PathVariable UUID topicId) {
        String companyName = resolveCallerCompanyName();
        log.debug("POST /partners/topics/{}/vote company={}", topicId, companyName);
        topicService.castVote(topicId, companyName);
        return ResponseEntity.noContent().build();
    }

    /**
     * DELETE /api/v1/partners/topics/{topicId}/vote
     * AC2: toggle vote off (idempotent).
     */
    @DeleteMapping("/{topicId}/vote")
    @PreAuthorize("hasRole('PARTNER')")
    @Timed("partner.topics.vote.remove")
    public ResponseEntity<Void> removeVote(@PathVariable UUID topicId) {
        String companyName = resolveCallerCompanyName();
        log.debug("DELETE /partners/topics/{}/vote company={}", topicId, companyName);
        topicService.removeVote(topicId, companyName);
        return ResponseEntity.noContent().build();
    }

    /**
     * PATCH /api/v1/partners/topics/{topicId}/status
     * AC4: organizer sets topic status (SELECTED or DECLINED).
     */
    @PatchMapping("/{topicId}/status")
    @PreAuthorize("hasRole('ORGANIZER')")
    @Timed("partner.topics.status.update")
    public ResponseEntity<TopicDTO> updateStatus(
            @PathVariable UUID topicId,
            @RequestBody TopicStatusUpdateRequest request) {
        log.info("PATCH /partners/topics/{}/status status={}", topicId, request.status());
        TopicDTO dto = topicService.updateStatus(topicId, request);
        return ResponseEntity.ok(dto);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    /** Returns the caller's company name, or null if they have no partner company (organizer). */
    private String resolveCallerCompanyNameOrNull() {
        return topicService.resolveCallerCompanyNameOrNull();
    }

    /** Returns the caller's company name; throws AccessDeniedException if not found. */
    private String resolveCallerCompanyName() {
        String username = securityContextHelper.getCurrentUsername();
        return topicService.resolveCompanyName(username);
    }
}
