package ch.batbern.events.entity;

import java.time.LocalTime;

/**
 * Read view of the agenda/slot knobs consumed by
 * {@link ch.batbern.events.service.TimetableService#computeTimeline}.
 *
 * <p>Implemented by BOTH the shared template ({@link EventTypeConfiguration}) and the
 * per-event copy-on-edit override ({@link EventAgendaConfig}), so the timeline algorithm
 * is decoupled from where the knobs came from. The
 * {@link ch.batbern.events.service.AgendaConfigResolver} returns the per-event override
 * when present, else the shared template (Story 15.2).</p>
 */
public interface AgendaConfig {

    LocalTime getTypicalStartTime();

    LocalTime getTypicalEndTime();

    Integer getMinSlots();

    Integer getMaxSlots();

    Integer getDefaultCapacity();

    Integer getSlotDuration();

    Integer getBreakSlots();

    Integer getLunchSlots();

    Integer getModerationStartDuration();

    Integer getModerationEndDuration();

    Integer getBreakDuration();

    Integer getLunchDuration();

    Boolean getTheoreticalSlotsAM();

    /** Apéro on/off count (0 = off, ≥1 = on). */
    Integer getAperitifSlots();

    /** Apéro duration in minutes. */
    Integer getAperitifDuration();

    /** Apéro placement: {@code "start"} (after moderation-start) or {@code "end"} (after moderation-end). */
    String getAperitifPosition();
}
