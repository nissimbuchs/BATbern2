package ch.batbern.agent.precedent;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The precedents of one proposed call (story 6, AD-8), for the gate to weigh and cite (story 7).
 * Decision precedents are newest first; past events in the order the API answered (newest first).
 *
 * @param approvals     matching approvals
 * @param denials       matching denials
 * @param undos         matching undos
 * @param pastEvents    the past events the tool asked for
 * @param historyStatus whether past events were asked for and could be read
 */
public record Precedents(List<DecisionPrecedent> approvals, List<DecisionPrecedent> denials,
                         List<DecisionPrecedent> undos, List<PastEventPrecedent> pastEvents,
                         HistoryStatus historyStatus) {

    /** Whether the past events behind a call were read. */
    public enum HistoryStatus {
        /** The tool declares no past-event query; nothing was read. */
        NOT_ASKED,
        /** The past events were read; {@link #pastEvents()} is what the API answered (possibly none). */
        READ,
        /** The API answered an error; {@link #pastEvents()} is empty and says nothing. */
        UNAVAILABLE
    }

    public Precedents {
        approvals = List.copyOf(approvals);
        denials = List.copyOf(denials);
        undos = List.copyOf(undos);
        pastEvents = List.copyOf(pastEvents);
        Objects.requireNonNull(historyStatus, "historyStatus");
        if (historyStatus != HistoryStatus.READ && !pastEvents.isEmpty()) {
            throw new IllegalArgumentException("Past events exist only when they were read");
        }
    }

    /** Every source id, approvals first, then denials, undos and past events: what a verdict cites. */
    public List<String> sourceIds() {
        List<String> ids = new ArrayList<>();
        approvals.forEach(precedent -> ids.add(precedent.sourceId()));
        denials.forEach(precedent -> ids.add(precedent.sourceId()));
        undos.forEach(precedent -> ids.add(precedent.sourceId()));
        pastEvents.forEach(precedent -> ids.add(precedent.sourceId()));
        return List.copyOf(ids);
    }
}
