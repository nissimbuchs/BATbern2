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
import ch.batbern.events.repository.SessionMaterialsRepository;
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
    private final SessionMaterialsRepository sessionMaterialsRepository;
    private final UserApiClient userApiClient;
    private final SlugGenerationService slugGenerationService;
    private final SessionMaterialsService sessionMaterialsService;

    private static final Duration SESSION_DURATION = Duration.ofMinutes(45);
    private static final Duration NO_SPEAKER_SESSION_DURATION = Duration.ofMinutes(10);
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
        int updatedCount = 0;
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
                    case "updated":
                        updatedCount++;
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

        log.info("Batch import completed: {} success, {} updated, {} skipped, {} failed",
                successCount, updatedCount, skippedCount, failedCount);

        return BatchImportSessionResult.builder()
                .totalProcessed(requests.size())
                .successfullyCreated(successCount)
                .updated(updatedCount)
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
        String materialUrl = request.getMaterialUrl();
        String pdfFilename = request.getPdf(); // Legacy field for backward compatibility

        // Check for existing session (same event + title)
        Optional<Session> existingSession = sessionRepository.findByEventIdAndTitle(event.getId(), title);
        if (existingSession.isPresent()) {
            // Session exists - check if we need to add material
            Session session = existingSession.get();

            // Clean up description (remove legacy PDF references)
            String cleanedDescription = removePdfReferences(session.getDescription());
            if (!cleanedDescription.equals(session.getDescription())) {
                session.setDescription(cleanedDescription);
                session.setUpdatedAt(Instant.now());
                sessionRepository.save(session);
                log.info("Cleaned up PDF references from description for session: {}", title);
            }

            // Upload material from URL if provided
            if (materialUrl != null && !materialUrl.isEmpty()) {
                try {
                    // Extract filename from URL or use PDF filename
                    String filename = pdfFilename != null && !pdfFilename.isEmpty()
                            ? pdfFilename
                            : extractFilenameFromUrl(materialUrl);

                    // Skip placeholder filenames (n/a, N/A, empty, etc.)
                    if (!isValidMaterialFilename(filename)) {
                        log.info("Skipping session with placeholder material filename: {} ({})", title, filename);
                        return SessionImportDetail.skipped(title, "No valid material file");
                    }

                    // Check if material already exists (duplicate prevention)
                    if (sessionMaterialsRepository.existsBySession_IdAndFileName(session.getId(), filename)) {
                        log.info("Material already exists for session {}: {}", title, filename);
                        return SessionImportDetail.skipped(
                                title,
                                "Session already has this material: " + filename
                        );
                    }

                    sessionMaterialsService.uploadMaterialFromUrl(
                            session.getSessionSlug(),
                            materialUrl,
                            filename,
                            "DOCUMENT",
                            event.getOrganizerUsername()
                    );

                    log.info("Updated existing session with material from URL: {} ({})", title, filename);
                    return SessionImportDetail.updated(
                            title,
                            session.getSessionSlug(),
                            "Material uploaded from CDN and associated with session"
                    );
                } catch (Exception e) {
                    log.error("Failed to upload material for session {}: {}", title, e.getMessage());
                    return SessionImportDetail.updated(
                            title,
                            session.getSessionSlug(),
                            "Session exists, but material upload failed: " + e.getMessage()
                    );
                }
            }

            // Session exists - skip
            log.info("Skipping duplicate session (no new material): {}", title);
            return SessionImportDetail.skipped(title, "Session already exists");
        }

        // Build description (abstract only, no PDF references)
        String description = buildDescription(request.getSessionAbstract(), null);

        // Check if session has speakers
        boolean hasSpeakers = request.getReferenten() != null && !request.getReferenten().isEmpty();

        // Calculate time slots
        // Sessions without speakers: 10 minutes, first slot (index 0)
        // Sessions with speakers: 45 minutes, sequential slots
        Duration duration = hasSpeakers ? SESSION_DURATION : NO_SPEAKER_SESSION_DURATION;
        int timeSlotIndex = hasSpeakers ? sequenceIndex : 0;
        Instant startTime = calculateStartTime(event, timeSlotIndex, duration);
        Instant endTime = startTime.plus(duration);

        // Generate unique session slug
        String baseSlug = slugGenerationService.generateSessionSlug(title);
        String sessionSlug = slugGenerationService.ensureUniqueSlug(
                baseSlug,
                sessionRepository::existsBySessionSlug
        );

        // Create session entity
        Session session = Session.builder()
                .eventId(event.getId())
                .eventCode(event.getEventCode())
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
        assignSpeakers(session, request.getReferenten(), event.getOrganizerUsername(), event.getDate());

        // Upload material from URL if provided (Story 5.9)
        if (materialUrl != null && !materialUrl.isEmpty()) {
            try {
                // Extract filename from URL or use PDF filename
                String filename = pdfFilename != null && !pdfFilename.isEmpty()
                        ? pdfFilename
                        : extractFilenameFromUrl(materialUrl);

                // Skip placeholder filenames (n/a, N/A, empty, etc.)
                if (!isValidMaterialFilename(filename)) {
                    log.info("Skipping material upload for new session with placeholder filename: {} ({})",
                            title, filename);
                } else if (!sessionMaterialsRepository.existsBySession_IdAndFileName(session.getId(), filename)) {
                    // Check if material already exists (should not happen for new sessions, but safety check)
                    sessionMaterialsService.uploadMaterialFromUrl(
                            sessionSlug,
                            materialUrl,
                            filename,
                            "DOCUMENT",
                            event.getOrganizerUsername()
                    );

                    log.info("Uploaded material from URL for new session: {} ({})", title, filename);
                } else {
                    log.warn("Material already exists for new session {} ({}), skipping upload", title, filename);
                }
            } catch (Exception e) {
                log.error("Failed to upload material for session {}: {}", title, e.getMessage());
                // Don't fail the entire import - material can be uploaded later
            }
        }

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
     * Remove PDF references from session description
     * Cleans up legacy "PDF: filename" references added by previous import logic
     *
     * @param description Current session description
     * @return Cleaned description without PDF references
     */
    private String removePdfReferences(String description) {
        if (description == null || description.isEmpty()) {
            return description;
        }

        // Remove "PDF: filename" patterns (with optional newlines before)
        String cleaned = description.replaceAll("\\n\\n?PDF:\\s*[^\\n]+", "");
        cleaned = cleaned.replaceAll("^PDF:\\s*[^\\n]+\\n\\n?", "");

        return cleaned.trim();
    }

    /**
     * Extract filename from URL
     * Example: https://cdn.staging.batbern.ch/import-data/session-materials/BAT01_RTC.pdf → BAT01_RTC.pdf
     *
     * @param url URL to extract filename from
     * @return Extracted filename
     */
    private String extractFilenameFromUrl(String url) {
        if (url == null || url.isEmpty()) {
            return "material.pdf";
        }

        // Extract last path segment after final slash
        int lastSlash = url.lastIndexOf('/');
        if (lastSlash >= 0 && lastSlash < url.length() - 1) {
            return url.substring(lastSlash + 1);
        }

        return "material.pdf";
    }

    /**
     * Calculate start time for a session based on event date and sequence index
     *
     * Logic:
     * - Session 0: eventDate @ eventStartTime
     * - Session 1: eventStartTime + slotDuration
     * - Session 2: eventStartTime + (slotDuration * 2)
     * - etc.
     *
     * Sessions without speakers always use slot 0 (first slot)
     *
     * If event has no startTime (date field only), use 09:00 AM
     *
     * @param event Event entity
     * @param sequenceIndex Sequence index in the batch
     * @param slotDuration Duration per slot (45min for normal sessions, 10min for no-speaker sessions)
     */
    private Instant calculateStartTime(Event event, int sequenceIndex, Duration slotDuration) {
        Instant eventDate = event.getDate();

        if (eventDate == null) {
            log.warn("Event {} has no date, using current time", event.getEventCode());
            return Instant.now().plus(slotDuration.multipliedBy(sequenceIndex));
        }

        // Calculate offset from event start time
        Duration offset = slotDuration.multipliedBy(sequenceIndex);
        return eventDate.plus(offset);
    }

    /**
     * Assign speakers to a session
     *
     * Logic:
     * - If referenten is not empty: Assign each speaker (first = primary_speaker, others = co_speaker)
     * - If referenten is empty: Assign event organizer as moderator
     * - If speaker not found: Log warning and skip (graceful degradation)
     *
     * @param session Session entity
     * @param referenten List of legacy speakers
     * @param eventOrganizerUsername Event organizer username
     * @param eventDate Event date (used to calculate invitation date)
     */
    private void assignSpeakers(
            Session session,
            List<BatchImportSessionRequest.LegacySpeaker> referenten,
            String eventOrganizerUsername,
            Instant eventDate
    ) {
        if (referenten == null || referenten.isEmpty()) {
            // No speakers provided - assign event organizer as moderator
            log.info("No speakers for session '{}', assigning organizer {} as moderator",
                    session.getTitle(), eventOrganizerUsername);
            assignSpeaker(session, eventOrganizerUsername, SpeakerRole.MODERATOR, eventDate);
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
            assignSpeaker(session, speakerId, role, eventDate);
        }
    }

    /**
     * Assign a single speaker to a session
     *
     * For historical data import:
     * - isConfirmed = true (sessions already happened)
     * - invitedAt = eventDate - 4 weeks (realistic invitation timeline)
     *
     * Re-import behavior (V38 migration compatibility):
     * - If speaker assignment exists with NULL names, backfills speaker_first_name and speaker_last_name
     * - If speaker assignment exists with names populated, skips (no-op)
     * - If speaker assignment doesn't exist, creates new assignment with names
     *
     * @param session Session entity
     * @param username Username (mapped from speakerId)
     * @param role Speaker role
     * @param eventDate Event date (used to calculate invitation date)
     */
    private void assignSpeaker(Session session, String username, SpeakerRole role, Instant eventDate) {
        try {
            // Verify user exists via API
            UserResponse user = userApiClient.getUserByUsername(username);

            if (user == null) {
                log.warn("User not found for username '{}', skipping speaker assignment", username);
                return;
            }

            // Check if already assigned - if so, backfill speaker names if missing
            Optional<SessionUser> existingAssignment =
                sessionUserRepository.findBySessionIdAndUsername(session.getId(), username);

            if (existingAssignment.isPresent()) {
                SessionUser sessionUser = existingAssignment.get();

                // Backfill speaker names if missing (V38 migration compatibility)
                if (sessionUser.getSpeakerFirstName() == null || sessionUser.getSpeakerLastName() == null) {
                    sessionUser.setSpeakerFirstName(user.getFirstName());
                    sessionUser.setSpeakerLastName(user.getLastName());
                    sessionUserRepository.save(sessionUser);
                    log.info("Backfilled speaker names for {} in session '{}': {} {}",
                            username, session.getTitle(), user.getFirstName(), user.getLastName());
                } else {
                    log.debug("Speaker {} already assigned to session '{}' with names populated, skipping",
                            username, session.getTitle());
                }
                return;
            }

            // Calculate invitation date: 4 weeks before event
            Instant invitedAt = eventDate != null
                    ? eventDate.minus(Duration.ofDays(28))
                    : Instant.now().minus(Duration.ofDays(28));

            // Create SessionUser entity
            // For historical data: isConfirmed = true (sessions already happened)
            // Populate speaker name cache fields for full-text search (V38 migration)
            SessionUser sessionUser = SessionUser.builder()
                    .session(session)
                    .username(username)
                    .speakerRole(role)
                    .speakerFirstName(user.getFirstName())  // Cache for full-text search
                    .speakerLastName(user.getLastName())    // Cache for full-text search
                    .isConfirmed(true)
                    .invitedAt(invitedAt)
                    .confirmedAt(invitedAt.plus(Duration.ofDays(3))) // Confirmed 3 days after invitation
                    .build();

            sessionUserRepository.save(sessionUser);
            log.info("Assigned speaker {} to session '{}' with role {} (confirmed: true, invited: {})",
                    username, session.getTitle(), role, invitedAt);

        } catch (Exception e) {
            // Graceful degradation - log warning but don't fail the import
            log.warn("Failed to assign speaker '{}' to session '{}': {}",
                    username, session.getTitle(), e.getMessage());
        }
    }

    /**
     * Check if a material filename is valid (not a placeholder)
     *
     * @param filename Filename to check
     * @return true if valid, false if placeholder (n/a, empty, etc.)
     */
    private boolean isValidMaterialFilename(String filename) {
        if (filename == null || filename.trim().isEmpty()) {
            return false;
        }

        String normalized = filename.trim().toLowerCase();

        // Common placeholder values
        return !normalized.equals("n/a")
                && !normalized.equals("na")
                && !normalized.equals("none")
                && !normalized.equals("null")
                && !normalized.equals("-");
    }
}
