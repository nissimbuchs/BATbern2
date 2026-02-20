package ch.batbern.events.service;

import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Session;
import ch.batbern.events.dto.TimetableResponse;
import ch.batbern.events.dto.TimetableSlot;
import ch.batbern.events.dto.generated.EventType;
import ch.batbern.events.entity.EventTypeConfiguration;
import ch.batbern.events.exception.EventNotFoundException;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.EventTypeRepository;
import ch.batbern.events.repository.SessionRepository;
import ch.batbern.shared.exception.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static ch.batbern.events.dto.TimetableSlot.Type.BREAK;
import static ch.batbern.events.dto.TimetableSlot.Type.LUNCH;
import static ch.batbern.events.dto.TimetableSlot.Type.MODERATION;
import static ch.batbern.events.dto.TimetableSlot.Type.SPEAKER_SLOT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;

import static org.mockito.Mockito.when;

/**
 * Unit tests for TimetableService.
 *
 * computeTimeline() tests: pure algorithm, no mocks needed.
 * getTimetable() tests: mocked repositories for DB interaction.
 */
@ExtendWith(MockitoExtension.class)
class TimetableServiceTest {

    @Mock
    private EventRepository eventRepository;

    @Mock
    private EventTypeRepository eventTypeRepository;

    @Mock
    private SessionRepository sessionRepository;

    @Mock
    private SessionService sessionService;

    private TimetableService timetableService;

    private EventTypeConfiguration fullDayConfig;
    private EventTypeConfiguration linearConfig;

    private static final LocalDate EVENT_DATE = LocalDate.of(2025, 6, 15);

