package ch.batbern.agent.gate;

import ch.batbern.agent.run.RunTrigger;
import ch.batbern.agent.tools.OperationClass;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The autonomy rules alone (story 7, AD-7): every branch of {@link GateRules}, in its order. */
@DisplayName("GateRules decides by class, precedents, recipients and trigger")
class GateRulesTest {

    private static final GateFacts.Mail TO_ANNA = mail("anna@example.com");
    /** The owner's values (2026-11-22), the defaults of {@code agent.limits}. */
    private static final GateRules.Thresholds OWNER = new GateRules.Thresholds(2, 1);

    @ParameterizedTest
    @EnumSource(value = OperationClass.class, names = {"REVERSIBLE", "REPLY"})
    @DisplayName("reversible and reply run alone")
    void should_run_when_reversibleOrReply(OperationClass toolClass) {
        GateRules.Decision decision = GateRules.decide(facts(toolClass, RunTrigger.ORGANIZER, null, 0, 0), OWNER);

        assertThat(decision.outcome()).isEqualTo(VerdictOutcome.RUN);
        assertThat(decision.reason()).isEqualTo(GateReason.RUNS_ALONE);
        assertThat(decision.effectiveClass()).isEqualTo(toolClass);
        assertThat(decision.confidence()).isNull();
    }

    @Test
    @DisplayName("binding: 0 and 1 approvals pending, 2 and 3 run, a denial always pending")
    void should_applyThreshold_when_binding() {
        assertThat(binding(0, 0)).extracting(GateRules.Decision::outcome, GateRules.Decision::reason)
                .containsExactly(VerdictOutcome.PENDING_APPROVAL, GateReason.BINDING_BELOW_THRESHOLD);
        assertThat(binding(1, 0)).extracting(GateRules.Decision::outcome, GateRules.Decision::confidence)
                .containsExactly(VerdictOutcome.PENDING_APPROVAL, new BigDecimal("0.5000"));
        assertThat(binding(2, 0)).extracting(GateRules.Decision::outcome, GateRules.Decision::reason,
                GateRules.Decision::confidence)
                .containsExactly(VerdictOutcome.RUN, GateReason.BINDING_CLEARED, new BigDecimal("1.0000"));
        assertThat(binding(3, 0).confidence()).isEqualTo(new BigDecimal("1.0000"));
        assertThat(binding(5, 1)).extracting(GateRules.Decision::outcome, GateRules.Decision::reason,
                GateRules.Decision::confidence)
                .containsExactly(VerdictOutcome.PENDING_APPROVAL, GateReason.BINDING_DENIED, new BigDecimal("0.0000"));
    }

    @Test
    @DisplayName("audience is pending even with approvals")
    void should_pend_when_audience() {
        GateRules.Decision decision = GateRules.decide(facts(OperationClass.AUDIENCE, RunTrigger.ORGANIZER,
                TO_ANNA, 9, 0), OWNER);

        assertThat(decision.outcome()).isEqualTo(VerdictOutcome.PENDING_APPROVAL);
        assertThat(decision.reason()).isEqualTo(GateReason.AUDIENCE_CLASS);
    }

    @Test
    @DisplayName("a second non-organizer, in the run or the day, makes the mail audience; the same one again does not")
    void should_raiseToAudience_when_secondRecipient() {
        GateFacts inRun = new GateFacts(OperationClass.REPLY, RunTrigger.INBOUND_MAIL, mail("ben@example.com"), false,
                Set.of("anna@example.com"), Set.of("anna@example.com"), 0, 0);
        GateFacts inDay = new GateFacts(OperationClass.REPLY, RunTrigger.INBOUND_MAIL, mail("ben@example.com"), false,
                Set.of(), Set.of("anna@example.com"), 0, 0);
        GateFacts again = new GateFacts(OperationClass.REPLY, RunTrigger.INBOUND_MAIL, TO_ANNA, false,
                Set.of("anna@example.com"), Set.of("anna@example.com"), 0, 0);
        GateFacts twoAtOnce = facts(OperationClass.BINDING, RunTrigger.ORGANIZER,
                mail("anna@example.com", "ben@example.com"), 2, 0);

        for (GateFacts facts : new GateFacts[] {inRun, inDay, twoAtOnce}) {
            GateRules.Decision decision = GateRules.decide(facts, OWNER);
            assertThat(decision.outcome()).isEqualTo(VerdictOutcome.PENDING_APPROVAL);
            assertThat(decision.effectiveClass()).isEqualTo(OperationClass.AUDIENCE);
            assertThat(decision.reason()).isEqualTo(GateReason.RECIPIENTS_ABOVE_ONE);
        }
        assertThat(GateRules.decide(inDay, OWNER).recipientsToday()).isEqualTo(2);
        assertThat(GateRules.decide(inDay, OWNER).recipientsThisRun()).isEqualTo(1);
        // Positive control: the same recipient again stays one
        GateRules.Decision same = GateRules.decide(again, OWNER);
        assertThat(same.outcome()).isEqualTo(VerdictOutcome.RUN);
        assertThat(same.recipientsToday()).isEqualTo(1);
    }

