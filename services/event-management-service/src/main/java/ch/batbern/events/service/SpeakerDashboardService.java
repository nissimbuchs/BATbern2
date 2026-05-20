package ch.batbern.events.service;

import ch.batbern.events.client.UserApiClient;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Session;
import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.events.dto.DashboardPastEventDto;
import ch.batbern.events.dto.DashboardUpcomingEventDto;
import ch.batbern.events.dto.SpeakerDashboardDto;
import ch.batbern.events.dto.generated.users.UserResponse;
import ch.batbern.events.exception.UserNotFoundException;
import ch.batbern.events.repository.SessionContentHistoryRepository;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.SessionMaterialsRepository;
import ch.batbern.events.repository.SessionRepository;
import ch.batbern.events.repository.SpeakerPoolRepository;
import ch.batbern.shared.types.SpeakerWorkflowState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Service for speaker dashboard data aggregation.
 * Story 6.4: Speaker Dashboard (View-Only) - AC1-AC5
 *
 * Aggregates SpeakerPool, Event, Session, and content data
 * to build the speaker dashboard summary view.
 */
@Service
public class SpeakerDashboardService {

    private static final Logger LOG = LoggerFactory.getLogger(SpeakerDashboardService.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final ZoneId SWISS_ZONE = ZoneId.of("Europe/Zurich");

    // Upcoming event states (AC2) — ADR-009 §0.1: post-INVITED states along the content lifecycle.
    private static final Set<SpeakerWorkflowState> UPCOMING_STATES = Set.of(
            SpeakerWorkflowState.INVITED,
            SpeakerWorkflowState.ACCEPTED,
            SpeakerWorkflowState.CONTENT_SUBMITTED,
            SpeakerWorkflowState.QUALITY_REVIEWED
    );

    // Past event states (AC3) — post-ACCEPTED states (the speaker actually committed).
    private static final Set<SpeakerWorkflowState> PAST_STATES = Set.of(
            SpeakerWorkflowState.ACCEPTED,
            SpeakerWorkflowState.CONTENT_SUBMITTED,
            SpeakerWorkflowState.QUALITY_REVIEWED
    );

    // Friendly labels for workflow states (AC2)
    private static final Map<SpeakerWorkflowState, String> WORKFLOW_STATE_LABELS = Map.of(
            SpeakerWorkflowState.INVITED, "Invitation Pending",
            SpeakerWorkflowState.ACCEPTED, "Accepted",
            SpeakerWorkflowState.CONTENT_SUBMITTED, "Content Submitted",
            SpeakerWorkflowState.QUALITY_REVIEWED, "Quality Reviewed"
    );

    // Friendly labels for content status (AC2)
    private static final Map<String, String> CONTENT_STATUS_LABELS = Map.of(
            "PENDING", "Not Submitted",
            "SUBMITTED", "Under Review",
            "APPROVED", "Approved",
            "REVISION_NEEDED", "Revision Needed"
    );

    private final SpeakerPoolRepository speakerPoolRepository;
    private final EventRepository eventRepository;
    private final SessionRepository sessionRepository;
    private final SessionContentHistoryRepository sessionContentHistoryRepository;
    private final SessionMaterialsRepository sessionMaterialsRepository;
    private final UserApiClient userApiClient;

    public SpeakerDashboardService(
            SpeakerPoolRepository speakerPoolRepository,
            EventRepository eventRepository,
            SessionRepository sessionRepository,
            SessionContentHistoryRepository sessionContentHistoryRepository,
            SessionMaterialsRepository sessionMaterialsRepository,
            UserApiClient userApiClient) {
        this.speakerPoolRepository = speakerPoolRepository;
        this.eventRepository = eventRepository;
        this.sessionRepository = sessionRepository;
        this.sessionContentHistoryRepository = sessionContentHistoryRepository;
        this.sessionMaterialsRepository = sessionMaterialsRepository;
        this.userApiClient = userApiClient;
    }

    /**
     * Get the speaker dashboard summary.
     * AC2: Upcoming events, AC3: Past events, AC4: Material status, AC5: Organizer contact.
     *
     * <p>Story 11.E.3: the magic-link token bridge is replaced by Cognito Bearer auth on
     * the controller, which passes the authenticated speaker's username here directly. The
     * username is the cross-service join key (ADR-003).
     *
     * @param username the Cognito-authenticated speaker's username
     * @return dashboard summary DTO
     */
    @Transactional(readOnly = true)
    public SpeakerDashboardDto getDashboard(String username) {
        LOG.info("Loading dashboard for speaker: {}", username);

        // Find all speaker pool entries for this speaker
        List<SpeakerPool> allEntries = speakerPoolRepository.findByUsername(username);

        if (allEntries.isEmpty()) {
            LOG.info("No speaker pool entries found for username: {}", username);
            // Code review 2026-05-18 (P23): fall back to the User's display name from CUMS
            // rather than echoing the raw username (e.g. "alice.muller" → "Alice Müller").
            // A speaker with the SPEAKER role but no current pool rows still deserves a
            // proper greeting on the empty-state dashboard.
            String fallbackName = username;
            try {
                UserResponse userProfile = userApiClient.getUserByUsername(username);
                if (userProfile != null
                        && userProfile.getFirstName() != null
                        && userProfile.getLastName() != null) {
                    fallbackName = (userProfile.getFirstName() + " " + userProfile.getLastName())
                            .trim();
                }
            } catch (Exception e) {
                LOG.debug(
                        "Could not resolve display name for empty-pool dashboard: {} ({})",
                        username,
                        e.getMessage());
            }
            return SpeakerDashboardDto.builder()
                    .speakerName(fallbackName)
                    .profileCompleteness(0)
                    .upcomingEvents(List.of())
                    .pastEvents(List.of())
                    .build();
        }

        // Collect all event IDs and fetch events in batch
        Set<UUID> eventIds = allEntries.stream()
                .map(SpeakerPool::getEventId)
                .collect(Collectors.toSet());
        Map<UUID, Event> eventsById = eventRepository.findAllById(eventIds).stream()
                .collect(Collectors.toMap(Event::getId, Function.identity()));

        // Collect session IDs and fetch sessions in batch
        Set<UUID> sessionIds = allEntries.stream()
                .map(SpeakerPool::getSessionId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());
        Map<UUID, Session> sessionsById = sessionRepository.findAllById(sessionIds).stream()
                .collect(Collectors.toMap(Session::getId, Function.identity()));

        Instant now = Instant.now();

        // Build upcoming and past event lists
        List<DashboardUpcomingEventDto> upcomingEvents = new ArrayList<>();
        List<DashboardPastEventDto> pastEvents = new ArrayList<>();

        for (SpeakerPool entry : allEntries) {
            Event event = eventsById.get(entry.getEventId());
            if (event == null) {
                continue;
            }

            Session session = entry.getSessionId() != null ? sessionsById.get(entry.getSessionId()) : null;
            boolean isUpcoming = event.getDate() != null && event.getDate().isAfter(now);

            if (isUpcoming && UPCOMING_STATES.contains(entry.getStatus())) {
                upcomingEvents.add(buildUpcomingEvent(entry, event, session));
            } else if (!isUpcoming && PAST_STATES.contains(entry.getStatus())) {
                pastEvents.add(buildPastEvent(entry, event, session));
            }
        }

        // AC2: Sort upcoming by event date ascending (soonest first)
        upcomingEvents.sort(Comparator.comparing(DashboardUpcomingEventDto::eventDate));

        // AC3: Sort past by event date descending (most recent first)
        pastEvents.sort(Comparator.comparing(DashboardPastEventDto::eventDate).reversed());

        // Story 11.E.8 §2.9 follow-up: greet the speaker by their canonical User profile
        // name (Cognito-provisioned at CONTACTED → READY), not by speaker_pool.speaker_name
        // which is the original brainstorm lead name (often a placeholder like
        // "testreferent1" or a one-line guess from the organizer). speaker_pool.speaker_name
        // remains the audit field for the IDENTIFIED/CONTACTED phase; once a real user is
        // bound at READY, the User profile is the source of truth for who the speaker is.
        // Fall back to speaker_pool.speaker_name if the User profile can't be resolved.
        String poolFallbackName = allEntries.get(0).getSpeakerName();
        String speakerName = poolFallbackName;
        String profilePictureUrl = null;
        int profileCompleteness = 0;
        try {
            UserResponse userProfile = userApiClient.getUserByUsername(username);
            if (userProfile != null) {
                String firstName = userProfile.getFirstName();
                String lastName = userProfile.getLastName();
                if (firstName != null || lastName != null) {
                    String resolvedName = ((firstName != null ? firstName : "") + " "
                            + (lastName != null ? lastName : "")).trim();
                    if (!resolvedName.isBlank()) {
                        speakerName = resolvedName;
                    }
                }
                if (userProfile.getProfilePictureUrl() != null) {
                    profilePictureUrl = userProfile.getProfilePictureUrl().toString();
                }
                profileCompleteness = calculateProfileCompleteness(userProfile);
            }
        } catch (UserNotFoundException e) {
            LOG.debug("User profile not found for {}, using speaker_pool.speakerName fallback",
                    username);
        } catch (Exception e) {
            LOG.warn("Failed to fetch user profile for {}: {}", username, e.getMessage());
        }

        LOG.info("Dashboard loaded for speaker: {} - {} upcoming, {} past events",
                username, upcomingEvents.size(), pastEvents.size());

        return SpeakerDashboardDto.builder()
                .speakerName(speakerName)
                .profilePictureUrl(profilePictureUrl)
                .profileCompleteness(profileCompleteness)
                .upcomingEvents(upcomingEvents)
                .pastEvents(pastEvents)
                .build();
    }

    private DashboardUpcomingEventDto buildUpcomingEvent(SpeakerPool entry, Event event, Session session) {
        String eventDate = formatEventDate(event.getDate());
        String sessionTitle = session != null ? session.getTitle() : null;

        // AC4: Content status. Story 11.E.8 §2.9: sessions.title is the canonical "current"
        // for every surface (speaker dashboard, organizer kanban, public archive, speaker
        // portal form). The latest session_content_history row is consulted ONLY to flag
        // whether the speaker has actually submitted content (vs. the placeholder title
        // written at CONTACTED → READY) and to surface reviewer feedback. Do NOT override
        // sessionTitle with the history row — that re-introduces the divergence Story
        // 11.E.8 §2.9 closed (organizer edits would stop propagating to the dashboard).
        boolean hasTitle = false;
        boolean hasAbstract = false;
        if (entry.getSessionId() != null) {
            var latestSubmissionAtSession = sessionContentHistoryRepository
                    .findFirstBySessionIdOrderBySubmissionVersionDesc(entry.getSessionId());
            if (latestSubmissionAtSession.isPresent()) {
                hasTitle = latestSubmissionAtSession.get().getTitle() != null
                        && !latestSubmissionAtSession.get().getTitle().isBlank();
                hasAbstract = latestSubmissionAtSession.get().getContentAbstract() != null
                        && !latestSubmissionAtSession.get().getContentAbstract().isBlank();
            }
        }

        // AC4: Material status
        boolean hasMaterial = false;
        String materialFileName = null;
        if (entry.getSessionId() != null) {
            hasMaterial = sessionMaterialsRepository
                    .existsBySession_IdAndMaterialType(entry.getSessionId(), "PRESENTATION");
            if (hasMaterial) {
                var materials = sessionMaterialsRepository.findBySession_Id(entry.getSessionId());
                materialFileName = materials.stream()
                        .filter(m -> "PRESENTATION".equals(m.getMaterialType()))
                        .findFirst()
                        .map(m -> m.getFileName())
                        .orElse(null);
            }
        }

        // AC4: Reviewer feedback (if REVISION_NEEDED). Story 11.E.8: derive contentStatus
        // from the latest SessionContentVersion's reviewer_feedback at read time.
        var latestVersion = entry.getSessionId() != null
                ? sessionContentHistoryRepository
                        .findFirstBySessionIdOrderBySubmissionVersionDesc(entry.getSessionId())
                : java.util.Optional.<ch.batbern.events.domain.SessionContentVersion>empty();
        String derivedContentStatus = ContentStatusDeriver.derive(entry.getStatus(), latestVersion);
        String reviewerFeedback = null;
        if ("REVISION_NEEDED".equals(derivedContentStatus) && latestVersion.isPresent()) {
            reviewerFeedback = latestVersion.get().getReviewerFeedback();
        }

        // AC5: Organizer contact
        String organizerName = null;
        String organizerEmail = null;
        if (event.getOrganizerUsername() != null) {
            try {
                UserResponse organizer = userApiClient.getUserByUsername(event.getOrganizerUsername());
                if (organizer != null) {
                    organizerName = organizer.getFirstName() + " " + organizer.getLastName();
                    organizerEmail = organizer.getEmail();
                }
            } catch (Exception e) {
                LOG.debug("Failed to fetch organizer info for {}: {}", event.getOrganizerUsername(), e.getMessage());
            }
        }

        // Format deadlines
        String responseDeadline = entry.getResponseDeadline() != null
                ? entry.getResponseDeadline().toString() : null;
        String contentDeadline = entry.getContentDeadline() != null
                ? entry.getContentDeadline().toString() : null;

        // Quick-action URLs (AC2)
        String respondUrl = entry.getStatus() == SpeakerWorkflowState.INVITED
                ? "/speaker-portal/respond" : null;
        // Show content submit for any speaker who has accepted or beyond
        boolean canSubmitContent = entry.getStatus() != SpeakerWorkflowState.INVITED
                && entry.getStatus() != SpeakerWorkflowState.IDENTIFIED
                && entry.getStatus() != SpeakerWorkflowState.CONTACTED
                && entry.getStatus() != SpeakerWorkflowState.READY
                && entry.getStatus() != SpeakerWorkflowState.DECLINED;
        String contentUrl = canSubmitContent ? "/speaker-portal/content" : null;

        return DashboardUpcomingEventDto.builder()
                .eventCode(event.getEventCode())
                .eventTitle(event.getTitle())
                .eventDate(eventDate)
                .eventLocation(event.getVenueName())
                .sessionTitle(sessionTitle)
                .workflowState(entry.getStatus().name())
                .workflowStateLabel(WORKFLOW_STATE_LABELS.getOrDefault(entry.getStatus(), entry.getStatus().name()))
                .contentStatus(derivedContentStatus)
                .contentStatusLabel(CONTENT_STATUS_LABELS.getOrDefault(
                        derivedContentStatus, derivedContentStatus))
                .hasTitle(hasTitle)
                .hasAbstract(hasAbstract)
                .hasMaterial(hasMaterial)
                .materialFileName(materialFileName)
                .responseDeadline(responseDeadline)
                .contentDeadline(contentDeadline)
                .reviewerFeedback(reviewerFeedback)
                .organizerName(organizerName)
                .organizerEmail(organizerEmail)
                .respondUrl(respondUrl)
                .contentUrl(contentUrl)
                .build();
    }

    private DashboardPastEventDto buildPastEvent(SpeakerPool entry, Event event, Session session) {
        String eventDate = formatEventDate(event.getDate());
        String sessionTitle = session != null ? session.getTitle() : null;

        // Material status for past events
        boolean hasMaterial = false;
        String materialFileName = null;
        if (entry.getSessionId() != null) {
            hasMaterial = sessionMaterialsRepository
                    .existsBySession_IdAndMaterialType(entry.getSessionId(), "PRESENTATION");
            if (hasMaterial) {
                var materials = sessionMaterialsRepository.findBySession_Id(entry.getSessionId());
                materialFileName = materials.stream()
                        .filter(m -> "PRESENTATION".equals(m.getMaterialType()))
                        .findFirst()
                        .map(m -> m.getFileName())
                        .orElse(null);
            }
        }

        return DashboardPastEventDto.builder()
                .eventCode(event.getEventCode())
                .eventTitle(event.getTitle())
                .eventDate(eventDate)
                .sessionTitle(sessionTitle)
                .hasMaterial(hasMaterial)
                .materialFileName(materialFileName)
                .build();
    }

    private String formatEventDate(Instant date) {
        if (date == null) {
            return null;
        }
        return date.atZone(SWISS_ZONE).format(DATE_FORMATTER);
    }

    private int calculateProfileCompleteness(UserResponse user) {
        int total = 0;
        int filled = 0;

        // Check key profile fields
        total++;
        if (user.getFirstName() != null && !user.getFirstName().isBlank()) {
            filled++;
        }
        total++;
        if (user.getLastName() != null && !user.getLastName().isBlank()) {
            filled++;
        }
        total++;
        if (user.getEmail() != null && !user.getEmail().isBlank()) {
            filled++;
        }
        total++;
        if (user.getBio() != null && !user.getBio().isBlank()) {
            filled++;
        }
        total++;
        if (user.getProfilePictureUrl() != null) {
            filled++;
        }

        return total > 0 ? (filled * 100 / total) : 0;
    }
}
