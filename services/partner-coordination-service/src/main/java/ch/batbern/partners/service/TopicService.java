package ch.batbern.partners.service;

import ch.batbern.partners.domain.TopicStatus;
import ch.batbern.partners.domain.TopicSuggestion;
import ch.batbern.partners.domain.TopicVote;
import ch.batbern.partners.dto.TopicDTO;
import ch.batbern.partners.dto.TopicSuggestionRequest;
import ch.batbern.partners.dto.TopicStatusUpdateRequest;
import ch.batbern.partners.repository.PartnerContactRepository;
import ch.batbern.partners.repository.TopicRepository;
import ch.batbern.partners.repository.TopicVoteRepository;
import ch.batbern.partners.security.SecurityContextHelper;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Business logic for partner topic suggestions and voting — Story 8.2.
 *
 * Design notes:
 * - No EventBridge events (removed from scope: AC table in story).
 * - companyName extracted from security context via PartnerContactRepository lookup.
 * - Vote toggle: idempotent castVote / removeVote (no exception on duplicate / missing).
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class TopicService {

    private final TopicRepository topicRepository;
    private final TopicVoteRepository topicVoteRepository;
    private final PartnerContactRepository partnerContactRepository;
    private final SecurityContextHelper securityContextHelper;

    /**
     * List all topics with vote counts sorted by vote count descending.
     * currentPartnerHasVoted is derived from the caller's company.
     *
     * @param callerCompanyName company name of the authenticated caller; null for organisers
     */
    @Transactional(readOnly = true)
    public List<TopicDTO> getAllTopics(String callerCompanyName) {
        String effectiveCaller = callerCompanyName != null ? callerCompanyName : "__NO_COMPANY__";
        List<Object[]> rows = topicRepository.findAllWithVoteCounts(effectiveCaller);
        return rows.stream().map(row -> toDTO((TopicSuggestion) row[0], (Long) row[1], (Long) row[2])).toList();
    }

    /**
     * Submit a new topic suggestion.
     * companyName and suggestedBy are resolved from the JWT principal.
     */
    public TopicDTO suggestTopic(TopicSuggestionRequest request) {
        validate(request);
        String username = securityContextHelper.getCurrentUsername();
        String companyName = resolveCompanyName(username);

        TopicSuggestion suggestion = TopicSuggestion.builder()
                .companyName(companyName)
                .suggestedBy(username)
                .title(request.title().strip())
                .description(request.description())
                .status(TopicStatus.PROPOSED)
                .build();

        TopicSuggestion saved = topicRepository.save(suggestion);
        log.info("Topic suggested: id={} company={} title={}", saved.getId(), companyName, request.title());
        return toDTO(saved, 0L, 0L);
    }

    /**
     * Toggle a vote on (idempotent — duplicate vote is silently ignored).
     */
    public void castVote(UUID topicId, String callerCompanyName) {
        if (!topicRepository.existsById(topicId)) {
            throw new EntityNotFoundException("Topic not found: " + topicId);
        }
        if (!topicVoteRepository.existsByTopicIdAndCompanyName(topicId, callerCompanyName)) {
            TopicVote vote = TopicVote.builder()
                    .topicId(topicId)
                    .companyName(callerCompanyName)
                    .votedAt(Instant.now())
                    .build();
            topicVoteRepository.save(vote);
            log.debug("Vote cast: topic={} company={}", topicId, callerCompanyName);
        }
    }

    /**
     * Toggle a vote off (idempotent — missing vote is silently ignored).
     */
    public void removeVote(UUID topicId, String callerCompanyName) {
        if (!topicRepository.existsById(topicId)) {
            throw new EntityNotFoundException("Topic not found: " + topicId);
        }
        topicVoteRepository.deleteByTopicIdAndCompanyName(topicId, callerCompanyName);
        log.debug("Vote removed: topic={} company={}", topicId, callerCompanyName);
    }

    /**
     * Update topic status (organizer only, enforced by @PreAuthorize on controller).
     */
    public TopicDTO updateStatus(UUID topicId, TopicStatusUpdateRequest request) {
        if (request.status() == null || request.status().isBlank()) {
            throw new IllegalArgumentException("Status is required and must be SELECTED or DECLINED.");
        }

        TopicSuggestion topic = topicRepository.findById(topicId)
                .orElseThrow(() -> new EntityNotFoundException("Topic not found: " + topicId));

        TopicStatus newStatus;
        try {
            newStatus = TopicStatus.valueOf(request.status().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid status: " + request.status()
                    + ". Must be SELECTED or DECLINED.");
        }
        if (newStatus == TopicStatus.PROPOSED) {
            throw new IllegalArgumentException("Cannot set status back to PROPOSED via this endpoint.");
        }

        topic.setStatus(newStatus);
        topic.setPlannedEvent(request.plannedEvent());
        TopicSuggestion saved = topicRepository.save(topic);

        // Targeted count — avoids full-table scan just to get one vote count
        long voteCount = topicVoteRepository.countByTopicId(topicId);

        log.info("Topic status updated: id={} status={} plannedEvent={}", topicId, newStatus, request.plannedEvent());
        return toDTO(saved, voteCount, 0L);
    }

    /** Resolves the current caller's company name, or null if the caller has no partner company (organiser). */
    public String resolveCallerCompanyNameOrNull() {
        try {
            String username = securityContextHelper.getCurrentUsername();
            return partnerContactRepository.findCompanyNameByUsername(username).orElse(null);
        } catch (SecurityException e) {
            return null;
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    /** Resolve the calling partner's company name from their username. */
    public String resolveCompanyName(String username) {
        return partnerContactRepository.findCompanyNameByUsername(username)
                .orElseThrow(() -> new AccessDeniedException(
                        "No partner company found for user: " + username));
    }

    private void validate(TopicSuggestionRequest request) {
        if (request.title() == null || request.title().isBlank()) {
            throw new IllegalArgumentException("Title is required");
        }
        String trimmed = request.title().strip();
        if (trimmed.length() < 5) {
            throw new IllegalArgumentException("Title must be at least 5 characters");
        }
        if (trimmed.length() > 255) {
            throw new IllegalArgumentException("Title must not exceed 255 characters");
        }
        if (request.description() != null && request.description().length() > 500) {
            throw new IllegalArgumentException("Description must not exceed 500 characters");
        }
    }

    private TopicDTO toDTO(TopicSuggestion topic, Long voteCount, Long callerVoteCount) {
        return new TopicDTO(
                topic.getId(),
                topic.getTitle(),
                topic.getDescription(),
                topic.getCompanyName(),
                voteCount != null ? voteCount.intValue() : 0,
                callerVoteCount != null && callerVoteCount > 0,
                topic.getStatus().name(),
                topic.getPlannedEvent(),
                topic.getCreatedAt()
        );
    }
}
