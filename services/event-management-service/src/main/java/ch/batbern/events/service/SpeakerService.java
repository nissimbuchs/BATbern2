package ch.batbern.events.service;

import ch.batbern.events.client.UserApiClient;
import ch.batbern.events.domain.Speaker;
import ch.batbern.events.domain.SpeakerAvailability;
import ch.batbern.events.dto.SpeakerRequest;
import ch.batbern.events.dto.SpeakerResponse;
import ch.batbern.events.dto.generated.users.UserResponse;
import ch.batbern.events.event.SpeakerCreatedEvent;
import ch.batbern.events.event.SpeakerUpdatedEvent;
import ch.batbern.events.exception.SpeakerNotFoundException;
import ch.batbern.events.repository.SpeakerRepository;
import ch.batbern.shared.events.DomainEventPublisher;
import ch.batbern.shared.types.SpeakerWorkflowState;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
    private final DomainEventPublisher domainEventPublisher;
    private final ObjectMapper objectMapper;

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

        // AC7: Publish SpeakerCreatedEvent
        domainEventPublisher.publish(new SpeakerCreatedEvent(
                saved.getId(),
                saved.getUsername(),
                saved.getAvailability() != null ? saved.getAvailability().name() : null,
                saved.getWorkflowState() != null ? saved.getWorkflowState().name() : null,
                saved.getExpertiseAreas(),
                saved.getSpeakingTopics(),
                saved.getLanguages(),
                request.getUsername()
        ));

        return enrichWithUserData(saved);
    }

    /**
     * Get speaker by username with user enrichment (AC2).
     *
     * @param username Speaker's username (ADR-003)
     * @return Speaker with enriched user data
     * @throws SpeakerNotFoundException if speaker not found
     */
    @Transactional(readOnly = true)
    public SpeakerResponse getSpeakerByUsername(String username) {
        return getSpeakerByUsername(username, null);
    }

    /**
     * Get speaker by username with user enrichment and optional expansions (AC2, AC3).
     *
     * @param username Speaker's username (ADR-003)
     * @param include Comma-separated list of expansions (speakingHistory,events,sessions)
     * @return Speaker with enriched user data and requested expansions
     * @throws SpeakerNotFoundException if speaker not found
     */
    @Transactional(readOnly = true)
    public SpeakerResponse getSpeakerByUsername(String username, String include) {
        log.debug("Fetching speaker by username: {}, include: {}", username, include);

        Speaker speaker = speakerRepository.findByUsernameAndDeletedAtIsNull(username)
                .orElseThrow(() -> new SpeakerNotFoundException(username));

        Set<String> includes = parseIncludes(include);
        return enrichWithUserData(speaker, includes);
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

        // Track changed fields for AC7 event
        List<String> changedFields = new ArrayList<>();

        // Update only non-null fields
        if (request.getAvailability() != null) {
            changedFields.add("availability");
            speaker.setAvailability(request.getAvailability());
        }
        if (request.getWorkflowState() != null) {
            changedFields.add("workflowState");
            speaker.setWorkflowState(request.getWorkflowState());
        }
        if (request.getExpertiseAreas() != null) {
            changedFields.add("expertiseAreas");
            speaker.setExpertiseAreas(new ArrayList<>(request.getExpertiseAreas()));
        }
        if (request.getSpeakingTopics() != null) {
            changedFields.add("speakingTopics");
            speaker.setSpeakingTopics(new ArrayList<>(request.getSpeakingTopics()));
        }
        if (request.getLinkedInUrl() != null) {
            changedFields.add("linkedInUrl");
            speaker.setLinkedInUrl(request.getLinkedInUrl());
        }
        if (request.getTwitterHandle() != null) {
            changedFields.add("twitterHandle");
            speaker.setTwitterHandle(request.getTwitterHandle());
        }
        if (request.getCertifications() != null) {
            changedFields.add("certifications");
            speaker.setCertifications(new ArrayList<>(request.getCertifications()));
        }
        if (request.getLanguages() != null) {
            changedFields.add("languages");
            speaker.setLanguages(new ArrayList<>(request.getLanguages()));
        }

        Speaker updated = speakerRepository.save(speaker);
        log.info("Updated speaker profile: {}", username);

        // AC7: Publish SpeakerUpdatedEvent
        domainEventPublisher.publish(new SpeakerUpdatedEvent(
                updated.getId(),
                updated.getUsername(),
                updated.getAvailability() != null ? updated.getAvailability().name() : null,
                updated.getWorkflowState() != null ? updated.getWorkflowState().name() : null,
                updated.getExpertiseAreas(),
                updated.getSpeakingTopics(),
                updated.getLanguages(),
                changedFields,
                username
        ));

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
     * List speakers with pagination and optional filtering (AC2).
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
        return listSpeakers(availability, workflowState, null, null, null, pageable);
    }

    /**
     * List speakers with pagination and advanced filtering (AC2, AC4).
     *
     * @param availability Optional availability filter
     * @param workflowState Optional workflow state filter
     * @param expertiseAreas Optional filter - expertise areas must contain this value (AC4)
     * @param languages Optional filter - languages must contain this value (AC4)
     * @param speakingTopics Optional filter - speaking topics must contain this value (AC4)
     * @param pageable Pagination parameters
     * @return Page of speakers with enriched user data
     */
    @Transactional(readOnly = true)
    public Page<SpeakerResponse> listSpeakers(
            SpeakerAvailability availability,
            SpeakerWorkflowState workflowState,
            String expertiseAreas,
            String languages,
            String speakingTopics,
            Pageable pageable
    ) {
        log.debug("Listing speakers with availability={}, workflowState={}, expertiseAreas={}, languages={}, speakingTopics={}",
                availability, workflowState, expertiseAreas, languages, speakingTopics);

        Page<Speaker> speakers;

        // Use native query for array filters (AC4), or Specification for basic filters
        if (hasArrayFilters(expertiseAreas, languages, speakingTopics)) {
            // Convert enum values to lowercase database representation
            String availabilityDb = availability != null ? availability.name().toLowerCase() : null;
            String workflowStateDb = workflowState != null ? workflowState.name().toLowerCase() : null;

            speakers = speakerRepository.findWithAdvancedFilters(
                    availabilityDb, workflowStateDb,
                    expertiseAreas, languages, speakingTopics,
                    pageable);
        } else {
            Specification<Speaker> spec = buildBasicSpecification(availability, workflowState);
            speakers = speakerRepository.findAll(spec, pageable);
        }

        return speakers.map(s -> enrichWithUserData(s, Collections.emptySet()));
    }

    /**
     * Check if any array filters are provided.
     */
    private boolean hasArrayFilters(String expertiseAreas, String languages, String speakingTopics) {
        return (expertiseAreas != null && !expertiseAreas.isBlank())
                || (languages != null && !languages.isBlank())
                || (speakingTopics != null && !speakingTopics.isBlank());
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
     * Build JPA Specification for basic filtering (availability, workflowState).
     * Array filters use native query via findWithAdvancedFilters.
     */
    private Specification<Speaker> buildBasicSpecification(
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
     * Parse comma-separated include parameter into a Set.
     */
    private Set<String> parseIncludes(String include) {
        if (include == null || include.isBlank()) {
            return Collections.emptySet();
        }
        Set<String> includes = new HashSet<>();
        for (String part : include.split(",")) {
            includes.add(part.trim().toLowerCase());
        }
        return includes;
    }

    /**
     * Enrich Speaker entity with User data from User Service (ADR-004).
     * Uses HTTP call to fetch user profile data.
     *
     * @param speaker Speaker entity
     * @return SpeakerResponse with combined data
     */
    private SpeakerResponse enrichWithUserData(Speaker speaker) {
        return enrichWithUserData(speaker, Collections.emptySet());
    }

    /**
     * Enrich Speaker entity with User data and optional expansions (ADR-004, AC3).
     *
     * @param speaker Speaker entity
     * @param includes Set of expansion names (speakingHistory, events, sessions)
     * @return SpeakerResponse with combined data and requested expansions
     */
    private SpeakerResponse enrichWithUserData(Speaker speaker, Set<String> includes) {
        UserResponse user = userApiClient.getUserByUsername(speaker.getUsername());

        SpeakerResponse.SpeakerResponseBuilder builder = SpeakerResponse.builder()
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
                .updatedAt(speaker.getUpdatedAt());

        // AC3: Resource expansion
        if (includes.contains("speakinghistory")) {
            builder.speakingHistory(parseSpeakingHistory(speaker.getSpeakingHistory()));
        }

        if (includes.contains("events")) {
            // For now, return empty list - would need SessionUser query
            builder.events(Collections.emptyList());
        }

        if (includes.contains("sessions")) {
            // For now, return empty list - would need SessionUser query
            builder.sessions(Collections.emptyList());
        }

        return builder.build();
    }

    /**
     * Parse speaking history JSON string to list of entries (AC3).
     */
    private List<SpeakerResponse.SpeakingHistoryEntry> parseSpeakingHistory(String speakingHistoryJson) {
        if (speakingHistoryJson == null || speakingHistoryJson.isBlank() || "[]".equals(speakingHistoryJson.trim())) {
            return Collections.emptyList();
        }

        try {
            return objectMapper.readValue(speakingHistoryJson,
                    new TypeReference<List<SpeakerResponse.SpeakingHistoryEntry>>() {});
        } catch (Exception e) {
            log.warn("Failed to parse speaking history JSON: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
}
