package ch.batbern.events.service;

import ch.batbern.events.client.UserApiClient;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Session;
import ch.batbern.events.domain.SessionUser;
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
import ch.batbern.events.repository.SessionUserRepository;
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

    // 2026-05-22 (BATbern75 bug report) — structural slot types that the organizer
    // self-assigns to (moderation/break/lunch/networking) leaked onto the SPEAKER
    // dashboard because the dashboard query is rooted in `session_users` (canonical
    // since 2026-05-21) and never filtered by session type. Worse, an ACCEPTED
    // moderation entry hid the real INVITED talk on the InvitationResponsePage
    // (`upcomingEvents.find(e => e.eventCode === X)`), surfacing as the misleading
    // "Already Responded — Accepted" banner. Single source of truth for the set
    // lives on Session.STRUCTURAL_SESSION_TYPES so SpeakerPortalAuthorizationService
    // (which has the same gap shape on the /respond endpoint) can reuse it via
    // Session.isStructuralSlot().

    // Friendly labels for workflow states (AC2)
    private static final Map<SpeakerWorkflowState, String> WORKFLOW_STATE_LABELS = Map.of(
            SpeakerWorkflowState.INVITED, "Invitation Pending",
            SpeakerWorkflowState.ACCEPTED, "Accepted",
            SpeakerWorkflowState.CONTENT_SUBMITTED, "Content Submitted",
            SpeakerWorkflowState.QUALITY_REVIEWED, "Quality Reviewed"
    );

    // 2026-05-20 (Q#D) — CONTENT_STATUS_LABELS map dropped along with the
    // `contentStatus` / `contentStatusLabel` fields on DashboardUpcomingEventDto.
    // Reviewer feedback is still surfaced via `reviewerFeedback`, derived directly
    // from the latest history row below.

    private final SpeakerPoolRepository speakerPoolRepository;
    private final EventRepository eventRepository;
    private final SessionRepository sessionRepository;
    private final SessionContentHistoryRepository sessionContentHistoryRepository;
    private final SessionMaterialsRepository sessionMaterialsRepository;
    private final SessionUserRepository sessionUserRepository;
    private final UserApiClient userApiClient;

    public SpeakerDashboardService(
            SpeakerPoolRepository speakerPoolRepository,
            EventRepository eventRepository,
            SessionRepository sessionRepository,
            SessionContentHistoryRepository sessionContentHistoryRepository,
            SessionMaterialsRepository sessionMaterialsRepository,
            SessionUserRepository sessionUserRepository,
            UserApiClient userApiClient) {
        this.speakerPoolRepository = speakerPoolRepository;
        this.eventRepository = eventRepository;
        this.sessionRepository = sessionRepository;
        this.sessionContentHistoryRepository = sessionContentHistoryRepository;
        this.sessionMaterialsRepository = sessionMaterialsRepository;
        this.sessionUserRepository = sessionUserRepository;
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

        // 2026-05-21 (BATbern75 follow-up) — canonical source is session_users.username,
        // NOT speaker_pool.username. The pool column carries one sessionId per row and
        // goes stale after a Sessions-tab reassignment (the new PRIMARY_SPEAKER's
        // dashboard would otherwise miss the session entirely). Querying session_users
        // also surfaces CO_SPEAKER / MODERATOR / PANELIST assignments that never had a
        // pool row of their own. One dashboard entry = one session_users row.
        List<SessionUser> memberships = sessionUserRepository.findByUsername(username);

        if (memberships.isEmpty()) {
            LOG.info("No session_users rows found for username: {}", username);
            return buildEmptyDashboard(username);
        }

        // Batch-load sessions referenced by the memberships. SessionUser.session is a
        // LAZY @ManyToOne; the explicit batch fetch avoids N+1.
        Set<UUID> sessionIds = memberships.stream()
                .map(su -> su.getSession().getId())
                .collect(Collectors.toSet());
        Map<UUID, Session> sessionsById = sessionRepository.findAllById(sessionIds).stream()
                .collect(Collectors.toMap(Session::getId, Function.identity()));

        // Batch-load events referenced by those sessions.
        Set<UUID> eventIds = sessionsById.values().stream()
                .map(Session::getEventId)
                .collect(Collectors.toSet());
        Map<UUID, Event> eventsById = eventRepository.findAllById(eventIds).stream()
                .collect(Collectors.toMap(Event::getId, Function.identity()));

        // Batch-load pool rows linked back via session.speakerPoolId. Sessions for which
        // this speaker is CO_SPEAKER (added via the Sessions tab without a pool row)
        // will have speakerPoolId pointing to whoever the PRIMARY pool row was, OR be
        // null. We tolerate both: the pool row is only used to enrich the entry with
        // workflow status + deadlines; absence means the speaker_users.isConfirmed
        // flag drives the displayed state.
        Set<UUID> poolIds = sessionsById.values().stream()
                .map(Session::getSpeakerPoolId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());
        Map<UUID, SpeakerPool> poolById = poolIds.isEmpty()
                ? Map.of()
                : speakerPoolRepository.findAllById(poolIds).stream()
                        .collect(Collectors.toMap(SpeakerPool::getId, Function.identity()));

        Instant now = Instant.now();

        // Build upcoming and past event lists — one entry per membership (i.e. per
        // session). A speaker with 3 sessions = 3 cards.
        List<DashboardUpcomingEventDto> upcomingEvents = new ArrayList<>();
        List<DashboardPastEventDto> pastEvents = new ArrayList<>();

        for (SessionUser membership : memberships) {
            Session session = sessionsById.get(membership.getSession().getId());
            if (session == null) {
                continue;
            }
            // 2026-05-22 (BATbern75) — drop structural slot types (moderation, break,
            // lunch, networking). NULL session_type passes through (legacy data).
            if (session.isStructuralSlot()) {
                continue;
            }
            Event event = eventsById.get(session.getEventId());
            if (event == null) {
                continue;
            }
            // Pool row is optional — only present when the session was provisioned via
            // the PRIMARY_SPEAKER workflow (Story 11.E.8 provisionSessionAndPrimary).
            // For a CO_SPEAKER added via the Sessions tab, the pool row attached to the
            // session belongs to a DIFFERENT user — its workflow state is theirs, not
            // ours. Only inherit pool state when the pool row was provisioned FOR this
            // speaker (i.e. the membership role is PRIMARY_SPEAKER).
            //
            // Legacy fallback (BATbern74 case, 2026-05-21): pre-Story-11.E.8 data has the
            // pool→session FK set but the session→pool back-reference missing (it wasn't
            // backfilled). If session.speakerPoolId is null, try the reverse lookup via
            // speakerPoolRepository.findBySessionId so the dashboard still picks up the
            // canonical workflow state. The slot's pool row represents the session's
            // workflow regardless of who the current PRIMARY_SPEAKER is — after a
            // reassignment the new speaker still inherits the slot's state (e.g. content
            // already submitted by the previous speaker; the content lives on session_-
            // content_history keyed by session_id, not by username).
            SpeakerPool pool = null;
            if (membership.getSpeakerRole() == SessionUser.SpeakerRole.PRIMARY_SPEAKER) {
                if (session.getSpeakerPoolId() != null) {
                    pool = poolById.get(session.getSpeakerPoolId());
                }
                if (pool == null) {
                    pool = speakerPoolRepository.findBySessionId(session.getId()).stream()
                            .findFirst()
                            .orElse(null);
                }
            }

            boolean isUpcoming = event.getDate() != null && event.getDate().isAfter(now);
            SpeakerWorkflowState effectiveState = pool != null
                    ? pool.getStatus()
                    : (membership.isConfirmed() ? SpeakerWorkflowState.ACCEPTED
                            : SpeakerWorkflowState.INVITED);
            // For PRIMARY_SPEAKER paths we keep the existing UPCOMING/PAST state filters
            // (they encode "the speaker has actually been invited" semantics). For
            // co-speaker entries we synthesise a state that always falls into the
            // displayable range. Membership decline (declinedAt set) hides the entry.
            if (membership.getDeclinedAt() != null) {
                continue;
            }
            if (isUpcoming && UPCOMING_STATES.contains(effectiveState)) {
                upcomingEvents.add(buildUpcomingEvent(membership, pool, event, session));
            } else if (!isUpcoming && PAST_STATES.contains(effectiveState)) {
                pastEvents.add(buildPastEvent(membership, pool, event, session));
            }
        }

        // AC2: Sort upcoming by event date ascending (soonest first)
        upcomingEvents.sort(Comparator.comparing(DashboardUpcomingEventDto::eventDate));

        // AC3: Sort past by event date descending (most recent first)
        pastEvents.sort(Comparator.comparing(DashboardPastEventDto::eventDate).reversed());

        DashboardHeader header = resolveDashboardHeader(memberships, poolById, username);

        LOG.info("Dashboard loaded for speaker: {} - {} upcoming, {} past events",
                username, upcomingEvents.size(), pastEvents.size());

        return SpeakerDashboardDto.builder()
                .speakerName(header.speakerName())
                .profilePictureUrl(header.profilePictureUrl())
                .profileCompleteness(header.profileCompleteness())
                .upcomingEvents(upcomingEvents)
                .pastEvents(pastEvents)
                .build();
    }

    /**
     * Empty-state dashboard for speakers with the SPEAKER role but no current
     * session memberships. Greets the speaker by their CUMS display name; falls
     * back to the raw username on profile-resolve failure.
     */
    private SpeakerDashboardDto buildEmptyDashboard(String username) {
        // Code review 2026-05-18 (P23): fall back to the User's display name from CUMS
        // rather than echoing the raw username (e.g. "alice.muller" → "Alice Müller").
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
            LOG.debug("Could not resolve display name for empty-dashboard: {} ({})",
                    username, e.getMessage());
        }
        return SpeakerDashboardDto.builder()
                .speakerName(fallbackName)
                .profileCompleteness(0)
                .upcomingEvents(List.of())
                .pastEvents(List.of())
                .build();
    }

    /** Header fields for the speaker dashboard: name + portrait + profile completeness. */
    private record DashboardHeader(
            String speakerName, String profilePictureUrl, int profileCompleteness) {}

    /**
     * Resolve the dashboard header — Story 11.E.8 §2.9 follow-up: prefer the CUMS
     * User profile name; fall back to a pool brainstorm name or the SessionUser
     * cached name. The profile picture + completeness ride along with the lookup.
     */
    private DashboardHeader resolveDashboardHeader(
            List<SessionUser> memberships,
            Map<UUID, SpeakerPool> poolById,
            String username) {
        String poolFallbackName = poolById.values().stream()
                .findFirst()
                .map(SpeakerPool::getSpeakerName)
                .orElse(null);
        String cachedFallback = memberships.stream()
                .map(su -> {
                    String f = su.getSpeakerFirstName();
                    String l = su.getSpeakerLastName();
                    if ((f == null || f.isBlank()) && (l == null || l.isBlank())) {
                        return null;
                    }
                    return ((f != null ? f : "") + " " + (l != null ? l : "")).trim();
                })
                .filter(n -> n != null && !n.isBlank())
                .findFirst()
                .orElse(null);
        String speakerName = poolFallbackName != null ? poolFallbackName
                : (cachedFallback != null ? cachedFallback : username);
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
        return new DashboardHeader(speakerName, profilePictureUrl, profileCompleteness);
    }

    private DashboardUpcomingEventDto buildUpcomingEvent(
            SessionUser membership, SpeakerPool pool, Event event, Session session) {
        String eventDate = formatEventDate(event.getDate());
        String sessionTitle = session.getTitle();
        UUID sessionId = session.getId();

        // Effective workflow state: pool row's when PRIMARY-via-pool; synthesized from
        // session_users.isConfirmed when CO_SPEAKER / no pool.
        SpeakerWorkflowState effectiveState = pool != null
                ? pool.getStatus()
                : (membership.isConfirmed() ? SpeakerWorkflowState.ACCEPTED
                        : SpeakerWorkflowState.INVITED);

        // AC4: Content status. Story 11.E.8 §2.9: sessions.title is the canonical "current"
        // for every surface (speaker dashboard, organizer kanban, public archive, speaker
        // portal form). The latest session_content_history row is consulted ONLY to flag
        // whether the speaker has actually submitted content (vs. the placeholder title
        // written at CONTACTED → READY) and to surface reviewer feedback.
        boolean hasTitle = false;
        boolean hasAbstract = false;
        var latestSubmissionAtSession = sessionContentHistoryRepository
                .findFirstBySessionIdOrderBySubmissionVersionDesc(sessionId);
        if (latestSubmissionAtSession.isPresent()) {
            hasTitle = latestSubmissionAtSession.get().getTitle() != null
                    && !latestSubmissionAtSession.get().getTitle().isBlank();
            hasAbstract = latestSubmissionAtSession.get().getContentAbstract() != null
                    && !latestSubmissionAtSession.get().getContentAbstract().isBlank();
        }

        // AC4: Material status
        boolean hasMaterial = sessionMaterialsRepository
                .existsBySession_IdAndMaterialType(sessionId, "PRESENTATION");
        String materialFileName = null;
        if (hasMaterial) {
            var materials = sessionMaterialsRepository.findBySession_Id(sessionId);
            materialFileName = materials.stream()
                    .filter(m -> "PRESENTATION".equals(m.getMaterialType()))
                    .findFirst()
                    .map(m -> m.getFileName())
                    .orElse(null);
        }

        // AC4: Reviewer feedback (surfaced when the moderator left a comment on the
        // latest submission). 2026-05-20 (Q#D) — the `derivedContentStatus` indirection
        // was removed along with the `contentStatus` field on the DTO; read the latest
        // history row's reviewer_feedback directly.
        String reviewerFeedback = latestSubmissionAtSession
                .map(v -> v.getReviewerFeedback())
                .filter(fb -> fb != null && !fb.isBlank())
                .orElse(null);

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

        // Format deadlines. Only available when this speaker has a pool row (PRIMARY
        // workflow); CO_SPEAKER memberships don't carry workflow deadlines.
        String responseDeadline = pool != null && pool.getResponseDeadline() != null
                ? pool.getResponseDeadline().toString() : null;
        String contentDeadline = pool != null && pool.getContentDeadline() != null
                ? pool.getContentDeadline().toString() : null;

        // Quick-action URLs (AC2). For pool-backed PRIMARY memberships we surface the
        // workflow-driven respond/submit links; for CO_SPEAKER we never offer the
        // accept/decline portal because the workflow lives on the pool row.
        String respondUrl = effectiveState == SpeakerWorkflowState.INVITED && pool != null
                ? "/speaker-portal/respond" : null;
        boolean canSubmitContent = pool != null
                && effectiveState != SpeakerWorkflowState.INVITED
                && effectiveState != SpeakerWorkflowState.IDENTIFIED
                && effectiveState != SpeakerWorkflowState.CONTACTED
                && effectiveState != SpeakerWorkflowState.READY
                && effectiveState != SpeakerWorkflowState.DECLINED;
        String contentUrl = canSubmitContent ? "/speaker-portal/content" : null;

        return DashboardUpcomingEventDto.builder()
                .eventCode(event.getEventCode())
                .eventTitle(event.getTitle())
                .eventDate(eventDate)
                .eventLocation(event.getVenueName())
                .sessionTitle(sessionTitle)
                .workflowState(effectiveState.name())
                .workflowStateLabel(WORKFLOW_STATE_LABELS.getOrDefault(effectiveState, effectiveState.name()))
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

    private DashboardPastEventDto buildPastEvent(
            SessionUser membership, SpeakerPool pool, Event event, Session session) {
        String eventDate = formatEventDate(event.getDate());
        String sessionTitle = session.getTitle();
        UUID sessionId = session.getId();

        boolean hasMaterial = sessionMaterialsRepository
                .existsBySession_IdAndMaterialType(sessionId, "PRESENTATION");
        String materialFileName = null;
        if (hasMaterial) {
            var materials = sessionMaterialsRepository.findBySession_Id(sessionId);
            materialFileName = materials.stream()
                    .filter(m -> "PRESENTATION".equals(m.getMaterialType()))
                    .findFirst()
                    .map(m -> m.getFileName())
                    .orElse(null);
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
