package ch.batbern.agent.precedent;

import ch.batbern.agent.run.PrincipalKind;
import ch.batbern.agent.tools.PastEventQuery;

import java.util.Optional;

/**
 * The one place that says which precedents match a proposed call (story 6, AD-8; epic Assumption,
 * 2026-11-22). Tighten the rule here, nowhere else:
 * <ul>
 *   <li><b>Decision log.</b> An approval, denial or undo matches when its call used the same tool
 *       and its run acted for the same principal kind. A run without a principal kind (no grant)
 *       matches nothing.</li>
 *   <li><b>Past events.</b> The past events the proposed tool itself asks for
 *       ({@code AgentTool.pastEventQuery}), read with the run's token, so they are what that
 *       principal may see.</li>
 * </ul>
 * The decision log is narrowed in SQL by {@link #candidateToolName}; {@link #matches} decides. A rule
 * that no longer narrows by tool changes both together.
 */
public final class PrecedentMatchRule {

    private PrecedentMatchRule() {
    }

    /** The tool whose decided calls are candidates for {@code call}. */
    public static String candidateToolName(ProposedCall call) {
        return call.tool().name();
    }

    /**
     * Whether {@code candidate} is a precedent of {@code call}, proposed by a run acting for
     * {@code callKind}.
     */
    public static boolean matches(ProposedCall call, PrincipalKind callKind, DecisionPrecedent candidate) {
        return candidate.toolName().equals(call.tool().name())
                && candidate.principalKind() != null
                && candidate.principalKind() == callKind;
    }

    /** The past events behind {@code call}, if its tool names any. */
    public static Optional<PastEventQuery> pastEventQuery(ProposedCall call) {
        return call.tool().pastEventQuery(call.input());
    }
}