    @BeforeEach
    void setUp() {
        timetableService = new TimetableService(
                eventRepository, eventTypeRepository, sessionRepository, sessionService);

        // FULL_DAY: 8 slots, AM/PM split, 2 breaks, 1 lunch
        fullDayConfig = EventTypeConfiguration.builder()
                .id(UUID.randomUUID())
                .type(EventType.FULL_DAY)
                .minSlots(6)
                .maxSlots(8)
                .slotDuration(45)
                .theoreticalSlotsAM(true)
                .breakSlots(2)
                .lunchSlots(1)
                .defaultCapacity(300)
                .typicalStartTime(LocalTime.of(9, 0))
                .typicalEndTime(LocalTime.of(17, 0))
                .moderationStartDuration(5)
                .moderationEndDuration(5)
                .breakDuration(20)
                .lunchDuration(60)
                .build();

        // Linear: 8 slots, no AM/PM split (lunchSlots=0), 1 break
        linearConfig = EventTypeConfiguration.builder()
                .id(UUID.randomUUID())
                .type(EventType.FULL_DAY)
                .minSlots(6)
                .maxSlots(8)
                .slotDuration(45)
                .theoreticalSlotsAM(true) // theoreticalSlotsAM=true but lunchSlots=0 → linear path
                .breakSlots(1)
                .lunchSlots(0)
                .defaultCapacity(300)
                .typicalStartTime(LocalTime.of(9, 0))
                .typicalEndTime(LocalTime.of(17, 0))
                .moderationStartDuration(5)
                .moderationEndDuration(5)
                .breakDuration(20)
                .lunchDuration(60)
                .build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // computeTimeline() — pure algorithm tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("FULL_DAY AM/PM split: total slot count = 2 MOD + 2 BREAK + 1 LUNCH + 8 SPEAKER = 13")
    void computeTimeline_fullDay_amPmSplit_correctSlotCount() {
        List<TimetableSlot> slots = timetableService.computeTimeline(fullDayConfig, EVENT_DATE);

        assertThat(slots).hasSize(13);
        assertThat(countByType(slots, MODERATION)).isEqualTo(2);
        assertThat(countByType(slots, BREAK)).isEqualTo(2);
        assertThat(countByType(slots, LUNCH)).isEqualTo(1);
        assertThat(countByType(slots, SPEAKER_SLOT)).isEqualTo(8);
    }

    @Test
    @DisplayName("FULL_DAY linear (lunchSlots=0): 2 MOD + 1 BREAK + 8 SPEAKER = 11 slots")
    void computeTimeline_linear_correctSlotCount() {
        List<TimetableSlot> slots = timetableService.computeTimeline(linearConfig, EVENT_DATE);

        assertThat(slots).hasSize(11);
        assertThat(countByType(slots, MODERATION)).isEqualTo(2);
        assertThat(countByType(slots, BREAK)).isEqualTo(1);
        assertThat(countByType(slots, LUNCH)).isEqualTo(0);
        assertThat(countByType(slots, SPEAKER_SLOT)).isEqualTo(8);
    }

    @Test
    @DisplayName("First slot is MODERATION, last structural is MODERATION")
    void computeTimeline_firstAndLastAreModeration() {
        List<TimetableSlot> slots = timetableService.computeTimeline(fullDayConfig, EVENT_DATE);

        assertThat(slots.get(0).getType()).isEqualTo(MODERATION);
        assertThat(slots.get(0).getTitle()).isEqualTo("Moderation Start");
        assertThat(slots.get(slots.size() - 1).getType()).isEqualTo(MODERATION);
        assertThat(slots.get(slots.size() - 1).getTitle()).isEqualTo("Moderation End");
    }

    @Test
    @DisplayName("Speaker slot indices are 1-based and contiguous across AM+PM (not reset for PM)")
    void computeTimeline_speakerSlotIndicesAreContiguous() {
        List<TimetableSlot> slots = timetableService.computeTimeline(fullDayConfig, EVENT_DATE);

        List<Integer> indices = slots.stream()
                .filter(s -> s.getType() == SPEAKER_SLOT)
                .map(TimetableSlot::getSlotIndex)
                .toList();

        assertThat(indices).containsExactly(1, 2, 3, 4, 5, 6, 7, 8);
    }

    @Test
    @DisplayName("MODERATION slots have null slotIndex; SPEAKER_SLOT has null sessionSlug by default")
    void computeTimeline_slotIndexNullForStructural() {
        List<TimetableSlot> slots = timetableService.computeTimeline(fullDayConfig, EVENT_DATE);

        slots.stream().filter(s -> s.getType() == MODERATION || s.getType() == BREAK || s.getType() == LUNCH)
                .forEach(s -> assertThat(s.getSlotIndex()).isNull());

        slots.stream().filter(s -> s.getType() == SPEAKER_SLOT)
                .forEach(s -> {
                    assertThat(s.getSlotIndex()).isNotNull();
                    assertThat(s.getSessionSlug()).isNull();
                    assertThat(s.getAssignedSessionSlug()).isNull();
                });
    }

    @Test
    @DisplayName("All slots are in chronological order (each start >= previous start)")
    void computeTimeline_slotsAreChronological() {
        List<TimetableSlot> slots = timetableService.computeTimeline(fullDayConfig, EVENT_DATE);

        for (int i = 1; i < slots.size(); i++) {
            assertThat(slots.get(i).getStartTime())
                    .isAfterOrEqualTo(slots.get(i - 1).getStartTime());
        }
    }

    @Test
    @DisplayName("FULL_DAY AM/PM: LUNCH appears between AM speaker slots and PM speaker slots")
    void computeTimeline_lunchSeparatesAmAndPm() {
        List<TimetableSlot> slots = timetableService.computeTimeline(fullDayConfig, EVENT_DATE);

        // Find LUNCH position
        int lunchIdx = -1;
        for (int i = 0; i < slots.size(); i++) {
            if (slots.get(i).getType() == LUNCH) {
                lunchIdx = i;
                break;
            }
        }
        assertThat(lunchIdx).isPositive();

        // Speaker slots before lunch (AM)
        long amSpeakerSlots = slots.subList(0, lunchIdx).stream()
                .filter(s -> s.getType() == SPEAKER_SLOT).count();
        // Speaker slots after lunch (PM)
        long pmSpeakerSlots = slots.subList(lunchIdx + 1, slots.size()).stream()
                .filter(s -> s.getType() == SPEAKER_SLOT).count();

        assertThat(amSpeakerSlots).isEqualTo(4); // ceil(8/2)=4
        assertThat(pmSpeakerSlots).isEqualTo(4); // 8-4=4
    }

    @Test
    @DisplayName("AM break placed after ceil(amSlots/2) speaker slots")
    void computeTimeline_amBreakPlacedCorrectly() {
        List<TimetableSlot> slots = timetableService.computeTimeline(fullDayConfig, EVENT_DATE);

        // amSlots=4, breakAfter=ceil(4/2)=2 → break after 2nd AM speaker slot
        // Sequence should be: MOD | SPEAKER1 | SPEAKER2 | BREAK | SPEAKER3 | SPEAKER4 | LUNCH | ...
        assertThat(slots.get(0).getType()).isEqualTo(MODERATION);
        assertThat(slots.get(1).getType()).isEqualTo(SPEAKER_SLOT);
        assertThat(slots.get(2).getType()).isEqualTo(SPEAKER_SLOT);
        assertThat(slots.get(3).getType()).isEqualTo(BREAK);
        assertThat(slots.get(4).getType()).isEqualTo(SPEAKER_SLOT);
        assertThat(slots.get(5).getType()).isEqualTo(SPEAKER_SLOT);
        assertThat(slots.get(6).getType()).isEqualTo(LUNCH);
    }

    @Test
    @DisplayName("No BREAK emitted when breakSlots=0")
    void computeTimeline_noBreakWhenBreakSlotsZero() {
        EventTypeConfiguration noBreakConfig = EventTypeConfiguration.builder()
                .type(EventType.FULL_DAY)
                .maxSlots(4)
                .slotDuration(45)
                .theoreticalSlotsAM(false)
                .breakSlots(0)
                .lunchSlots(0)
                .moderationStartDuration(5)
                .moderationEndDuration(5)
                .breakDuration(20)
                .lunchDuration(60)
                .typicalStartTime(LocalTime.of(9, 0))
                .build();

        List<TimetableSlot> slots = timetableService.computeTimeline(noBreakConfig, EVENT_DATE);

        assertThat(countByType(slots, BREAK)).isEqualTo(0);
        assertThat(countByType(slots, SPEAKER_SLOT)).isEqualTo(4);
        assertThat(slots).hasSize(6); // 2 MOD + 4 SPEAKER
    }

    @Test
    @DisplayName("Null config values use safe defaults (slotDuration=45)")
    void computeTimeline_nullValuesUseDefaults() {
        EventTypeConfiguration minimalConfig = EventTypeConfiguration.builder()
                .type(EventType.FULL_DAY)
                .maxSlots(2)
                .slotDuration(null)   // should default to 45
                .theoreticalSlotsAM(null) // should default to false
                .breakSlots(null)     // should default to 0
                .lunchSlots(null)     // should default to 0
                .moderationStartDuration(5)
                .moderationEndDuration(5)
                .breakDuration(20)
                .lunchDuration(60)
                .typicalStartTime(LocalTime.of(9, 0))
                .build();

        List<TimetableSlot> slots = timetableService.computeTimeline(minimalConfig, EVENT_DATE);

        // 2 MOD + 2 SPEAKER = 4 slots, no breaks, no lunch
        assertThat(slots).hasSize(4);
        assertThat(countByType(slots, SPEAKER_SLOT)).isEqualTo(2);

        // Slot duration defaults to 45 min
        TimetableSlot firstSpeaker = slots.stream().filter(s -> s.getType() == SPEAKER_SLOT).findFirst().orElseThrow();
        long durationMinutes = java.time.temporal.ChronoUnit.MINUTES.between(
                firstSpeaker.getStartTime(), firstSpeaker.getEndTime());
        assertThat(durationMinutes).isEqualTo(45);
    }

    @Test
    @DisplayName("Moderation start time on event date in Europe/Zurich zone")
    void computeTimeline_moderationStartsAtTypicalStartTime() {
        List<TimetableSlot> slots = timetableService.computeTimeline(fullDayConfig, EVENT_DATE);

        TimetableSlot modStart = slots.get(0);
        // 09:00 Europe/Zurich on 2025-06-15 = 07:00 UTC (CEST = UTC+2)
        assertThat(modStart.getStartTime().toString()).contains("2025-06-15T07:00:00Z");
        // endTime = start + 5 min
        assertThat(modStart.getEndTime().toString()).contains("2025-06-15T07:05:00Z");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // getTimetable() — mocked repository tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getTimetable: structural slots have null sessionSlug when no DB sessions exist")
    void getTimetable_noDbSessions_structuralSlugsNull() {
        Event event = buildEvent("BATbern142", "2025-06-15T07:00:00Z");
        when(eventRepository.findByEventCode("BATbern142")).thenReturn(Optional.of(event));
        when(eventTypeRepository.findByType(EventType.FULL_DAY)).thenReturn(Optional.of(fullDayConfig));
        when(sessionRepository.findByEventId(event.getId())).thenReturn(List.of());

        TimetableResponse response = timetableService.getTimetable("BATbern142");

        assertThat(response.getSlots()).isNotEmpty();
        response.getSlots().stream()
                .filter(s -> s.getType() != SPEAKER_SLOT)
                .forEach(s -> assertThat(s.getSessionSlug()).isNull());
        assertThat(response.getUnassignedSessions()).isEmpty();
    }

    @Test
    @DisplayName("getTimetable: structural DB sessions matched by startTime → sessionSlug populated")
    void getTimetable_withDbSessions_structuralSlugsPopulated() {
        Event event = buildEvent("BATbern142", "2025-06-15T07:00:00Z");
        when(eventRepository.findByEventCode("BATbern142")).thenReturn(Optional.of(event));
        when(eventTypeRepository.findByType(EventType.FULL_DAY)).thenReturn(Optional.of(fullDayConfig));

        // Create a moderation session at the expected start time (09:00 Zurich = 07:00 UTC)
        Session modSession = Session.builder()
                .id(UUID.randomUUID())
                .eventId(event.getId())
                .sessionSlug("moderation-start-abc")
                .sessionType("moderation")
                .startTime(Instant.parse("2025-06-15T07:00:00Z"))
                .endTime(Instant.parse("2025-06-15T07:05:00Z"))
                .build();

        when(sessionRepository.findByEventId(event.getId())).thenReturn(List.of(modSession));

        TimetableResponse response = timetableService.getTimetable("BATbern142");

        TimetableSlot firstSlot = response.getSlots().get(0);
        assertThat(firstSlot.getType()).isEqualTo(MODERATION);
        assertThat(firstSlot.getSessionSlug()).isEqualTo("moderation-start-abc");
    }

    @Test
    @DisplayName("getTimetable: assigned speaker session sets assignedSessionSlug on matching SPEAKER_SLOT")
    void getTimetable_assignedSpeakerSession_setsAssignedSessionSlug() {
        Event event = buildEvent("BATbern142", "2025-06-15T07:00:00Z");
        when(eventRepository.findByEventCode("BATbern142")).thenReturn(Optional.of(event));
        when(eventTypeRepository.findByType(EventType.FULL_DAY)).thenReturn(Optional.of(fullDayConfig));

        // First SPEAKER_SLOT starts at 09:05 Zurich = 07:05 UTC (after 5 min moderation start)
        Session speakerSession = Session.builder()
                .id(UUID.randomUUID())
                .eventId(event.getId())
                .sessionSlug("speaker-xyz")
                .sessionType("presentation")
                .startTime(Instant.parse("2025-06-15T07:05:00Z"))
                .endTime(Instant.parse("2025-06-15T07:50:00Z"))
                .build();

        when(sessionRepository.findByEventId(event.getId())).thenReturn(List.of(speakerSession));

        TimetableResponse response = timetableService.getTimetable("BATbern142");

        TimetableSlot matchedSlot = response.getSlots().stream()
                .filter(s -> s.getType() == SPEAKER_SLOT && s.getStartTime().equals(Instant.parse("2025-06-15T07:05:00Z")))
                .findFirst()
                .orElseThrow();

        assertThat(matchedSlot.getAssignedSessionSlug()).isEqualTo("speaker-xyz");
    }

    @Test
    @DisplayName("getTimetable: unassigned speaker sessions appear in unassignedSessions list")
    void getTimetable_unassignedSessions_populatedInResponse() {
        Event event = buildEvent("BATbern142", "2025-06-15T07:00:00Z");
        when(eventRepository.findByEventCode("BATbern142")).thenReturn(Optional.of(event));
        when(eventTypeRepository.findByType(EventType.FULL_DAY)).thenReturn(Optional.of(fullDayConfig));

        Session unassigned = Session.builder()
                .id(UUID.randomUUID())
                .eventId(event.getId())
                .sessionSlug("unassigned-talk")
                .sessionType("presentation")
                .startTime(null)
                .endTime(null)
                .build();

        when(sessionRepository.findByEventId(event.getId())).thenReturn(List.of(unassigned));
        when(sessionService.toSessionResponse(any(Session.class), any(String.class)))
                .thenAnswer(inv -> {
                    Session s = inv.getArgument(0);
                    ch.batbern.events.dto.SessionResponse r = new ch.batbern.events.dto.SessionResponse();
                    r.setSessionSlug(s.getSessionSlug());
                    return r;
                });

        TimetableResponse response = timetableService.getTimetable("BATbern142");

        assertThat(response.getUnassignedSessions()).hasSize(1);
        assertThat(response.getUnassignedSessions().get(0).getSessionSlug()).isEqualTo("unassigned-talk");
    }

    @Test
    @DisplayName("getTimetable: 404 when event not found")
    void getTimetable_eventNotFound_throws404() {
        when(eventRepository.findByEventCode("INVALID")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> timetableService.getTimetable("INVALID"))
                .isInstanceOf(EventNotFoundException.class);
    }

    @Test
    @DisplayName("getTimetable: NotFoundException when event has no event type")
    void getTimetable_noEventType_throwsNotFoundException() {
        Event event = buildEvent("BATbern142", "2025-06-15T07:00:00Z");
        event.setEventType(null);
        when(eventRepository.findByEventCode("BATbern142")).thenReturn(Optional.of(event));

        assertThatThrownBy(() -> timetableService.getTimetable("BATbern142"))
                .isInstanceOf(NotFoundException.class);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private long countByType(List<TimetableSlot> slots, TimetableSlot.Type type) {
        return slots.stream().filter(s -> s.getType() == type).count();
    }

    private Event buildEvent(String eventCode, String dateIso) {
        Event event = new Event();
        event.setId(UUID.randomUUID());
        event.setEventCode(eventCode);
        event.setEventType(EventType.FULL_DAY);
        event.setDate(Instant.parse(dateIso));
        return event;
    }
}
