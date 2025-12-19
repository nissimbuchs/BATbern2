package ch.batbern.events.service;

import ch.batbern.events.client.UserApiClient;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Session;
import ch.batbern.events.domain.SessionUser;
import ch.batbern.events.domain.SessionUser.SpeakerRole;
import ch.batbern.events.dto.BatchImportSessionRequest;
import ch.batbern.events.dto.BatchImportSessionResult;
import ch.batbern.events.dto.SessionImportDetail;
import ch.batbern.events.dto.generated.users.UserResponse;
import ch.batbern.events.exception.EventNotFoundException;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.SessionRepository;
import ch.batbern.events.repository.SessionUserRepository;
import ch.batbern.shared.service.SlugGenerationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for batch importing sessions from legacy JSON data (sessions.json)
 *
 * Handles:
 * - Duplicate detection by (event_id, title)
 * - Sequential 45-minute time slot calculation
 * - Speaker assignment by matching speakerId to username
 * - Graceful handling of missing speakers
 * - Event organizer assignment as moderator when no speakers
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SessionBatchImportService {

    private final EventRepository eventRepository;
    private final SessionRepository sessionRepository;
    private final SessionUserRepository sessionUserRepository;
    private final UserApiClient userApiClient;
    private final SlugGenerationService slugGenerationService;

    private static final Duration SESSION_DURATION = Duration.ofMinutes(45);
    private static final String DEFAULT_SESSION_TYPE = "presentation";
    private static final String DEFAULT_LANGUAGE = "de";

    /**
     * Import a batch of sessions from legacy JSON
     *
     * @param eventCode Event code (e.g., "BATbern142")
     * @param requests List of session import requests
     * @return BatchImportSessionResult with statistics and details
     */
    @Transactional
    public BatchImportSessionResult importSessions(String eventCode, List<BatchImportSessionRequest> requests) {
        log.info("Starting batch import of {} sessions for event {}", requests.size(), eventCode);

        // Find the event
        Event event = eventRepository.findByEventCode(eventCode)
                .orElseThrow(() -> new EventNotFoundException("Event not found with code: " + eventCode));

        List<SessionImportDetail> details = new ArrayList<>();
        int successCount = 0;
        int skippedCount = 0;
        int failedCount = 0;

        // Process each session
        for (int i = 0; i < requests.size(); i++) {
            BatchImportSessionRequest request = requests.get(i);
            try {
                SessionImportDetail detail = importSingleSession(event, request, i);
                details.add(detail);

                // Update counters
                switch (detail.getStatus()) {
                    case "success":
                        successCount++;
                        break;
                    case "skipped":
                        skippedCount++;
                        break;
                    case "failed":
                        failedCount++;
                        break;
                    default:
                        // Unexpected status - treat as failed
                        failedCount++;
                        break;
                }
            } catch (Exception e) {
                log.error("Error importing session '{}': {}", request.getTitle(), e.getMessage(), e);
                details.add(SessionImportDetail.failed(request.getTitle(), e.getMessage()));
                failedCount++;
            }
        }

        log.info("Batch import completed: {} success, {} skipped, {} failed",
                successCount, skippedCount, failedCount);

        return BatchImportSessionResult.builder()
                .totalProcessed(requests.size())
                .successfullyCreated(successCount)
                .skipped(skippedCount)
                .failed(failedCount)
                .details(details)
                .build();
    }

    /**
     * Import a single session
     *
     * @param event Event entity
     * @param request Session import request
     * @param sequenceIndex Index in the batch (for time slot calculation)
     * @return SessionImportDetail with result
     */
    private SessionImportDetail importSingleSession(
            Event event,
            BatchImportSessionRequest request,
            int sequenceIndex
    ) {
        String title = request.getTitle();

        // Check for duplicate (same event + title)
        Optional<Session> existingSession = sessionRepository.findByEventIdAndTitle(event.getId(), title);
        if (existingSession.isPresent()) {
            log.info("Skipping duplicate session: {}", title);
            return SessionImportDetail.skipped(title, "Session already exists with this title");
        }

        // Build description (abstract + PDF reference)
        String description = buildDescription(request.getSessionAbstract(), request.getPdf());

        // Calculate sequential time slots
        Instant startTime = calculateStartTime(event, sequenceIndex);
        Instant endTime = startTime.plus(SESSION_DURATION);

        // Generate unique session slug
        String baseSlug = slugGenerationService.generateSessionSlug(title);
        String sessionSlug = slugGenerationService.ensureUniqueSlug(
                baseSlug,
                sessionRepository::existsBySessionSlug
        );

        // Create session entity
        Session session = Session.builder()
                .eventId(event.getId())
                .sessionSlug(sessionSlug)
                .title(title)
                .description(description)
                .sessionType(DEFAULT_SESSION_TYPE)
                .startTime(startTime)
                .endTime(endTime)
                .language(DEFAULT_LANGUAGE)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        session = sessionRepository.save(session);
        log.info("Created session: {} (slug: {})", title, sessionSlug);

        // Assign speakers
        assignSpeakers(session, request.getReferenten(), event.getOrganizerUsername());

        return SessionImportDetail.success(title, sessionSlug);
    }

    /**
     * Build session description from abstract and PDF filename
     */
    private String buildDescription(String abstractText, String pdf) {
        StringBuilder description = new StringBuilder();

        if (abstractText != null && !abstractText.isEmpty()) {
            description.append(abstractText);
        }

        if (pdf != null && !pdf.isEmpty()) {
            if (description.length() > 0) {
                description.append("\n\n");
            }
            description.append("PDF: ").append(pdf);
        }

        return description.toString();
    }

    /**
     * Calculate start time for a session based on event date and sequence index
     *
     * Logic:
     * - Session 0: eventDate @ eventStartTime
     * - Session 1: eventStartTime + 45min
     * - Session 2: eventStartTime + 90min
     * - etc.
     *
     * If event has no startTime (date field only), use 09:00 AM
     */
    private Instant calculateStartTime(Event event, int sequenceIndex) {
        Instant eventDate = event.getDate();

        if (eventDate == null) {
            log.warn("Event {} has no date, using current time", event.getEventCode());
            return Instant.now().plus(SESSION_DURATION.multipliedBy(sequenceIndex));
        }

        // Calculate offset from event start time
        Duration offset = SESSION_DURATION.multipliedBy(sequenceIndex);
        return eventDate.plus(offset);
    }

    /**
     * Assign speakers to a session
     *
     * Logic:
     * - If referenten is not empty: Assign each speaker (first = primary_speaker, others = co_speaker)
     * - If referenten is empty: Assign event organizer as moderator
     * - If speaker not found: Log warning and skip (graceful degradation)
     */
    private void assignSpeakers(
            Session session,
            List<BatchImportSessionRequest.LegacySpeaker> referenten,
            String eventOrganizerUsername
    ) {
        if (referenten == null || referenten.isEmpty()) {
            // No speakers provided - assign event organizer as moderator
            log.info("No speakers for session '{}', assigning organizer {} as moderator",
                    session.getTitle(), eventOrganizerUsername);
            assignSpeaker(session, eventOrganizerUsername, SpeakerRole.MODERATOR);
            return;
        }

        // Assign each speaker
        for (int i = 0; i < referenten.size(); i++) {
            BatchImportSessionRequest.LegacySpeaker legacySpeaker = referenten.get(i);
            String speakerId = legacySpeaker.getSpeakerId();

            if (speakerId == null || speakerId.isEmpty()) {
                log.warn("Speaker has no speakerId, skipping: {}", legacySpeaker.getName());
                continue;
            }

            // First speaker is primary, others are co-speakers
            SpeakerRole role = (i == 0) ? SpeakerRole.PRIMARY_SPEAKER : SpeakerRole.CO_SPEAKER;

            // Map speakerId to username and assign
            assignSpeaker(session, speakerId, role);
        }
    }

    /**
     * Assign a single speaker to a session
     *
     * @param session Session entity
     * @param username Username (mapped from speakerId)
     * @param role Speaker role
     */
    private void assignSpeaker(Session session, String username, SpeakerRole role) {
        try {
            // Verify user exists via API
            UserResponse user = userApiClient.getUserByUsername(username);

            if (user == null) {
                log.warn("User not found for username '{}', skipping speaker assignment", username);
                return;
            }

            // Check if already assigned (shouldn't happen in batch import, but be safe)
            if (sessionUserRepository.existsBySessionIdAndUsername(session.getId(), username)) {
                log.warn("User {} already assigned to session {}, skipping", username, session.getId());
                return;
            }

            // Create placeholder UUID for userId (backward compat field)
            UUID userId = UUID.nameUUIDFromBytes(("user:" + username).getBytes());

            // Create SessionUser entity
            SessionUser sessionUser = SessionUser.builder()
                    .session(session)
                    .userId(userId)
                    .username(username)
                    .speakerRole(role)
                    .isConfirmed(false)
                    .invitedAt(Instant.now())
                    .build();

            sessionUserRepository.save(sessionUser);
            log.info("Assigned speaker {} to session '{}' with role {}",
                    username, session.getTitle(), role);

        } catch (Exception e) {
            // Graceful degradation - log warning but don't fail the import
            log.warn("Failed to assign speaker '{}' to session '{}': {}",
                    username, session.getTitle(), e.getMessage());
        }
    }
}
