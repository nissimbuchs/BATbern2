package ch.batbern.events.service;

import ch.batbern.shared.types.EventWorkflowState;

import java.util.List;

/**
 * The order of the event lifecycle, for "has the event reached state X" questions (#824).
 *
 * <p>Never compare {@link EventWorkflowState#ordinal()}: CANCELLED is the last constant but lies off
 * the path, so an ordinal comparison reads a cancelled event as past every state (it activated
 * every task when an event was cancelled). A cancelled event reaches CANCELLED only.
 */
public final class EventLifecycle {

    private static final List<EventWorkflowState> ORDER = List.of(
            EventWorkflowState.CREATED,
            EventWorkflowState.TOPIC_SELECTION,
            EventWorkflowState.SPEAKER_IDENTIFICATION,
            EventWorkflowState.SLOT_ASSIGNMENT,
            EventWorkflowState.AGENDA_PUBLISHED,
            EventWorkflowState.EVENT_LIVE,
            EventWorkflowState.EVENT_COMPLETED,
            EventWorkflowState.ARCHIVED);

    private EventLifecycle() {
    }

    /** Whether an event in {@code state} has reached {@code milestone}; false for a null state. */
    public static boolean reached(EventWorkflowState state, EventWorkflowState milestone) {
        if (state == null || milestone == null) {
            return false;
        }
        if (state == EventWorkflowState.CANCELLED || milestone == EventWorkflowState.CANCELLED) {
            return state == milestone;
        }
        return ORDER.indexOf(state) >= ORDER.indexOf(milestone);
    }
}
