package ch.batbern.agent.precedent;

import ch.batbern.agent.run.PrincipalKind;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A precedent from the decision log (story 6, AD-8): an organizer's approval, denial or undo of an
 * earlier call of the agent. Ids only: who decided stays in the log.
 *
 * @param kind          approval, denial or undo
 * @param sourceId      the citeable source: {@code agent_approvals:<id>} or {@code agent_undos:<id>}
 * @param toolCallId    the earlier call in {@code agent_tool_calls}
 * @param runId         the run of that call
 * @param toolName      the tool of that call
 * @param principalKind whom that run acted for; null for a run without a grant (it never matches)
 * @param at            when the organizer decided, or when the undo was performed
 * @param undoOutcome   PENDING, SUCCEEDED or FAILED for an undo; null otherwise
 */
public record DecisionPrecedent(PrecedentKind kind, String sourceId, UUID toolCallId, UUID runId, String toolName,
                                PrincipalKind principalKind, Instant at, String undoOutcome) {

    /** Source prefix of an approval or denial. */
    public static final String APPROVALS = "agent_approvals:";
    /** Source prefix of an undo. */
    public static final String UNDOS = "agent_undos:";

    public DecisionPrecedent {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(sourceId, "sourceId");
        Objects.requireNonNull(toolCallId, "toolCallId");
        Objects.requireNonNull(runId, "runId");
        Objects.requireNonNull(toolName, "toolName");
        Objects.requireNonNull(at, "at");
        if ((kind == PrecedentKind.UNDO) != (undoOutcome != null)) {
            throw new IllegalArgumentException("An undo, and only an undo, has an outcome");
        }
    }
}
