package ch.batbern.events.watch;

import ch.batbern.events.client.UserApiClient;
import ch.batbern.events.domain.Session;
import ch.batbern.events.domain.SessionUser;
import ch.batbern.events.dto.generated.users.UserResponse;
import ch.batbern.events.exception.EventNotFoundException;
import ch.batbern.events.exception.UserNotFoundException;
import ch.batbern.events.exception.UserServiceException;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.SessionRepository;
import ch.batbern.events.repository.SessionUserRepository;
import ch.batbern.events.repository.LiveTimingPresenceRepository;
import ch.batbern.events.repository.UserPortraitProjection;
import ch.batbern.events.watch.dto.LiveTimingActionRequest;
import ch.batbern.events.watch.dto.LiveTimingResponse;
import ch.batbern.events.watch.dto.SessionDetail;
import ch.batbern.events.watch.dto.SpeakerDetail;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Story 15.1: REST polling replacement for the STOMP live-agenda surface.
 *
 * <p>{@link #getLiveTiming(String)} assembles the {@link LiveTimingResponse} snapshot
 * entirely from persistent storage (sessions + {@code events.live_timing_version} +
 * {@code live_timing_presence}) so successive polls on different Fargate tasks are
 * identical (AC3). {@link #applyAction} reuses the existing {@link WatchSessionService}
 * cascade (parity, AC5) and bumps the monotonic version in the same transaction (AC1).
 *
 * <p>The speaker enrichment here intentionally mirrors {@code WatchEventController}'s
 * (per-record {@link UserApiClient} lookup + the read-only company-portrait join, ADR-004).
 * The duplication is transient — the WebSocket {@code WatchEventController} path is removed
 * in this story's P3 teardown.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LiveTimingService {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_INSTANT;

    /** Presence TTL: an organizer counts as "present" if seen within this window. */
    private static final Duration PRESENCE_TTL = Duration.ofSeconds(30);

    private final SessionRepository sessionRepository;
    private final EventRepository eventRepository;
    private final SessionUserRepository sessionUserRepository;
    private final LiveTimingPresenceRepository presenceRepository;
    private final UserApiClient userApiClient;
    private final WatchSpeakerArrivalService arrivalService;
    private final WatchSessionService watchSessionService;

    /**
     * Builds the current live-timing snapshot for an event.
     *
     * @param eventCode the event
     * @return the snapshot (version, presence, arrival summary, sessions with timing)
     * @throws EventNotFoundException if no event with the given code exists
     */
    @Transactional(readOnly = true)
    public LiveTimingResponse getLiveTiming(String eventCode) {
        long version = eventRepository.findLiveTimingVersionByEventCode(eventCode)
                .orElseThrow(() -> new EventNotFoundException(
                        "Event not found: " + eventCode));

        List<Session> sessions = sessionRepository.findByEventCode(eventCode);
        sessions.sort(Comparator.comparing(
                s -> s.getStartTime() != null ? s.getStartTime() : Instant.MIN));

        List<SessionDetail> sessionDetails = enrichSessions(sessions);

        boolean organizerPresent = presenceRepository.existsActivePresence(
                eventCode, Instant.now().minus(PRESENCE_TTL));

        int arrived = arrivalService.getArrivals(eventCode).size();
        int total = (int) sessionRepository.countDistinctSpeakersByEventCode(eventCode);
        String currentSlug = deriveCurrentSessionSlug(sessions);

        return new LiveTimingResponse(
                eventCode, version, organizerPresent, currentSlug, arrived, total, sessionDetails);
    }

    /**
     * Reads the current live-timing version (cheap; for the ETag / 304 path).
     *
     * @param eventCode the event
     * @return the version
     * @throws EventNotFoundException if no event with the given code exists
     */
    @Transactional(readOnly = true)
    public long getVersion(String eventCode) {
        return eventRepository.findLiveTimingVersionByEventCode(eventCode)
                .orElseThrow(() -> new EventNotFoundException("Event not found: " + eventCode));
    }

    /**
     * Records that an authenticated organizer polled this event (presence heartbeat).
     * Anonymous presenter polls do NOT call this — they must not flip the present flag.
     *
     * @param eventCode the event polled
     * @param username  the authenticated organizer
     */
    @Transactional
    public void recordOrganizerPoll(String eventCode, String username) {
        presenceRepository.upsertPresence(eventCode, username, Instant.now());
    }

    /**
     * Applies a timing action (END/EXTEND/DELAY) and bumps the monotonic version in the
     * same transaction, then returns the post-cascade snapshot.
     *
     * <p>Delegates the cascade to {@link WatchSessionService} (REQUIRED propagation joins
     * this transaction) so behaviour is byte-identical to the WebSocket path (AC5). The
     * version increment runs last so its flush/clear persists the cascade's pending
     * session/event changes first.
     *
     * @param eventCode the event
     * @param request   the action
     * @param username  the authenticated organizer applying it
     * @return the post-action snapshot with the bumped version
     * @throws EventNotFoundException if no event with the given code exists
     */
    @Transactional
    public LiveTimingResponse applyAction(
            String eventCode, LiveTimingActionRequest request, String username) {
        int minutes = request.minutes() != null ? request.minutes() : 0;
        switch (request.type()) {
            case END_SESSION ->
                    watchSessionService.endSession(eventCode, request.sessionSlug(), username);
            case EXTEND_SESSION ->
                    watchSessionService.extendSession(
                            eventCode, request.sessionSlug(), minutes, username);
            case DELAY_TO_PREVIOUS ->
                    watchSessionService.delayToPreviousSession(
                            eventCode, request.sessionSlug(), minutes, username);
            default -> throw new IllegalArgumentException(
                    "Unknown live-timing action: " + request.type());
        }

        int updated = eventRepository.incrementLiveTimingVersion(eventCode);
        if (updated == 0) {
            throw new EventNotFoundException("Event not found: " + eventCode);
        }
        log.debug("Live-timing action {} on {}/{} by {} — version bumped",
                request.type(), eventCode, request.sessionSlug(), username);

        return getLiveTiming(eventCode);
    }

    /**
     * The "current" session: an organizer-started-but-not-ended session if one exists
     * (actualStartTime set, actualEndTime null); otherwise the clock-based active session
     * (now within scheduled start/end and not yet completed). Null when none applies.
     */
    private String deriveCurrentSessionSlug(List<Session> sessions) {
        for (Session s : sessions) {
            if (s.getActualStartTime() != null && s.getActualEndTime() == null) {
                return s.getSessionSlug();
            }
        }
        Instant now = Instant.now();
        for (Session s : sessions) {
            if (s.getStartTime() != null && s.getEndTime() != null
                    && s.getCompletedByUsername() == null
                    && !now.isBefore(s.getStartTime()) && now.isBefore(s.getEndTime())) {
                return s.getSessionSlug();
            }
        }
        return null;
    }

    /** Enriches sessions with speaker identity + company logo (ADR-004 read-time enrichment). */
    private List<SessionDetail> enrichSessions(List<Session> sessions) {
        Set<String> speakerUsernames = sessions.stream()
                .flatMap(s -> s.getSessionUsers().stream())
                .map(SessionUser::getUsername)
                .collect(Collectors.toSet());

        Map<String, UserResponse> userMap = new HashMap<>();
        boolean degraded = false;
        for (String username : speakerUsernames) {
            try {
                userMap.put(username, userApiClient.getUserByUsername(username));
            } catch (UserNotFoundException e) {
                log.debug("Speaker user {} not found; using session_users fallback", username);
            } catch (UserServiceException e) {
                if (!degraded) {
                    log.warn("user-management-service degraded; using session_users fallback: {}",
                            e.getMessage());
                    degraded = true;
                }
            }
        }

        Map<String, UserPortraitProjection> portraitMap = new HashMap<>();
        if (!speakerUsernames.isEmpty()) {
            for (UserPortraitProjection p
                    : sessionUserRepository.findUserPortraitsByUsernames(speakerUsernames)) {
                portraitMap.put(p.getUsername(), p);
            }
        }

        return sessions.stream()
                .map(session -> mapToSessionDetail(session, userMap, portraitMap))
                .collect(Collectors.toList());
    }

    private SessionDetail mapToSessionDetail(Session session, Map<String, UserResponse> userMap,
                                             Map<String, UserPortraitProjection> portraitMap) {
        List<SpeakerDetail> speakers = session.getSessionUsers().stream()
                .map(su -> mapToSpeakerDetail(su, userMap, portraitMap))
                .collect(Collectors.toList());

        String scheduledStart = session.getStartTime() != null
                ? ISO.format(session.getStartTime()) : null;
        String scheduledEnd = session.getEndTime() != null
                ? ISO.format(session.getEndTime()) : null;

        Integer durationMinutes = null;
        if (session.getStartTime() != null && session.getEndTime() != null) {
            durationMinutes = (int) ChronoUnit.MINUTES.between(
                    session.getStartTime(), session.getEndTime());
        }

        String actualStart = session.getActualStartTime() != null
                ? ISO.format(session.getActualStartTime()) : null;
        String actualEnd = session.getActualEndTime() != null
                ? ISO.format(session.getActualEndTime()) : null;
        int overrun = session.getOverrunMinutes() != null ? session.getOverrunMinutes() : 0;

        return new SessionDetail(
                session.getSessionSlug(),
                session.getTitle(),
                session.getDescription(),
                session.getSessionType(),
                scheduledStart,
                scheduledEnd,
                durationMinutes,
                speakers,
                deriveSessionStatus(session),
                actualStart,
                actualEnd,
                overrun,
                session.getCompletedByUsername()
        );
    }

    private SpeakerDetail mapToSpeakerDetail(SessionUser sessionUser, Map<String, UserResponse> userMap,
                                             Map<String, UserPortraitProjection> portraitMap) {
        UserResponse user = userMap.get(sessionUser.getUsername());
        String firstName = user != null ? user.getFirstName() : sessionUser.getSpeakerFirstName();
        String lastName = user != null ? user.getLastName() : sessionUser.getSpeakerLastName();
        String bio = user != null ? user.getBio() : null;
        String profilePictureUrl = user != null && user.getProfilePictureUrl() != null
                ? user.getProfilePictureUrl().toString() : null;

        UserPortraitProjection portrait = portraitMap.get(sessionUser.getUsername());
        String company = portrait != null ? portrait.getCompanyDisplayName() : null;
        String companyLogoUrl = portrait != null ? portrait.getCompanyLogoUrl() : null;

        return new SpeakerDetail(
                sessionUser.getUsername(),
                firstName,
                lastName,
                company,
                companyLogoUrl,
                profilePictureUrl,
                bio,
                sessionUser.getSpeakerRole() != null
                        ? sessionUser.getSpeakerRole().name().toLowerCase() : null
        );
    }

    /** Derives session status from timing relative to now (actual times take precedence). */
    private String deriveSessionStatus(Session session) {
        if (session.getCompletedByUsername() != null || session.getActualEndTime() != null) {
            return "COMPLETED";
        }
        if (session.getStartTime() == null) {
            return "SCHEDULED";
        }
        Instant now = Instant.now();
        if (session.getActualStartTime() != null) {
            return "ACTIVE";
        }
        if (now.isBefore(session.getStartTime())) {
            return "SCHEDULED";
        }
        if (session.getEndTime() != null && now.isAfter(session.getEndTime())) {
            return "COMPLETED";
        }
        return "ACTIVE";
    }
}
