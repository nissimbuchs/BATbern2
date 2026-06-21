package ch.batbern.events.dto;

import lombok.Builder;

/**
 * Resolved agenda/slot config for an event (Story 15.2).
 *
 * <p>{@code source} indicates whether the values came from the shared event-type template
 * ({@code TEMPLATE}) or the per-event copy-on-edit override ({@code EVENT_OVERRIDE}).
 * Times are formatted as {@code HH:mm} strings (nullable).</p>
 */
@Builder
public record EventAgendaConfigResponse(
        String source,
        Integer minSlots,
        Integer maxSlots,
        Integer slotDuration,
        Boolean theoreticalSlotsAM,
        Integer breakSlots,
        Integer lunchSlots,
        Integer defaultCapacity,
        Integer moderationStartDuration,
        Integer moderationEndDuration,
        Integer breakDuration,
        Integer lunchDuration,
        Integer aperitifSlots,
        Integer aperitifDuration,
        String aperitifPosition,
        String typicalStartTime,
        String typicalEndTime) {
}
