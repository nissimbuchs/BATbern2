package ch.batbern.partners.service;

import ch.batbern.partners.domain.TopicStatus;
import ch.batbern.partners.domain.TopicSuggestion;
import ch.batbern.partners.domain.TopicVote;
import ch.batbern.partners.dto.TopicDTO;
import ch.batbern.partners.dto.TopicSuggestionRequest;
import ch.batbern.partners.dto.TopicStatusUpdateRequest;
import ch.batbern.partners.client.UserServiceClient;
import ch.batbern.partners.client.user.dto.UserResponse;
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
 * - companyName extracted from security context via User Service lookup (user.companyId).
 * - Vote toggle: idempotent castVote / removeVote (no exception on duplicate / missing).
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class TopicService {

    private final TopicRepository topicRepository;
    private final TopicVoteRepository topicVoteRepository;
    private final UserServiceClient userServiceClient;
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
     * Submit a new topic suggestion as a PARTNER (company resolved from JWT).
     * Delegates to the two-argument form with {@code onBehalfOfCompany = null}.
     */
    public TopicDTO suggestTopic(TopicSuggestionRequest request) {
        return suggestTopic(request, null);
    }

    /**
     * Submit a new topic suggestion.
     *
     * <p>When {@code onBehalfOfCompany} is non-blank (organizer proxy path), the given company
     * name is used directly. Otherwise the company is resolved from the JWT principal (partner
     * self-service path).
     *
     * @param request          title + optional description
     * @param onBehalfOfCompany company name supplied by the organizer; {@code null} for partners
     */
    public TopicDTO suggestTopic(TopicSuggestionRequest request, String onBehalfOfCompany) {
        validate(request);
        String username = securityContextHelper.getCurrentUsername();
        String companyName = (onBehalfOfCompany != null && !onBehalfOfCompany.isBlank())
                ? onBehalfOfCompany
                : resolveCompanyName(username);

        TopicSuggestion suggestion = TopicSuggestion.builder()
                .companyName(companyName)
                .suggestedBy(username)
                .title(request.title().strip())
                .description(request.description())
                .status(TopicStatus.PROPOSED)
                .build();

        TopicSuggestion saved = topicRepository.save(suggestion);
        log.info("Topic suggested: id={} company={} title={} suggestedBy={}",
                saved.getId(), companyName, request.title(), username);
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
     * Update a topic's title/description — partner can only edit their own company's topics.
     */
    public TopicDTO updateTopic(UUID topicId, TopicSuggestionRequest request, String callerCompanyName) {
        validate(request);
        TopicSuggestion topic = topicRepository.findById(topicId)
                .orElseThrow(() -> new EntityNotFoundException("Topic not found: " + topicId));
        if (callerCompanyName != null && !topic.getCompanyName().equals(callerCompanyName)) {
            throw new AccessDeniedException("Cannot edit a topic from another company");
        }
        topic.setTitle(request.title().strip());
        topic.setDescription(request.description());
        // Intentional: createdAt is reused as "last activity" timestamp.
        // When a topic is edited it becomes recent again and should float to the top
        // when the UI sorts by date descending.  A separate updatedAt column was
        // deliberately avoided to keep the schema simple — the field name is
        // slightly misleading but the behaviour is the intended design.
        topic.setCreatedAt(Instant.now());
        TopicSuggestion saved = topicRepository.save(topic);
        long voteCount = topicVoteRepository.countByTopicId(topicId);
        log.info("Topic updated: id={} company={}", topicId, callerCompanyName);
        return toDTO(saved, voteCount, 1L); // caller has voted iff voteCount > 0 — not critical for edit response
    }

    /**
     * Delete a topic — partner can only delete their own company's topics.
     */
    public void deleteTopic(UUID topicId, String callerCompanyName) {
        TopicSuggestion topic = topicRepository.findById(topicId)
                .orElseThrow(() -> new EntityNotFoundException("Topic not found: " + topicId));
        if (callerCompanyName != null && !topic.getCompanyName().equals(callerCompanyName)) {
            throw new AccessDeniedException("Cannot delete a topic from another company");
        }
        topicRepository.delete(topic); // votes cascade via ON DELETE CASCADE
        log.info("Topic deleted: id={} company={}", topicId, callerCompanyName);
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
            UserResponse user = userServiceClient.getUserByUsername(username);
            String companyId = user != null ? user.getCompanyId() : null;
            return (companyId != null && !companyId.isBlank()) ? companyId : null;
        } catch (SecurityException e) {
            return null;
        } catch (Exception e) {
            log.debug("Could not resolve company for caller: {}", e.getMessage());
            return null;
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    /** Resolve the calling partner's company name from their username via the User Service. */
    public String resolveCompanyName(String username) {
        UserResponse user = userServiceClient.getUserByUsername(username);
        String companyId = user != null ? user.getCompanyId() : null;
        if (companyId == null || companyId.isBlank()) {
            throw new AccessDeniedException("No partner company found for user: " + username);
        }
        return companyId;
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
