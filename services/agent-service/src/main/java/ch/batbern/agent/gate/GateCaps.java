package ch.batbern.agent.gate;

import ch.batbern.agent.tools.OperationClass;

import java.util.Optional;
import java.util.UUID;

/**
 * The caps a call the gate would let run alone must stay within (AD-7, Operational envelope). The
 * hook for story 8 (per-run steps, tokens and {@code reply} mails, the daily budget): asked for
 * every call the rules let run ({@code reversible}, {@code reply}, or a {@code binding} call that
 * cleared the threshold), inside the gate's transaction, after the rules and before the verdict is
 * written. A reached cap refuses the call
 * ({@link GateReason#CAP_REACHED}, the cap's name in {@code inputs.cap}).
 */
@FunctionalInterface
public interface GateCaps {

    /** No caps: what the gate uses while no {@code GateCaps} bean exists (until story 8). */
    GateCaps NONE = (runId, effectiveClass, mail) -> Optional.empty();

    /**
     * The name of the cap {@code runId} would exceed by this call, or empty when it may run.
     *
     * @param effectiveClass {@code reversible}, {@code reply} or {@code binding}
     * @param mail           whether the call sends a mail
     */
    Optional<String> reached(UUID runId, OperationClass effectiveClass, boolean mail);
}
