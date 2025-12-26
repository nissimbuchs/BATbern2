package ch.batbern.events.service.slotassignment;

import ch.batbern.events.domain.Session;
import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.events.domain.SpeakerSlotPreference;
import ch.batbern.events.repository.SessionRepository;
import ch.batbern.events.repository.SpeakerPoolRepository;
import ch.batbern.events.repository.SpeakerSlotPreferenceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit tests for ConflictDetectionService
 * Story BAT-11 (5.7): Slot Assignment & Progressive Publishing - Task 2a
 *
 * IMPORTANT: RED PHASE tests - will fail until ConflictDetectionService is implemented.
 *
 * Service responsibilities:
 * - Detect room overlap conflicts (same room, overlapping time)
 * - Detect speaker double-booking (same speaker in overlapping sessions)
 * - Detect speaker unavailability conflicts (preferences)
 * - Provide comprehensive conflict analysis for events
 *
 * Tests cover AC9: Conflict detection and warnings
 */
@ExtendWith(MockitoExtension.class)
class ConflictDetectionServiceTest {

    private ConflictDetectionService conflictDetectionService;

    @Mock
    private SessionRepository sessionRepository;

    @Mock
    private SpeakerPoolRepository speakerPoolRepository;

    @Mock
    private SpeakerSlotPreferenceRepository speakerSlotPreferenceRepository;

    private Session sessionA;
    private Session sessionB;
    private SpeakerPool speaker;

    @BeforeEach
    void setUp() {
        conflictDetectionService = new ConflictDetectionService(
                sessionRepository,
                speakerPoolRepository,
                speakerSlotPreferenceRepository
        );

        sessionA = Session.builder()
                .id(UUID.randomUUID())
                .sessionSlug("session-a")
                .eventCode("BATbern997")
                .startTime(Instant.parse("2025-06-15T18:00:00Z"))
                .endTime(Instant.parse("2025-06-15T18:45:00Z"))
                .room("Main Hall")
                .build();

        sessionB = Session.builder()
                .id(UUID.randomUUID())
                .sessionSlug("session-b")
                .eventCode("BATbern997")
                .startTime(Instant.parse("2025-06-15T18:15:00Z"))
                .endTime(Instant.parse("2025-06-15T19:00:00Z"))
                .room("Main Hall")
                .build();

        speaker = SpeakerPool.builder()
                .id(UUID.randomUUID())
                .username("john.doe")
                .build();
    }

    /**
     * Test: Detect room overlap conflict
     * AC9: Two sessions cannot use same room at overlapping times
     */
    @Test
    void should_detectRoomOverlap_when_sessionsOverlapInSameRoom() {
        // Given: Session A in Main Hall 18:00-18:45
        String eventCode = "BATbern997";
        Instant proposedStartTime = Instant.parse("2025-06-15T18:15:00Z");
        Instant proposedEndTime = Instant.parse("2025-06-15T19:00:00Z");
        String room = "Main Hall";

        // Mock simplified - actual implementation uses findByEventCode and filters
        when(sessionRepository.findByEventCode(eventCode))
                .thenReturn(List.of(sessionA, sessionB));

        // When: detectRoomOverlap(eventCode, startTime, endTime, room, excludeSessionSlug)
        Optional<SchedulingConflict> conflict = conflictDetectionService.detectRoomOverlap(
                eventCode, proposedStartTime, proposedEndTime, room, null
        );

        // Then: Returns conflict with type=room_overlap
        assertThat(conflict).isPresent();
        assertThat(conflict.get().getConflictType()).isEqualTo(ConflictType.ROOM_OVERLAP);
        assertThat(conflict.get().getSeverity()).isEqualTo(ConflictSeverity.ERROR);
        assertThat(conflict.get().getConflictingSessionIds()).contains(sessionA.getId());
    }

