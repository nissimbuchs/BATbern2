package ch.batbern.partners.controller;

import ch.batbern.partners.api.generated.PartnerTopicsApi;
import ch.batbern.partners.dto.generated.TopicDTO;
import ch.batbern.partners.dto.generated.TopicStatusUpdateRequest;
import ch.batbern.partners.dto.generated.TopicSuggestionRequest;
import ch.batbern.partners.security.SecurityContextHelper;
import ch.batbern.partners.service.TopicService;
import io.micrometer.core.annotation.Timed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for Partner Topic Suggestions &amp; Voting — Story 8.2.
 *
 * Implements the generated {@link PartnerTopicsApi} interface from the consolidated
 * partners-api OpenAPI spec (Phase 5). Because the generated method signatures are fixed,
 * the caller's role/company is resolved from the {@link SecurityContextHolder} rather than
 * an injected {@code Authentication} parameter.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
public class TopicController implements PartnerTopicsApi {

    private final TopicService topicService;
    private final SecurityContextHelper securityContextHelper;

    /**
     * AC1: list all topics sorted by vote count descending.
     * AC2: currentPartnerHasVoted flag set for PARTNER callers.
     */
    @Override
    @PreAuthorize("hasRole('PARTNER') or hasRole('ORGANIZER')")
    @Timed("partner.topics.list")
    public ResponseEntity<List<TopicDTO>> listTopics() {
        String callerCompanyName = topicService.resolveCallerCompanyNameOrNull();
        log.debug("GET /partners/topics caller={}", callerCompanyName);
        return ResponseEntity.ok(topicService.getAllTopics(callerCompanyName));
    }

    /**
     * AC3: partner suggests a new topic (company resolved from JWT).
     * Extended: an organizer may submit on behalf of a partner company by supplying
     * {@code companyName} in the request body. The organizer MUST provide {@code companyName};
     * partners must NOT — their company is always resolved from the JWT regardless of input.
     */
    @Override
    @PreAuthorize("hasRole('PARTNER') or hasRole('ORGANIZER')")
    @Timed("partner.topics.suggest")
    public ResponseEntity<TopicDTO> suggestTopic(TopicSuggestionRequest topicSuggestionRequest) {
        boolean isOrg = isOrganizer();
        if (isOrg && (topicSuggestionRequest.getCompanyName() == null
                || topicSuggestionRequest.getCompanyName().isBlank())) {
            log.warn("Organizer attempted to suggest topic without companyName");
            return ResponseEntity.badRequest().build();
        }
        // Partners: onBehalf = null → company resolved from JWT (cannot be overridden)
        String onBehalf = isOrg ? topicSuggestionRequest.getCompanyName() : null;
        log.info("POST /partners/topics title={} onBehalf={}", topicSuggestionRequest.getTitle(), onBehalf);
        TopicDTO dto = topicService.suggestTopic(topicSuggestionRequest, onBehalf);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    /** AC2: toggle vote on (idempotent). */
    @Override
    @PreAuthorize("hasRole('PARTNER')")
    @Timed("partner.topics.vote.cast")
    public ResponseEntity<Void> castVote(UUID topicId) {
        String companyName = resolveCallerCompanyName();
        log.debug("POST /partners/topics/{}/vote company={}", topicId, companyName);
        topicService.castVote(topicId, companyName);
        return ResponseEntity.noContent().build();
    }

    /** AC2: toggle vote off (idempotent). */
    @Override
    @PreAuthorize("hasRole('PARTNER')")
    @Timed("partner.topics.vote.remove")
    public ResponseEntity<Void> removeVote(UUID topicId) {
        String companyName = resolveCallerCompanyName();
        log.debug("DELETE /partners/topics/{}/vote company={}", topicId, companyName);
        topicService.removeVote(topicId, companyName);
        return ResponseEntity.noContent().build();
    }

    /** Edit topic title/description — only the submitting company (or an organizer) may edit. */
    @Override
    @PreAuthorize("hasRole('PARTNER') or hasRole('ORGANIZER')")
    @Timed("partner.topics.update")
    public ResponseEntity<TopicDTO> updateTopic(UUID topicId, TopicSuggestionRequest topicSuggestionRequest) {
        String companyName = isOrganizer() ? null : resolveCallerCompanyName();
        log.info("PATCH /partners/topics/{} company={}", topicId, companyName);
        TopicDTO dto = topicService.updateTopic(topicId, topicSuggestionRequest, companyName);
        return ResponseEntity.ok(dto);
    }

    /** Delete topic — partner may only delete own company's; organizer may delete any. */
    @Override
    @PreAuthorize("hasRole('PARTNER') or hasRole('ORGANIZER')")
    @Timed("partner.topics.delete")
    public ResponseEntity<Void> deleteTopic(UUID topicId) {
        String companyName = isOrganizer() ? null : resolveCallerCompanyName();
        log.info("DELETE /partners/topics/{} company={}", topicId, companyName);
        topicService.deleteTopic(topicId, companyName);
        return ResponseEntity.noContent().build();
    }

    /** AC4: organizer sets topic status (SELECTED or DECLINED). */
    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    @Timed("partner.topics.status.update")
    public ResponseEntity<TopicDTO> updateTopicStatus(UUID topicId, TopicStatusUpdateRequest topicStatusUpdateRequest) {
        log.info("PATCH /partners/topics/{}/status status={}", topicId, topicStatusUpdateRequest.getStatus());
        TopicDTO dto = topicService.updateStatus(topicId, topicStatusUpdateRequest);
        return ResponseEntity.ok(dto);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    /** Returns true if the authenticated caller holds the ORGANIZER role. */
    private boolean isOrganizer() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ORGANIZER"));
    }

    /** Returns the caller's company name; throws AccessDeniedException if not found. */
    private String resolveCallerCompanyName() {
        String username = securityContextHelper.getCurrentUsername();
        return topicService.resolveCompanyName(username);
    }
}
