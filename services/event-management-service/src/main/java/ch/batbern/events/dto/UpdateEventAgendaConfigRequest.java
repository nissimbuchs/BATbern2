package ch.batbern.events.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Copy-on-edit payload for the per-event agenda config (Story 15.2).
 *
 * <p>Writes only the per-event {@code event_agenda_config} row — never the shared template.
 * The dialog pre-fills from the resolved config (GET) and submits the full knob set.</p>
 */
public record UpdateEventAgendaConfigRequest(
        @NotNull @Min(1) Integer minSlots,
        @NotNull @Min(1) Integer maxSlots,
        @NotNull @Min(15) Integer slotDuration,
        @NotNull Boolean theoreticalSlotsAM,
        @NotNull @Min(0) Integer breakSlots,
        @NotNull @Min(0) Integer lunchSlots,
        @NotNull @Min(1) Integer defaultCapacity,
        @NotNull @Min(1) Integer moderationStartDuration,
        @NotNull @Min(1) Integer moderationEndDuration,
        @NotNull @Min(1) Integer breakDuration,
        @NotNull @Min(1) Integer lunchDuration,
        @NotNull @Min(0) Integer aperitifSlots,
        @NotNull @Min(1) Integer aperitifDuration,
        @NotNull @Pattern(regexp = "start|end") String aperitifPosition,
        String typicalStartTime,
        String typicalEndTime) {
}
