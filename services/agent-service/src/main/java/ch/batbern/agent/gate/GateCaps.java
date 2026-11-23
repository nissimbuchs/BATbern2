package ch.batbern.agent.gate;

import ch.batbern.agent.run.RunLimit;
import ch.batbern.agent.tools.OperationClass;

import java.util.Optional;
import java.util.UUID;

/**
 * The caps a call the gate would let run alone must stay within (AD-7, Operational envelope "Limits").
 * Asked for every call the rules let run ({@code reversible}, {@code reply}, or a {@code binding}
 * call that cleared the threshold), inside the gate's transaction, after the rules and before the
 * verdict is written. A reached cap refuses the call ({@link GateReason#CAP_REACHED}, the limit in
 * {@code inputs.cap}), and the loop then stops the run (story 8). The one bean is {@link ReplyCap};
 * steps, run tokens and the daily budget are checked by the loop before every model call.
 */
@FunctionalInterface
public interface GateCaps {

    /** No caps: what the gate uses when no {@code GateCaps} bean exists. */
    GateCaps NONE = (runId, effectiveClass, mail) -> Optional.empty();

    /**
     * The limit {@code runId} would exceed by this call, or empty when it may run.
     *
     * @param effectiveClass {@code reversible}, {@code reply} or {@code binding}
     * @param mail           whether the call sends a mail
     */
    Optional<RunLimit> reached(UUID runId, OperationClass effectiveClass, boolean mail);
}
