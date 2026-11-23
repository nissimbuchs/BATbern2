package ch.batbern.agent.gate;

import ch.batbern.agent.log.ToolCallStatus;
import ch.batbern.agent.run.RunLimit;
import ch.batbern.agent.tools.OperationClass;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * A verdict the gate wrote to {@code agent_verdicts} (story 7, AD-7, AD-8).
 *
 * @param id             the verdict row
 * @param outcome        run, pending approval or refused
 * @param effectiveClass the class the call was treated as
 * @param reason         the rule that decided
 * @param approvalId     the PENDING {@code agent_approvals} row of a pending verdict; {@code null} otherwise
 * @param cap            the limit a refused call reached; {@code null} otherwise
 * @param cited          the source ids of the precedents the verdict cites
 */
public record Verdict(UUID id, VerdictOutcome outcome, OperationClass effectiveClass, GateReason reason,
                      UUID approvalId, RunLimit cap, List<String> cited) {

    public Verdict {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(effectiveClass, "effectiveClass");
        Objects.requireNonNull(reason, "reason");
        cited = List.copyOf(cited);
        if ((outcome == VerdictOutcome.PENDING_APPROVAL) != (approvalId != null)) {
            throw new IllegalArgumentException("A pending verdict, and only a pending one, has an approval");
        }
    }

    /** Whether the call may run now. */
    public boolean runs() {
        return outcome == VerdictOutcome.RUN;
    }

    /** The status a held call ends with in {@code agent_tool_calls}. */
    public ToolCallStatus heldStatus() {
        return switch (outcome) {
            case PENDING_APPROVAL -> ToolCallStatus.PENDING_APPROVAL;
            case REFUSED -> ToolCallStatus.REFUSED;
            case RUN -> throw new IllegalStateException("A call that runs is not held");
        };
    }
}
