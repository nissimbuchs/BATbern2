package ch.batbern.events.service.slotassignment;

import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Session;
import ch.batbern.events.dto.TimetableResponse;
import ch.batbern.events.dto.TimetableSlot;
import ch.batbern.events.exception.AgendaFullException;
import ch.batbern.events.exception.InvalidSlotAssignmentException;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.SessionRepository;
import ch.batbern.events.service.TimetableService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static ch.batbern.events.dto.TimetableSlot.Type.MODERATION;
import static ch.batbern.events.dto.TimetableSlot.Type.SPEAKER_SLOT;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SlotReorderService} (Story 15.3): ASSIGN / INSERT / SWAP by stable
 * slotKey, reflow persistence, and the agenda-full overflow guard.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SlotReorderServiceTest {

    @Mock
    private EventRepository eventRepository;

    @Mock
    private SessionRepository sessionRepository;

    @Mock
    private TimetableService timetableService;

    @Mock
    private SessionTimingService sessionTimingService;

    private SlotReorderService service;

    private static final String EVENT_CODE = "BATbern142";
    private final UUID eventId = UUID.randomUUID();

    private static final Instant T1 = Instant.parse("2025-06-15T07:05:00Z");
    private static final Instant T1_END = Instant.parse("2025-06-15T07:50:00Z");
    private static final Instant T2 = Instant.parse("2025-06-15T07:50:00Z");
    private static final Instant T2_END = Instant.parse("2025-06-15T08:35:00Z");
    private static final Instant T3 = Instant.parse("2025-06-15T08:35:00Z");
    private static final Instant T3_END = Instant.parse("2025-06-15T09:20:00Z");
    private static final Instant T4 = Instant.parse("2025-06-15T09:20:00Z");
    private static final Instant T4_END = Instant.parse("2025-06-15T10:05:00Z");

    @BeforeEach
    void setUp() {
        service = new SlotReorderService(
                eventRepository, sessionRepository, timetableService, sessionTimingService);

        Event event = new Event();
        event.setId(eventId);
        event.setEventCode(EVENT_CODE);
        when(eventRepository.findByEventCode(EVENT_CODE)).thenReturn(Optional.of(event));

        // Any session slug resolves to a session belonging to the event (room = Main Hall).
        when(sessionRepository.findBySessionSlug(any())).thenAnswer(inv -> {
            String slug = inv.getArgument(0);
            Session s = Session.builder()
                    .id(UUID.randomUUID())
                    .eventId(eventId)
                    .sessionSlug(slug)
                    .sessionType("presentation")
                    .room("Main Hall")
                    .build();
            return Optional.of(s);
        });
    }

    /** Build a timetable: MODERATION-1 + 4 speaker slots with the given occupants (null = free). */
    private void mockTimetable(String s1, String s2, String s3, String s4) {
        List<TimetableSlot> slots = new ArrayList<>();
        slots.add(TimetableSlot.builder().type(MODERATION).slotKey("MODERATION-1")
                .startTime(Instant.parse("2025-06-15T07:00:00Z"))
                .endTime(T1).title("Moderation Start").build());
        slots.add(speakerSlot("SPEAKER_SLOT-1", T1, T1_END, s1));
        slots.add(speakerSlot("SPEAKER_SLOT-2", T2, T2_END, s2));
        slots.add(speakerSlot("SPEAKER_SLOT-3", T3, T3_END, s3));
        slots.add(speakerSlot("SPEAKER_SLOT-4", T4, T4_END, s4));
        TimetableResponse tt = TimetableResponse.builder().slots(slots).unassignedSessions(List.of()).build();
        when(timetableService.getTimetable(EVENT_CODE)).thenReturn(tt);
    }

    private TimetableSlot speakerSlot(String key, Instant start, Instant end, String occupant) {
        return TimetableSlot.builder()
                .type(SPEAKER_SLOT).slotKey(key).slotIndex(Integer.parseInt(key.substring(key.lastIndexOf('-') + 1)))
                .startTime(start).endTime(end).assignedSessionSlug(occupant).build();
    }

    @Test
    @DisplayName("ASSIGN onto an empty slot retimes only the dragged session (AC1 base case)")
    void assign_emptySlot_retimesDragged() {
        mockTimetable("a", "b", null, null);

        service.assignToSlot(EVENT_CODE, "c", "SPEAKER_SLOT-3", SlotAssignmentMode.ASSIGN, "organizer");

        verify(sessionTimingService).assignTiming(eq("c"), eq(T3), eq(T3_END), eq("Main Hall"),
                any(), eq("organizer"));
        verify(sessionTimingService, never()).assignTiming(eq("a"), any(), any(), any(), any(), any());
        verify(sessionTimingService, never()).assignTiming(eq("b"), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("ASSIGN onto an occupied slot is rejected (use INSERT or SWAP)")
    void assign_occupiedSlot_rejected() {
        mockTimetable("a", "b", null, null);

        assertThatThrownBy(() ->
                service.assignToSlot(EVENT_CODE, "c", "SPEAKER_SLOT-1", SlotAssignmentMode.ASSIGN, "organizer"))
                .isInstanceOf(InvalidSlotAssignmentException.class);
    }

    @Test
    @DisplayName("INSERT from pool shifts later sessions down one slot and reflows their times (AC1)")
    void insert_shiftsLaterSessionsAndReflows() {
        mockTimetable("a", "b", null, null);

        // Insert "c" at SPEAKER_SLOT-1 → [c, a, b, _]
        service.assignToSlot(EVENT_CODE, "c", "SPEAKER_SLOT-1", SlotAssignmentMode.INSERT, "organizer");

        verify(sessionTimingService).assignTiming(eq("c"), eq(T1), eq(T1_END), any(), any(), any());
        verify(sessionTimingService).assignTiming(eq("a"), eq(T2), eq(T2_END), any(), any(), any());
        verify(sessionTimingService).assignTiming(eq("b"), eq(T3), eq(T3_END), any(), any(), any());
    }

    @Test
    @DisplayName("INSERT into a full agenda throws AgendaFullException (AC4 / 409)")
    void insert_fullAgenda_throwsAgendaFull() {
        mockTimetable("a", "b", "c", "d");

        assertThatThrownBy(() ->
                service.assignToSlot(EVENT_CODE, "e", "SPEAKER_SLOT-1", SlotAssignmentMode.INSERT, "organizer"))
                .isInstanceOf(AgendaFullException.class);

        verify(sessionTimingService, never()).assignTiming(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("SWAP exchanges the dragged and occupant sessions' slots (AC2)")
    void swap_exchangesAssignments() {
        mockTimetable("a", "b", null, null);

        // Swap "a" (slot-1) onto "b" (slot-2): a→T2, b→T1
        service.assignToSlot(EVENT_CODE, "a", "SPEAKER_SLOT-2", SlotAssignmentMode.SWAP, "organizer");

        verify(sessionTimingService).assignTiming(eq("a"), eq(T2), eq(T2_END), any(), any(), any());
        verify(sessionTimingService).assignTiming(eq("b"), eq(T1), eq(T1_END), any(), any(), any());
    }

    @Test
    @DisplayName("SWAP onto an empty slot is rejected (use ASSIGN)")
    void swap_emptySlot_rejected() {
        mockTimetable("a", "b", null, null);

        assertThatThrownBy(() ->
                service.assignToSlot(EVENT_CODE, "a", "SPEAKER_SLOT-3", SlotAssignmentMode.SWAP, "organizer"))
                .isInstanceOf(InvalidSlotAssignmentException.class);
    }

    @Test
    @DisplayName("Unknown target slotKey is rejected with 400")
    void unknownTargetSlotKey_rejected() {
        mockTimetable("a", "b", null, null);

        assertThatThrownBy(() ->
                service.assignToSlot(EVENT_CODE, "c", "SPEAKER_SLOT-99", SlotAssignmentMode.ASSIGN, "organizer"))
                .isInstanceOf(InvalidSlotAssignmentException.class);
    }

    @Test
    @DisplayName("INSERT of an already-assigned earlier session moving later reflows the block between")
    void insert_reassignExisting_movesAndReflows() {
        mockTimetable("a", "b", "c", null);

        // Move "a" (slot-1) to insert at SPEAKER_SLOT-3 → detach a → [_, b, c, _] →
        // free slot is index 0 (>= target 2? no). After detach, occupants=[null,b,c,null].
        // first free >= target(2) is index 3 → shift [2,3): slot3←slot2(c) → [null,b,c→,..]; place a@2.
        // Result: [null, b, a, c]
        service.assignToSlot(EVENT_CODE, "a", "SPEAKER_SLOT-3", SlotAssignmentMode.INSERT, "organizer");

        // a moves to slot-3 time, c shifts to slot-4 time; b unchanged (stays slot-2).
        verify(sessionTimingService).assignTiming(eq("a"), eq(T3), eq(T3_END), any(), any(), any());
        verify(sessionTimingService).assignTiming(eq("c"), eq(T4), eq(T4_END), any(), any(), any());
        verify(sessionTimingService, never()).assignTiming(eq("b"), any(), any(), any(), any(), any());
    }
}
