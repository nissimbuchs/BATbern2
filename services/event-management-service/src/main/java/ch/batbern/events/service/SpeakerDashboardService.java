package ch.batbern.events.service;

import ch.batbern.events.client.UserApiClient;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Session;
import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.events.dto.DashboardPastEventDto;
import ch.batbern.events.dto.DashboardUpcomingEventDto;
import ch.batbern.events.dto.SpeakerDashboardDto;
import ch.batbern.events.dto.TokenValidationResult;
import ch.batbern.events.dto.generated.users.UserResponse;
import ch.batbern.events.exception.UserNotFoundException;
import ch.batbern.events.repository.ContentSubmissionRepository;
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

    // Upcoming event states (AC2)
    private static final Set<SpeakerWorkflowState> UPCOMING_STATES = Set.of(
            SpeakerWorkflowState.INVITED,
            SpeakerWorkflowState.ACCEPTED,
            SpeakerWorkflowState.CONFIRMED,
            SpeakerWorkflowState.CONTENT_SUBMITTED,
            SpeakerWorkflowState.QUALITY_REVIEWED,
            SpeakerWorkflowState.SLOT_ASSIGNED
    );

    // Past event states (AC3)
    private static final Set<SpeakerWorkflowState> PAST_STATES = Set.of(
            SpeakerWorkflowState.ACCEPTED,
            SpeakerWorkflowState.CONFIRMED,
            SpeakerWorkflowState.CONTENT_SUBMITTED,
            SpeakerWorkflowState.QUALITY_REVIEWED,
            SpeakerWorkflowState.SLOT_ASSIGNED
    );

    // Friendly labels for workflow states (AC2)
    private static final Map<SpeakerWorkflowState, String> WORKFLOW_STATE_LABELS = Map.of(
            SpeakerWorkflowState.INVITED, "Invitation Pending",
            SpeakerWorkflowState.ACCEPTED, "Accepted",
            SpeakerWorkflowState.CONFIRMED, "Confirmed"
    );

    // Friendly labels for content status (AC2)
    private static final Map<String, String> CONTENT_STATUS_LABELS = Map.of(
            "PENDING", "Not Submitted",
            "SUBMITTED", "Under Review",
            "APPROVED", "Approved",
            "REVISION_NEEDED", "Revision Needed"
    );

    private final MagicLinkService magicLinkService;
    private final SpeakerPoolRepository speakerPoolRepository;
    private final EventRepository eventRepository;
    private final SessionRepository sessionRepository;
    private final ContentSubmissionRepository contentSubmissionRepository;
    private final SessionMaterialsRepository sessionMaterialsRepository;
    private final UserApiClient userApiClient;

    public SpeakerDashboardService(
            MagicLinkService magicLinkService,
            SpeakerPoolRepository speakerPoolRepository,
            EventRepository eventRepository,
            SessionRepository sessionRepository,
            ContentSubmissionRepository contentSubmissionRepository,
            SessionMaterialsRepository sessionMaterialsRepository,
            UserApiClient userApiClient) {
        this.magicLinkService = magicLinkService;
        this.speakerPoolRepository = speakerPoolRepository;
        this.eventRepository = eventRepository;
        this.sessionRepository = sessionRepository;
        this.contentSubmissionRepository = contentSubmissionRepository;
        this.sessionMaterialsRepository = sessionMaterialsRepository;
        this.userApiClient = userApiClient;
    }

    /**
     * Get the speaker dashboard summary.
     * AC1: Token validation, AC2: Upcoming events, AC3: Past events,
     * AC4: Material status, AC5: Organizer contact
     *
     * @param token the magic link token
     * @return dashboard summary DTO
     * @throws IllegalArgumentException if token is invalid/expired
     */
    @Transactional(readOnly = true)
    public SpeakerDashboardDto getDashboard(String token) {
        // AC1: Validate token (VIEW tokens are reusable)
        TokenValidationResult validation = magicLinkService.validateToken(token);
        if (!validation.valid()) {
            throw new IllegalArgumentException("Token is invalid or expired: " + validation.error());
        }

        String username = validation.username();
        LOG.info("Loading dashboard for speaker: {}", username);

        // Find all speaker pool entries for this speaker
        List<SpeakerPool> allEntries = speakerPoolRepository.findByUsername(username);

        if (allEntries.isEmpty()) {
            LOG.info("No speaker pool entries found for username: {}", username);
            return SpeakerDashboardDto.builder()
                    .speakerName(validation.speakerName())
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

        // Get speaker name from first entry
        String speakerName = allEntries.get(0).getSpeakerName();

        // Try to get profile picture URL from user service
        String profilePictureUrl = null;
        int profileCompleteness = 0;
        try {
            UserResponse userProfile = userApiClient.getUserByUsername(username);
            if (userProfile != null) {
                if (userProfile.getProfilePictureUrl() != null) {
                    profilePictureUrl = userProfile.getProfilePictureUrl().toString();
                }
                profileCompleteness = calculateProfileCompleteness(userProfile);
            }
        } catch (UserNotFoundException e) {
            LOG.debug("User profile not found for {}, using defaults", username);
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

        // AC4: Content status
        boolean hasTitle = false;
        boolean hasAbstract = false;
        if (entry.getSessionId() != null) {
            var latestSubmission = contentSubmissionRepository
                    .findFirstBySpeakerPoolIdOrderBySubmissionVersionDesc(entry.getId());
            if (latestSubmission.isPresent()) {
                hasTitle = latestSubmission.get().getTitle() != null
                        && !latestSubmission.get().getTitle().isBlank();
                hasAbstract = latestSubmission.get().getContentAbstract() != null
                        && !latestSubmission.get().getContentAbstract().isBlank();
                // Use submitted title if available (overrides original session title)
                if (hasTitle) {
                    sessionTitle = latestSubmission.get().getTitle();
                }
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

        // AC4: Reviewer feedback (if REVISION_NEEDED)
        String reviewerFeedback = null;
        if ("REVISION_NEEDED".equals(entry.getContentStatus())) {
            var latestSubmission = contentSubmissionRepository
                    .findFirstBySpeakerPoolIdOrderBySubmissionVersionDesc(entry.getId());
            if (latestSubmission.isPresent()) {
                reviewerFeedback = latestSubmission.get().getReviewerFeedback();
            }
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
        String contentUrl = entry.getSessionId() != null
                ? "/speaker-portal/content" : null;

        return DashboardUpcomingEventDto.builder()
                .eventCode(event.getEventCode())
                .eventTitle(event.getTitle())
                .eventDate(eventDate)
                .eventLocation(event.getVenueName())
                .sessionTitle(sessionTitle)
                .workflowState(entry.getStatus().name())
                .workflowStateLabel(WORKFLOW_STATE_LABELS.getOrDefault(entry.getStatus(), entry.getStatus().name()))
                .contentStatus(entry.getContentStatus())
                .contentStatusLabel(CONTENT_STATUS_LABELS.getOrDefault(
                        entry.getContentStatus(), entry.getContentStatus()))
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
                .profileUrl("/speaker-portal/profile")
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
