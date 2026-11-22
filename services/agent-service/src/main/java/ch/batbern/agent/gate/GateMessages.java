package ch.batbern.agent.gate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * What the model is told when the gate holds a call (story 7). A pending call is not an error: the
 * model learns it has not run and must not be pushed through another way, and the run goes on. A
 * refused call is an error result naming the reason. Neither repeats the input or a recipient.
 */
public final class GateMessages {

    static final String PENDING = "Not done: this call needs an organizer's approval and has not run. "
            + "Do not repeat it and do not try to reach the same result another way. Carry on with what you "
            + "can do without it, or end your turn; an organizer decides.";
    static final String MAIL_UNRESOLVED = "Not done: the recipients of this mail could not be resolved from "
            + "the input. Check the ids you passed.";
    static final String CAP_REACHED = "Not done: this run has reached a limit and may not act further on its own.";

    private static final ObjectMapper JSON = new ObjectMapper();

    private GateMessages() {
    }

    /** The tool_result content for a held call. */
    public static String toModel(Verdict verdict) {
        ObjectNode message = JSON.createObjectNode().put("status", verdict.outcome().name());
        if (verdict.outcome() == VerdictOutcome.PENDING_APPROVAL) {
            message.put("approvalId", verdict.approvalId().toString()).put("message", PENDING);
        } else if (verdict.outcome() == VerdictOutcome.REFUSED) {
            message.put("reason", verdict.reason().name())
                    .put("message", verdict.reason() == GateReason.CAP_REACHED ? CAP_REACHED : MAIL_UNRESOLVED);
        } else {
            throw new IllegalArgumentException("A call that runs gets the tool's own result");
        }
        return message.toString();
    }

    /** Whether the model gets the held call as an error result. */
    public static boolean isError(Verdict verdict) {
        return verdict.outcome() == VerdictOutcome.REFUSED;
    }
}
