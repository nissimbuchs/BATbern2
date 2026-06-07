package ch.batbern.events.watch;

import ch.batbern.events.client.UserApiClient;
import ch.batbern.events.dto.generated.users.UserResponse;
import ch.batbern.events.exception.UserNotFoundException;
import ch.batbern.events.exception.UserServiceException;
import ch.batbern.events.repository.SessionRepository;
import ch.batbern.events.watch.domain.SpeakerArrival;
import ch.batbern.events.watch.dto.ArrivalCount;
import ch.batbern.events.watch.dto.ArrivalStatusDto;
import ch.batbern.events.watch.dto.SpeakerArrivalBroadcast;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for speaker arrival tracking.
 * W2.4: FR38, FR39 — idempotent arrival confirmation + real-time broadcast to all organizer watches.
 *
 * Architecture note: Belongs to event-management-service (not company-user-management-service)
 * because EMS owns session/event data and WebSocket connections.
 *
 * Story 11.C.1: speaker identity (first/last name) is now resolved via UserApiClient
 * (per-record HTTP enrichment per ADR-004) instead of the deleted Speaker entity.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WatchSpeakerArrivalService {

    private final SpeakerArrivalRepository arrivalRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final UserApiClient userApiClient;
    private final SessionRepository sessionRepository;

    /**
     * Returns all arrival records for a given event.
     * Used by Watch clients on initial load (GET fallback when WebSocket not yet connected).
     */
    public List<ArrivalStatusDto> getArrivals(String eventCode) {
        return arrivalRepository.findByEventCode(eventCode)
                .stream()
                .map(a -> new ArrivalStatusDto(
                        a.getSpeakerUsername(),
                        a.getConfirmedByUsername(),
                        a.getArrivedAt().toString()
                ))
                .collect(Collectors.toList());
    }

    /**
     * Confirms a speaker's arrival idempotently and broadcasts to all organizer watches.
     *
     * Idempotency: UNIQUE (event_code, speaker_username) in DB.
     * Concurrent confirmation safety: @Transactional + catch DataIntegrityViolationException.
     * Both REST fallback and STOMP action handler call this method.
     */
    @Transactional
    public SpeakerArrivalBroadcast confirmArrival(
            String eventCode,
            String speakerUsername,
            String confirmedBy
    ) {
        // Idempotent: catch DataIntegrityViolationException from UNIQUE(event_code, speaker_username)
        try {
            if (!arrivalRepository.existsByEventCodeAndSpeakerUsername(eventCode, speakerUsername)) {
                arrivalRepository.save(new SpeakerArrival(eventCode, speakerUsername, confirmedBy));
            }
        } catch (DataIntegrityViolationException e) {
            // Concurrent confirmation — already inserted by another organizer. Safe to ignore.
            log.debug(
                "Concurrent arrival confirmation for {}/{} — already recorded",
                eventCode,
                speakerUsername
            );
        }

        // Server-authoritative counts for FR39 (real-time counter sync across all watches)
        long arrivedCount = arrivalRepository.countByEventCode(eventCode);
        long totalSpeakers = getTotalSpeakerCount(eventCode);

        // Look up speaker name for broadcast via UserApiClient (ADR-004 HTTP enrichment).
        // Fall back to username if the user is unknown OR user-management-service is degraded —
        // the arrival broadcast (FR38) must fire regardless, since the arrival is already persisted.
        String firstName = speakerUsername;
        String lastName = "";
        try {
            UserResponse user = userApiClient.getUserByUsername(speakerUsername);
            firstName = user.getFirstName() != null ? user.getFirstName() : speakerUsername;
            lastName = user.getLastName() != null ? user.getLastName() : "";
        } catch (UserNotFoundException e) {
            log.debug("User {} not found in user-management-service; using username fallback", speakerUsername);
        } catch (UserServiceException e) {
            log.warn("user-management-service degraded ({}); using username fallback for arrival {}/{}",
                    e.getMessage(), eventCode, speakerUsername);
        }

        SpeakerArrivalBroadcast broadcast = new SpeakerArrivalBroadcast(
                "SPEAKER_ARRIVED",
                eventCode,
                speakerUsername,
                firstName,
                lastName,
                confirmedBy,
                Instant.now().toString(),
                new ArrivalCount((int) arrivedCount, (int) totalSpeakers)
        );

        // Broadcast to all watches subscribed to arrivals topic (FR38: <3 seconds)
        messagingTemplate.convertAndSend(
                "/topic/events/" + eventCode + "/arrivals",
                broadcast
        );

        return broadcast;
    }

    /**
     * Returns total distinct speaker count for an event.
     * Single JPQL query — avoids N+1 from loading sessions then streaming sessionUsers.
     */
    private long getTotalSpeakerCount(String eventCode) {
        return sessionRepository.countDistinctSpeakersByEventCode(eventCode);
    }
}