    @Test
    @DisplayName("a mail to organizers only is not counted")
    void should_notCount_when_organizersOnly() {
        GateFacts organizersOnly = new GateFacts(OperationClass.REPLY, RunTrigger.ORGANIZER,
                new GateFacts.Mail(1, 1, Set.of()), false, Set.of("anna@example.com", "ben@example.com"),
                Set.of("anna@example.com", "ben@example.com"), 0, 0);

        assertThat(GateRules.decide(organizersOnly, OWNER).outcome()).isEqualTo(VerdictOutcome.RUN);
    }

    @Test
    @DisplayName("a scheduled run's mail is pending, its non-mail call is not")
    void should_pend_when_scheduledMail() {
        GateRules.Decision mail = GateRules.decide(facts(OperationClass.REPLY, RunTrigger.SCHEDULED, TO_ANNA, 0, 0),
                OWNER);
        GateRules.Decision binding = GateRules.decide(facts(OperationClass.BINDING, RunTrigger.SCHEDULED, TO_ANNA,
                2, 0), OWNER);
        GateRules.Decision noMail = GateRules.decide(facts(OperationClass.REVERSIBLE, RunTrigger.SCHEDULED, null,
                0, 0), OWNER);

        assertThat(mail.outcome()).isEqualTo(VerdictOutcome.PENDING_APPROVAL);
        assertThat(mail.reason()).isEqualTo(GateReason.SCHEDULED_MAIL);
        assertThat(binding.reason()).isEqualTo(GateReason.SCHEDULED_MAIL);
        assertThat(noMail.outcome()).isEqualTo(VerdictOutcome.RUN);
    }

    @Test
    @DisplayName("an unresolved mail is refused, before any other rule")
    void should_refuse_when_mailUnresolved() {
        GateRules.Decision decision = GateRules.decide(new GateFacts(OperationClass.AUDIENCE, RunTrigger.SCHEDULED,
                null, true, Set.of(), Set.of(), 0, 0), OWNER);

        assertThat(decision.outcome()).isEqualTo(VerdictOutcome.REFUSED);
        assertThat(decision.reason()).isEqualTo(GateReason.MAIL_UNRESOLVED);
    }

    @Test
    @DisplayName("a read call is not the gate's")
    void should_throw_when_read() {
        assertThatThrownBy(() -> GateRules.decide(facts(OperationClass.READ, RunTrigger.ORGANIZER, null, 0, 0),
                OWNER)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("configured thresholds move the binding threshold and the recipient limit (story 8)")
    void should_followThresholds_when_configured() {
        GateRules.Thresholds three = new GateRules.Thresholds(3, 2);
        GateFacts twoApprovals = facts(OperationClass.BINDING, RunTrigger.ORGANIZER, null, 2, 0);
        assertThat(GateRules.decide(twoApprovals, OWNER).outcome()).isEqualTo(VerdictOutcome.RUN);
        GateRules.Decision belowThree = GateRules.decide(twoApprovals, three);
        assertThat(belowThree.outcome()).isEqualTo(VerdictOutcome.PENDING_APPROVAL);
        assertThat(belowThree.confidence()).isEqualTo(new BigDecimal("0.6666"));
        assertThat(GateRules.decide(facts(OperationClass.BINDING, RunTrigger.ORGANIZER, null, 3, 0), three).outcome())
                .isEqualTo(VerdictOutcome.RUN);

        GateFacts secondPerson = new GateFacts(OperationClass.REPLY, RunTrigger.ORGANIZER, mail("ben@example.com"),
                false, Set.of("anna@example.com"), Set.of("anna@example.com"), 0, 0);
        assertThat(GateRules.decide(secondPerson, OWNER).reason()).isEqualTo(GateReason.RECIPIENTS_ABOVE_ONE);
        assertThat(GateRules.decide(secondPerson, three).reason()).isEqualTo(GateReason.RUNS_ALONE);
        GateFacts firstPerson = facts(OperationClass.REPLY, RunTrigger.ORGANIZER, TO_ANNA, 0, 0);
        assertThat(GateRules.decide(firstPerson, new GateRules.Thresholds(2, 0)).reason())
                .isEqualTo(GateReason.RECIPIENTS_ABOVE_ONE);
    }

    @Test
    @DisplayName("a binding threshold below 1 is refused: no binding call runs without a precedent")
    void should_refuse_when_thresholdBelowOne() {
        assertThatThrownBy(() -> new GateRules.Thresholds(0, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GateRules.Thresholds(2, -1)).isInstanceOf(IllegalArgumentException.class);
    }

    private static GateRules.Decision binding(int approvals, int denials) {
        return GateRules.decide(facts(OperationClass.BINDING, RunTrigger.ORGANIZER, null, approvals, denials), OWNER);
    }

    private static GateFacts facts(OperationClass toolClass, RunTrigger trigger, GateFacts.Mail mail, int approvals,
                                   int denials) {
        return new GateFacts(toolClass, trigger, mail, false, Set.of(), Set.of(), approvals, denials);
    }

    private static GateFacts.Mail mail(String... nonOrganizers) {
        return new GateFacts.Mail(nonOrganizers.length, 0, Set.of(nonOrganizers));
    }
}
