package ch.batbern.events.mapper;

import ch.batbern.events.dto.TimetableSlot;
import ch.batbern.events.sessions.dto.generated.SessionResponse;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link TimetableMapper} (Phase 7 boundary mapper:
 * internal scheduling model -> generated wire DTO).
 */
class TimetableMapperTest {

    private final TimetableMapper mapper = new TimetableMapper();

    @Test
    void should_mapAllFields_when_slotConverted() {
        TimetableSlot internal = TimetableSlot.builder()
                .type(TimetableSlot.Type.SPEAKER_SLOT)
                .slotKey("SPEAKER_SLOT-3")
                .startTime(Instant.parse("2025-06-15T07:05:00Z"))
                .endTime(Instant.parse("2025-06-15T07:50:00Z"))
                .title(null)
                .slotIndex(3)
                .sessionSlug("structural-x")
                .assignedSessionSlug("speaker-y")
                .build();

        var wire = mapper.toWireSlot(internal);

        assertThat(wire.getType())
                .isEqualTo(ch.batbern.events.sessions.dto.generated.TimetableSlot.TypeEnum.SPEAKER_SLOT);
        assertThat(wire.getSlotKey()).isEqualTo("SPEAKER_SLOT-3");
        assertThat(wire.getStartTime()).isEqualTo(OffsetDateTime.parse("2025-06-15T07:05:00Z"));
        assertThat(wire.getEndTime()).isEqualTo(OffsetDateTime.parse("2025-06-15T07:50:00Z"));
        assertThat(wire.getTitle()).isNull();
        assertThat(wire.getSlotIndex()).isEqualTo(3);
        assertThat(wire.getSessionSlug()).isEqualTo("structural-x");
        assertThat(wire.getAssignedSessionSlug()).isEqualTo("speaker-y");
    }

    @Test
    void should_mapStructuralType_when_moderationSlot() {
        TimetableSlot internal = TimetableSlot.builder()
                .type(TimetableSlot.Type.MODERATION)
                .slotKey("MODERATION-1")
                .startTime(Instant.parse("2025-06-15T07:00:00Z"))
                .endTime(Instant.parse("2025-06-15T07:05:00Z"))
                .title("Moderation Start")
                .build();

        var wire = mapper.toWireSlot(internal);

        assertThat(wire.getType())
                .isEqualTo(ch.batbern.events.sessions.dto.generated.TimetableSlot.TypeEnum.MODERATION);
        assertThat(wire.getTitle()).isEqualTo("Moderation Start");
        // Instant at UTC serialises to the same ...Z wire form
        assertThat(wire.getStartTime().toInstant()).isEqualTo(Instant.parse("2025-06-15T07:00:00Z"));
    }

    @Test
    void should_returnNull_when_slotNull() {
        assertThat(mapper.toWireSlot(null)).isNull();
    }

    @Test
    void should_returnNull_when_responseNull() {
        assertThat(mapper.toWire(null)).isNull();
    }

    @Test
    void should_mapSlotsAndPassThroughUnassigned_when_responseConverted() {
        TimetableSlot slot = TimetableSlot.builder()
                .type(TimetableSlot.Type.LUNCH)
                .slotKey("LUNCH-1")
                .startTime(Instant.parse("2025-06-15T11:00:00Z"))
                .endTime(Instant.parse("2025-06-15T12:00:00Z"))
                .title("Mittagessen")
                .build();
        SessionResponse unassigned = new SessionResponse();
        unassigned.setSessionSlug("unassigned-talk");

        ch.batbern.events.dto.TimetableResponse internal =
                ch.batbern.events.dto.TimetableResponse.builder()
                        .slots(List.of(slot))
                        .unassignedSessions(List.of(unassigned))
                        .build();

        var wire = mapper.toWire(internal);

        assertThat(wire.getSlots()).hasSize(1);
        assertThat(wire.getSlots().get(0).getType())
                .isEqualTo(ch.batbern.events.sessions.dto.generated.TimetableSlot.TypeEnum.LUNCH);
        assertThat(wire.getUnassignedSessions()).hasSize(1);
        assertThat(wire.getUnassignedSessions().get(0).getSessionSlug()).isEqualTo("unassigned-talk");
    }

    @Test
    void should_handleNullCollections_when_responseHasNullSlots() {
        ch.batbern.events.dto.TimetableResponse internal =
                ch.batbern.events.dto.TimetableResponse.builder().build();

        var wire = mapper.toWire(internal);

        assertThat(wire.getSlots()).isNull();
        assertThat(wire.getUnassignedSessions()).isNull();
        // sanity: a UTC instant round-trips through the mapper unchanged
        assertThat(Instant.parse("2025-06-15T07:00:00Z").atOffset(ZoneOffset.UTC).toInstant())
                .isEqualTo(Instant.parse("2025-06-15T07:00:00Z"));
    }
}
