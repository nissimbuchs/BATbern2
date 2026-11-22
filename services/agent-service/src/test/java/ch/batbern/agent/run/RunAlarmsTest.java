package ch.batbern.agent.run;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Story 10 (R10): which run ends carry which alarm marker. The markers are what the CloudWatch
 * metric filters match (agent-run-alarms.ts reads them from {@link RunAlarms}).
 */
@DisplayName("Run ends map to their alarm markers")
class RunAlarmsTest {

    @Test
    @DisplayName("a fault ends FAILED and carries AGENT_RUN_FAILED only")
    void should_markFailure_when_internalError() {
        assertThat(RunAlarms.markers(RunEndReason.INTERNAL_ERROR, null)).containsExactly(RunAlarms.RUN_FAILED);
    }

    @ParameterizedTest
    @EnumSource(value = RunLimit.class, names = {"STEPS", "RUN_TOKENS", "REPLIES"})
    @DisplayName("a per-run cap carries AGENT_RUN_CAP_REACHED only")
    void should_markCap_when_perRunLimitReached(RunLimit limit) {
        assertThat(RunAlarms.markers(RunEndReason.CAP_REACHED, limit.name()))
                .containsExactly(RunAlarms.RUN_CAP_REACHED);
    }

    @Test
    @DisplayName("the daily budget carries AGENT_RUN_CAP_REACHED and AGENT_DAILY_BUDGET_BREACHED")
    void should_markBudget_when_dailyTokensReached() {
        assertThat(RunAlarms.markers(RunEndReason.CAP_REACHED, RunLimit.DAILY_TOKENS.name()))
                .containsExactly(RunAlarms.RUN_CAP_REACHED, RunAlarms.DAILY_BUDGET_BREACHED);
    }

    @ParameterizedTest
    @EnumSource(value = RunEndReason.class, names = {"GRANT_REFUSED", "TOKEN_REFUSED"})
    @DisplayName("a refused grant or mint carries AGENT_CREDENTIALS_REFUSED")
    void should_markRefusal_when_cumsRefused(RunEndReason reason) {
        assertThat(RunAlarms.markers(reason, "GRANT_INVALID")).containsExactly(RunAlarms.CREDENTIALS_REFUSED);
    }

    @ParameterizedTest
    @EnumSource(value = RunEndReason.class,
            names = {"COMPLETED", "MODEL_UNAVAILABLE", "MODEL_MAX_TOKENS", "MODEL_STOPPED"})
    @DisplayName("a completed run and a model that is unreachable, cut off or stopping carry no marker")
    void should_markNothing_when_endIsNotAlarmed(RunEndReason reason) {
        assertThat(RunAlarms.markers(reason, null)).isEmpty();
    }

    @Test
    @DisplayName("every end reason is decided, and FAILED is exactly the failure marker")
    void should_coverEveryReason_when_enumGrows() {
        for (RunEndReason reason : RunEndReason.values()) {
            boolean failed = RunAlarms.markers(reason, null).contains(RunAlarms.RUN_FAILED);
            assertThat(failed).as(reason.name()).isEqualTo(reason.status() == AgentRunStatus.FAILED);
        }
    }

    @Test
    @DisplayName("no marker contains another, so each literal metric filter matches its own line only")
    void should_beDistinct_when_matchedLiterally() {
        List<String> markers = List.of(RunAlarms.RUN_FAILED, RunAlarms.RUN_CAP_REACHED,
                RunAlarms.DAILY_BUDGET_BREACHED, RunAlarms.CREDENTIALS_REFUSED);
        for (String a : markers) {
            for (String b : markers) {
                if (!a.equals(b)) {
                    assertThat(a).doesNotContain(b);
                }
            }
        }
    }
}