    /**
     * Test: Detect speaker double-booking
     * AC9: Same speaker cannot be in two sessions at overlapping times
     */
    @Test
    void should_detectSpeakerDoubleBooking_when_speakerInOverlappingSessions() {
        // Given: Speaker john.doe in session 18:00-18:45
        UUID speakerId = speaker.getId();
        Instant proposedStartTime = Instant.parse("2025-06-15T18:30:00Z");
        Instant proposedEndTime = Instant.parse("2025-06-15T19:15:00Z");

        when(speakerPoolRepository.findById(speakerId)).thenReturn(Optional.of(speaker));
        // Mock simplified - actual implementation uses findByEventId and filters
        when(sessionRepository.findByEventId(speaker.getEventId()))
                .thenReturn(List.of(sessionA));

        // When: detectSpeakerDoubleBooking(speakerId, startTime, endTime)
        Optional<SchedulingConflict> conflict = conflictDetectionService.detectSpeakerDoubleBooking(
                speakerId, proposedStartTime, proposedEndTime
        );

        // Then: Returns conflict with type=speaker_double_booked
        assertThat(conflict).isPresent();
        assertThat(conflict.get().getConflictType()).isEqualTo(ConflictType.SPEAKER_DOUBLE_BOOKED);
        assertThat(conflict.get().getSeverity()).isEqualTo(ConflictSeverity.ERROR);
        assertThat(conflict.get().getMessage()).contains("john.doe");
    }

    /**
     * Test: Detect speaker preference conflict
     * AC7-AC8: Session timing conflicts with speaker preferences
     */
    @Test
    void should_detectPreferenceConflict_when_sessionOutsidePreferredTime() {
        // Given: Speaker prefers morning sessions
        UUID speakerId = speaker.getId();
        Instant proposedStartTime = Instant.parse("2025-06-15T18:00:00Z"); // Evening

        SpeakerSlotPreference preference = SpeakerSlotPreference.builder()
                .speakerId(speakerId)
                .preferredTimeOfDay("morning")
                .build();

        when(speakerPoolRepository.findById(speakerId)).thenReturn(Optional.of(speaker));
        when(speakerSlotPreferenceRepository.findBySpeakerId(speakerId))
                .thenReturn(Optional.of(preference));

        // When: detectSpeakerPreferenceConflicts(speakerId, startTime)
        Optional<SchedulingConflict> conflict = conflictDetectionService.detectSpeakerPreferenceConflict(
                speakerId, proposedStartTime
        );

        // Then: Returns warning with type=speaker_unavailable, severity=warning
        assertThat(conflict).isPresent();
        assertThat(conflict.get().getConflictType()).isEqualTo(ConflictType.PREFERENCE_MISMATCH);
        assertThat(conflict.get().getSeverity()).isEqualTo(ConflictSeverity.WARNING);
        assertThat(conflict.get().getMessage()).contains("prefers morning");
    }

    /**
     * Test: Comprehensive conflict analysis
     * AC9: Analyze all conflicts across event sessions
     */
    @Test
    void should_analyzeAllConflicts_when_multipleConflictsExist() {
        // Given: Event with 3 room overlaps, 1 speaker double-booking
        String eventCode = "BATbern997";

        Session sessionC = Session.builder()
                .id(UUID.randomUUID())
                .sessionSlug("session-c")
                .eventCode(eventCode)
                .startTime(Instant.parse("2025-06-15T19:00:00Z"))
                .endTime(Instant.parse("2025-06-15T19:45:00Z"))
                .room("Main Hall")
                .build();

        when(sessionRepository.findByEventCode(eventCode))
                .thenReturn(List.of(sessionA, sessionB, sessionC));

        // When: analyzeAllConflicts(eventCode)
        ConflictAnalysisResponse result = conflictDetectionService.analyzeAllConflicts(eventCode);

        // Then: Returns ConflictAnalysisResponse with conflicts
        assertThat(result.isHasConflicts()).isTrue();
        assertThat(result.getConflicts()).isNotEmpty();
        assertThat(result.getConflicts()).anyMatch(c -> c.getConflictType() == ConflictType.ROOM_OVERLAP);
    }

    /**
     * Test: No conflicts detected
     */
    @Test
    void should_returnNoConflicts_when_noOverlapsExist() {
        // Given: Event with non-overlapping sessions
        String eventCode = "BATbern997";

        Session nonOverlappingSession = Session.builder()
                .id(UUID.randomUUID())
                .sessionSlug("session-separate")
                .eventCode(eventCode)
                .startTime(Instant.parse("2025-06-15T19:00:00Z"))
                .endTime(Instant.parse("2025-06-15T19:45:00Z"))
                .room("Room B")
                .build();

        when(sessionRepository.findByEventCode(eventCode))
                .thenReturn(List.of(sessionA, nonOverlappingSession));

        // When: analyzeAllConflicts(eventCode)
        ConflictAnalysisResponse result = conflictDetectionService.analyzeAllConflicts(eventCode);

        // Then: Returns hasConflicts=false, conflictCount=0
        assertThat(result.isHasConflicts()).isFalse();
        assertThat(result.getConflicts()).isEmpty();
    }
}
