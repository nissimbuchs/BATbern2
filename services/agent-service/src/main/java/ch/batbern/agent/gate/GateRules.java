package ch.batbern.agent.gate;

import ch.batbern.agent.run.RunTrigger;
import ch.batbern.agent.tools.OperationClass;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.Set;

/**
 * The autonomy rules (story 7, AD-7, owner decisions 2026-11-22), the one place they live. Pure:
 * {@link GateFacts} in, {@link Decision} out. In order:
 * <ol>
 *   <li>A mail the tool could not resolve is REFUSED.</li>
 *   <li>An {@code audience} tool, or a mail to a non-organizer that brings the distinct
 *       non-organizer recipients of the run or of the day above one, is {@code audience}: always
 *       PENDING_APPROVAL. A mail to organizers only is not counted.</li>
 *   <li>Any mail of a scheduled run is PENDING_APPROVAL.</li>
 *   <li>A {@code binding} call runs alone only with at least {@value #REQUIRED_APPROVALS} matching
 *       approvals and no matching denial; otherwise PENDING_APPROVAL.</li>
 *   <li>A {@code reversible} or {@code reply} call runs alone (the caps are checked after, by
 *       {@link GateCaps}, as for a cleared {@code binding} call).</li>
 * </ol>
 */
public final class GateRules {

    /** Matching approved precedents a {@code binding} call needs to run alone (owner, 2026-11-22). */
    public static final int REQUIRED_APPROVALS = 2;

    /** Distinct non-organizer recipients a run or a day may reach before every further mail is {@code audience}. */
    public static final int MAX_RECIPIENTS = 1;

    private GateRules() {
    }

    /**
     * What the gate decided.
     *
     * @param outcome           run, pending approval or refused
     * @param effectiveClass    the class the call was treated as
     * @param reason            the rule that decided
     * @param confidence        0..1 for a {@code binding} call; {@code null} otherwise
     * @param recipientsThisRun distinct non-organizer recipients of the run, this mail included
     * @param recipientsToday   distinct non-organizer recipients of the day, this mail included
     */
    public record Decision(VerdictOutcome outcome, OperationClass effectiveClass, GateReason reason,
                           BigDecimal confidence, int recipientsThisRun, int recipientsToday) {
    }

    /** Decides a non-read call. */
    public static Decision decide(GateFacts facts) {
        OperationClass toolClass = facts.toolClass();
        if (toolClass == OperationClass.READ) {
            throw new IllegalArgumentException("Read calls skip the gate");
        }
        Set<String> thisMail = facts.mail() == null ? Set.of() : facts.mail().nonOrganizerAddresses();
        int thisRun = union(facts.runRecipientsBefore(), thisMail);
        int today = union(facts.dayRecipientsBefore(), thisMail);

        if (facts.mailUnresolved()) {
            return new Decision(VerdictOutcome.REFUSED, toolClass, GateReason.MAIL_UNRESOLVED, null, thisRun, today);
        }
        if (toolClass == OperationClass.AUDIENCE) {
            return pending(OperationClass.AUDIENCE, GateReason.AUDIENCE_CLASS, thisRun, today);
        }
        if (!thisMail.isEmpty() && (thisRun > MAX_RECIPIENTS || today > MAX_RECIPIENTS)) {
            return pending(OperationClass.AUDIENCE, GateReason.RECIPIENTS_ABOVE_ONE, thisRun, today);
        }
        if (facts.sendsMail() && facts.trigger() == RunTrigger.SCHEDULED) {
            return pending(toolClass, GateReason.SCHEDULED_MAIL, thisRun, today);
        }
        if (toolClass == OperationClass.BINDING) {
            BigDecimal confidence = confidence(facts.matchingApprovals(), facts.matchingDenials());
            if (facts.matchingDenials() > 0) {
                return new Decision(VerdictOutcome.PENDING_APPROVAL, toolClass, GateReason.BINDING_DENIED,
                        confidence, thisRun, today);
            }
            if (facts.matchingApprovals() >= REQUIRED_APPROVALS) {
                return new Decision(VerdictOutcome.RUN, toolClass, GateReason.BINDING_CLEARED, confidence,
                        thisRun, today);
            }
            return new Decision(VerdictOutcome.PENDING_APPROVAL, toolClass, GateReason.BINDING_BELOW_THRESHOLD,
                    confidence, thisRun, today);
        }
        return new Decision(VerdictOutcome.RUN, toolClass, GateReason.RUNS_ALONE, null, thisRun, today);
    }

    /**
     * Confidence of a {@code binding} call: 0 with any matching denial, else the share of the
     * required approvals present, capped at 1. The threshold is 1.
     */
    static BigDecimal confidence(int approvals, int denials) {
        if (denials > 0) {
            return BigDecimal.ZERO.setScale(4, RoundingMode.UNNECESSARY);
        }
        int counted = Math.min(approvals, REQUIRED_APPROVALS);
        return BigDecimal.valueOf(counted).divide(BigDecimal.valueOf(REQUIRED_APPROVALS), 4, RoundingMode.DOWN);
    }

    private static Decision pending(OperationClass effectiveClass, GateReason reason, int thisRun, int today) {
        return new Decision(VerdictOutcome.PENDING_APPROVAL, effectiveClass, reason, null, thisRun, today);
    }

    private static int union(Set<String> before, Set<String> thisMail) {
        Set<String> all = new HashSet<>(before);
        all.addAll(thisMail);
        return all.size();
    }
}
