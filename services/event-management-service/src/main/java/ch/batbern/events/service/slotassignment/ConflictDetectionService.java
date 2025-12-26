package ch.batbern.events.service.slotassignment;

import ch.batbern.events.domain.Session;
import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.events.domain.SpeakerSlotPreference;
import ch.batbern.events.repository.SessionRepository;
import ch.batbern.events.repository.SpeakerPoolRepository;
import ch.batbern.events.repository.SpeakerSlotPreferenceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for detecting scheduling conflicts
 * Story BAT-11 (5.7): Slot Assignment & Progressive Publishing
 *
 * Detects:
 * - Room overlap conflicts (same room, overlapping time)
 * - Speaker double-booking (same speaker in overlapping sessions)
 * - Speaker preference conflicts (warnings only)
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ConflictDetectionService {

    private final SessionRepository sessionRepository;
    private final SpeakerPoolRepository speakerPoolRepository;
    private final SpeakerSlotPreferenceRepository speakerSlotPreferenceRepository;

    /**
     * Detect room overlap conflict
     *
     * @param excludeSessionSlug - Optional session slug to exclude from conflict check (for reassignments)
     */
    public Optional<SchedulingConflict> detectRoomOverlap(String eventCode, Instant startTime,
                                                          Instant endTime, String room,
                                                          String excludeSessionSlug) {
        log.debug("Checking room overlap for {} at {}-{} (excluding: {})",
                room, startTime, endTime, excludeSessionSlug);

        // Find sessions in same room with overlapping time
        List<Session> sessions = sessionRepository.findByEventCode(eventCode);
        List<UUID> conflictingSessionIds = sessions.stream()
                .filter(s -> s.getRoom() != null && s.getRoom().equals(room))
                .filter(s -> s.getStartTime() != null && s.getEndTime() != null)
                // Exclude the session being updated to allow reassignment
                .filter(s -> excludeSessionSlug == null || !s.getSessionSlug().equals(excludeSessionSlug))
                .filter(s -> timesOverlap(s.getStartTime(), s.getEndTime(), startTime, endTime))
                .map(Session::getId)
                .toList();

        if (!conflictingSessionIds.isEmpty()) {
            return Optional.of(SchedulingConflict.builder()
                    .conflictType(ConflictType.ROOM_OVERLAP)
                    .severity(ConflictSeverity.ERROR)
                    .message("Room " + room + " conflicts with existing schedule")
                    .conflictingSessionIds(conflictingSessionIds)
                    .conflictStartTime(startTime)
                    .conflictEndTime(endTime)
                    .resolution("Choose a different room or adjust timing")
                    .build());
        }

        return Optional.empty();
    }

    /**
     * Detect speaker double-booking by session slug
     */
    public Optional<SchedulingConflict> detectSpeakerDoubleBooking(String sessionSlug,
                                                                   Instant startTime,
                                                                   Instant endTime) {
        log.debug("Checking speaker double-booking for session {} at {}-{}", sessionSlug, startTime, endTime);

        // Find the session to get the speaker
        Session session = sessionRepository.findBySessionSlug(sessionSlug).orElse(null);
        if (session == null || session.getSpeakerPoolId() == null) {
            return Optional.empty();
        }

        // Delegate to the UUID version
        return detectSpeakerDoubleBooking(session.getSpeakerPoolId(), startTime, endTime);
    }

    /**
     * Detect speaker double-booking by speaker ID
     */
    public Optional<SchedulingConflict> detectSpeakerDoubleBooking(UUID speakerId,
                                                                   Instant startTime,
                                                                   Instant endTime) {
        log.debug("Checking speaker double-booking for speaker {} at {}-{}", speakerId, startTime, endTime);

        SpeakerPool speaker = speakerPoolRepository.findById(speakerId).orElse(null);
        if (speaker == null) {
            return Optional.empty();
        }

        // Find all sessions for this speaker with overlapping time
        List<Session> allSessions = sessionRepository.findByEventId(speaker.getEventId());
        List<UUID> conflictingSessionIds = allSessions.stream()
                .filter(s -> s.getSpeakerPoolId() != null && s.getSpeakerPoolId().equals(speakerId))
                .filter(s -> s.getStartTime() != null && s.getEndTime() != null)
                .filter(s -> timesOverlap(s.getStartTime(), s.getEndTime(), startTime, endTime))
                .map(Session::getId)
                .toList();

        if (!conflictingSessionIds.isEmpty()) {
            String speakerName = speaker.getUsername() != null ? speaker.getUsername() : speaker.getSpeakerName();
            // Get the conflicting session slug for the error response
            String conflictingSlug = allSessions.stream()
                    .filter(s -> s.getSpeakerPoolId() != null && s.getSpeakerPoolId().equals(speakerId))
                    .filter(s -> s.getStartTime() != null && s.getEndTime() != null)
                    .filter(s -> timesOverlap(s.getStartTime(), s.getEndTime(), startTime, endTime))
                    .findFirst()
                    .map(Session::getSessionSlug)
                    .orElse("unknown");

            return Optional.of(SchedulingConflict.builder()
                    .conflictType(ConflictType.SPEAKER_DOUBLE_BOOKED)
                    .severity(ConflictSeverity.ERROR)
                    .message("Speaker " + speakerName + " is already scheduled at this time")
                    .conflictingSessionIds(conflictingSessionIds)
                    .conflictingSessionSlug(conflictingSlug)
                    .conflictStartTime(startTime)
                    .conflictEndTime(endTime)
                    .resolution("Choose a different time slot for this speaker")
                    .build());
        }

        return Optional.empty();
    }

    /**
     * Detect speaker preference conflict (warning only)
     */
    public Optional<SchedulingConflict> detectSpeakerPreferenceConflict(UUID speakerId,
                                                                        Instant startTime) {
        log.debug("Checking speaker preference for speaker {} at {}", speakerId, startTime);

        SpeakerPool speaker = speakerPoolRepository.findById(speakerId).orElse(null);
        if (speaker == null) {
            return Optional.empty();
        }

        Optional<SpeakerSlotPreference> preferenceOpt = speakerSlotPreferenceRepository.findBySpeakerId(speakerId);
        if (preferenceOpt.isEmpty()) {
            return Optional.empty();
        }

        SpeakerSlotPreference preference = preferenceOpt.get();
        String preferredTimeOfDay = preference.getPreferredTimeOfDay();

        if (preferredTimeOfDay == null || preferredTimeOfDay.equals("any")) {
            return Optional.empty();
        }

        // Check if session time matches preference
        ZonedDateTime zonedTime = startTime.atZone(ZoneId.of("Europe/Zurich"));
        int hour = zonedTime.getHour();

        boolean matchesPreference = switch (preferredTimeOfDay.toLowerCase()) {
            case "morning" -> hour >= 8 && hour < 12;
            case "afternoon" -> hour >= 12 && hour < 17;
            case "evening" -> hour >= 17 && hour < 21;
            default -> true;
        };

        if (!matchesPreference) {
            String speakerName = speaker.getUsername() != null ? speaker.getUsername() : speaker.getSpeakerName();
            return Optional.of(SchedulingConflict.builder()
                    .conflictType(ConflictType.PREFERENCE_MISMATCH)
                    .severity(ConflictSeverity.WARNING)
                    .message("Speaker " + speakerName + " prefers " + preferredTimeOfDay + " sessions")
                    .conflictingSessionIds(List.of())
                    .conflictStartTime(startTime)
                    .resolution("Consider assigning to a " + preferredTimeOfDay + " slot if available")
                    .build());
        }

        return Optional.empty();
    }

    /**
     * Analyze all conflicts for an event
     */
    public ConflictAnalysisResponse analyzeAllConflicts(String eventCode) {
        log.info("Analyzing all conflicts for event: {}", eventCode);

        List<Session> sessions = sessionRepository.findByEventCode(eventCode);
        List<ConflictAnalysisResponse.ConflictDetail> conflicts = new ArrayList<>();

        // Check for room overlaps
        for (int i = 0; i < sessions.size(); i++) {
            Session sessionA = sessions.get(i);
            if (sessionA.getStartTime() == null || sessionA.getRoom() == null) {
                continue;
            }

            for (int j = i + 1; j < sessions.size(); j++) {
                Session sessionB = sessions.get(j);
                if (sessionB.getStartTime() == null || sessionB.getRoom() == null) {
                    continue;
                }

                // Check room overlap
                if (sessionA.getRoom().equals(sessionB.getRoom())
                    && timesOverlap(sessionA.getStartTime(), sessionA.getEndTime(),
                                sessionB.getStartTime(), sessionB.getEndTime())) {

                    conflicts.add(ConflictAnalysisResponse.ConflictDetail.builder()
                            .sessionSlug(sessionA.getSessionSlug())
                            .conflictType(ConflictType.ROOM_OVERLAP)
                            .severity(ConflictSeverity.ERROR)
                            .affectedSessions(List.of(sessionA.getSessionSlug(), sessionB.getSessionSlug()))
                            .timeRange(ConflictAnalysisResponse.TimeRange.builder()
                                    .start(sessionA.getStartTime().toString())
                                    .end(sessionA.getEndTime().toString())
                                    .build())
                            .resolution("Assign one session to a different room or adjust timing")
                            .build());
                }
            }
        }

        return ConflictAnalysisResponse.builder()
                .hasConflicts(!conflicts.isEmpty())
                .conflictCount(conflicts.size())
                .conflicts(conflicts)
                .build();
    }

    /**
     * Check if two time ranges overlap
     */
    private boolean timesOverlap(Instant start1, Instant end1, Instant start2, Instant end2) {
        return start1.isBefore(end2) && end1.isAfter(start2);
    }
}
