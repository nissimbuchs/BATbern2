package ch.batbern.events.service;

import ch.batbern.events.client.UserApiClient;
import ch.batbern.events.domain.Speaker;
import ch.batbern.events.domain.SpeakerAvailability;
import ch.batbern.events.dto.SpeakerRequest;
import ch.batbern.events.dto.SpeakerResponse;
import ch.batbern.events.dto.generated.users.UserResponse;
import ch.batbern.events.exception.SpeakerNotFoundException;
import ch.batbern.events.repository.SpeakerRepository;
import ch.batbern.shared.types.SpeakerWorkflowState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Service for managing global Speaker profiles - Story 6.0.
 *
 * Provides CRUD operations with ADR-003/ADR-004 compliance:
 * - Username-based identification (not UUID)
 * - User data enrichment via HTTP (not database join)
 * - Soft delete support
 *
 * @see ch.batbern.events.service.SpeakerPoolService for event-specific speaker tracking
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class SpeakerService {

    private final SpeakerRepository speakerRepository;
    private final UserApiClient userApiClient;

    /**
     * Create a new speaker profile.
     *
     * @param request Speaker creation request
     * @return Created speaker with enriched user data
     * @throws IllegalArgumentException if speaker already exists
     */
    public SpeakerResponse createSpeaker(SpeakerRequest request) {
        log.info("Creating speaker profile for username: {}", request.getUsername());

        // Validate user exists via API
        userApiClient.getUserByUsername(request.getUsername());

        // Check for duplicate
        if (speakerRepository.existsByUsername(request.getUsername())) {
            throw new IllegalArgumentException("Speaker profile already exists for username: " + request.getUsername());
        }

        Speaker speaker = Speaker.builder()
                .username(request.getUsername())
                .availability(request.getAvailability() != null ? request.getAvailability() : SpeakerAvailability.AVAILABLE)
                .workflowState(request.getWorkflowState() != null ? request.getWorkflowState() : SpeakerWorkflowState.IDENTIFIED)
                .expertiseAreas(request.getExpertiseAreas() != null ? new ArrayList<>(request.getExpertiseAreas()) : new ArrayList<>())
                .speakingTopics(request.getSpeakingTopics() != null ? new ArrayList<>(request.getSpeakingTopics()) : new ArrayList<>())
                .linkedInUrl(request.getLinkedInUrl())
                .twitterHandle(request.getTwitterHandle())
                .certifications(request.getCertifications() != null ? new ArrayList<>(request.getCertifications()) : new ArrayList<>())
                .languages(request.getLanguages() != null ? new ArrayList<>(request.getLanguages()) : new ArrayList<>(List.of("de", "en")))
                .build();

        Speaker saved = speakerRepository.save(speaker);
        log.info("Created speaker profile with ID: {}", saved.getId());

        return enrichWithUserData(saved);
    }

    /**
     * Get speaker by username with user enrichment.
     *
     * @param username Speaker's username (ADR-003)
     * @return Speaker with enriched user data
     * @throws SpeakerNotFoundException if speaker not found
     */
    @Transactional(readOnly = true)
    public SpeakerResponse getSpeakerByUsername(String username) {
        log.debug("Fetching speaker by username: {}", username);

        Speaker speaker = speakerRepository.findByUsernameAndDeletedAtIsNull(username)
                .orElseThrow(() -> new SpeakerNotFoundException(username));

        return enrichWithUserData(speaker);
    }

    /**
     * Get speaker by username without user enrichment.
     * For internal use when user data not needed.
     *
     * @param username Speaker's username
     * @return Speaker entity
     * @throws SpeakerNotFoundException if speaker not found
     */
    @Transactional(readOnly = true)
    public Speaker getSpeakerEntityByUsername(String username) {
        return speakerRepository.findByUsernameAndDeletedAtIsNull(username)
                .orElseThrow(() -> new SpeakerNotFoundException(username));
    }

    /**
     * Update speaker profile.
     *
     * @param username Speaker's username
     * @param request Update request
     * @return Updated speaker with enriched user data
     * @throws SpeakerNotFoundException if speaker not found
     */
    public SpeakerResponse updateSpeaker(String username, SpeakerRequest request) {
        log.info("Updating speaker profile: {}", username);

        Speaker speaker = speakerRepository.findByUsernameAndDeletedAtIsNull(username)
                .orElseThrow(() -> new SpeakerNotFoundException(username));

        // Update only non-null fields
        if (request.getAvailability() != null) {
            speaker.setAvailability(request.getAvailability());
        }
        if (request.getWorkflowState() != null) {
            speaker.setWorkflowState(request.getWorkflowState());
        }
        if (request.getExpertiseAreas() != null) {
            speaker.setExpertiseAreas(new ArrayList<>(request.getExpertiseAreas()));
        }
        if (request.getSpeakingTopics() != null) {
            speaker.setSpeakingTopics(new ArrayList<>(request.getSpeakingTopics()));
        }
        if (request.getLinkedInUrl() != null) {
            speaker.setLinkedInUrl(request.getLinkedInUrl());
        }
        if (request.getTwitterHandle() != null) {
            speaker.setTwitterHandle(request.getTwitterHandle());
        }
        if (request.getCertifications() != null) {
            speaker.setCertifications(new ArrayList<>(request.getCertifications()));
        }
        if (request.getLanguages() != null) {
            speaker.setLanguages(new ArrayList<>(request.getLanguages()));
        }

        Speaker updated = speakerRepository.save(speaker);
        log.info("Updated speaker profile: {}", username);

        return enrichWithUserData(updated);
    }

    /**
     * Soft delete a speaker profile.
     *
     * @param username Speaker's username
     * @throws SpeakerNotFoundException if speaker not found
     */
    public void deleteSpeaker(String username) {
        log.info("Soft deleting speaker profile: {}", username);

        Speaker speaker = speakerRepository.findByUsernameAndDeletedAtIsNull(username)
                .orElseThrow(() -> new SpeakerNotFoundException(username));

        speaker.setDeletedAt(Instant.now());
        speakerRepository.save(speaker);

        log.info("Soft deleted speaker profile: {}", username);
    }

    /**
     * List speakers with pagination and optional filtering.
     *
     * @param availability Optional availability filter
     * @param workflowState Optional workflow state filter
     * @param pageable Pagination parameters
     * @return Page of speakers with enriched user data
     */
    @Transactional(readOnly = true)
    public Page<SpeakerResponse> listSpeakers(
            SpeakerAvailability availability,
            SpeakerWorkflowState workflowState,
            Pageable pageable
    ) {
        log.debug("Listing speakers with availability={}, workflowState={}", availability, workflowState);

        Specification<Speaker> spec = buildSpecification(availability, workflowState);
        Page<Speaker> speakers = speakerRepository.findAll(spec, pageable);

        return speakers.map(this::enrichWithUserData);
    }

    /**
     * Check if speaker profile exists for username.
     *
     * @param username Speaker's username
     * @return true if exists (not soft deleted), false otherwise
     */
    @Transactional(readOnly = true)
    public boolean speakerExists(String username) {
        return speakerRepository.findByUsernameAndDeletedAtIsNull(username).isPresent();
    }

    /**
     * Build JPA Specification for filtering speakers.
     */
    private Specification<Speaker> buildSpecification(
            SpeakerAvailability availability,
            SpeakerWorkflowState workflowState
    ) {
        return (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();

            // Always exclude soft-deleted records
            predicates.add(cb.isNull(root.get("deletedAt")));

            if (availability != null) {
                predicates.add(cb.equal(root.get("availability"), availability));
            }

            if (workflowState != null) {
                predicates.add(cb.equal(root.get("workflowState"), workflowState));
            }

            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }

    /**
     * Enrich Speaker entity with User data from User Service (ADR-004).
     * Uses HTTP call to fetch user profile data.
     *
     * @param speaker Speaker entity
     * @return SpeakerResponse with combined data
     */
    private SpeakerResponse enrichWithUserData(Speaker speaker) {
        UserResponse user = userApiClient.getUserByUsername(speaker.getUsername());

        return SpeakerResponse.builder()
                // User fields (from HTTP enrichment)
                .username(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .bio(user.getBio())
                .profilePictureUrl(user.getProfilePictureUrl() != null ? user.getProfilePictureUrl().toString() : null)
                .company(user.getCompanyId())
                // Speaker domain fields
                .availability(speaker.getAvailability())
                .workflowState(speaker.getWorkflowState())
                .expertiseAreas(speaker.getExpertiseAreas())
                .speakingTopics(speaker.getSpeakingTopics())
                .linkedInUrl(speaker.getLinkedInUrl())
                .twitterHandle(speaker.getTwitterHandle())
                .certifications(speaker.getCertifications())
                .languages(speaker.getLanguages())
                .createdAt(speaker.getCreatedAt())
                .updatedAt(speaker.getUpdatedAt())
                .build();
    }
}
