package ch.batbern.agent.loop;

import org.junit.jupiter.api.DisplayName;
import org.springframework.test.context.TestPropertySource;

/**
 * The same scripts as {@link RunLimitsIntegrationTest} with larger values: each stop point moves.
 * 6 steps, 70 run tokens (five calls of 15), 2 replies, a daily budget of 140 (80 spent, so four
 * calls of 15).
 */
@DisplayName("Changing agent.limits moves where a run stops")
@TestPropertySource(properties = {"agent.enabled=true", "agent.limits.run-steps=6", "agent.limits.run-tokens=70",
    "agent.limits.run-replies=2", "agent.limits.daily-tokens=140"})
class RunLimitsMovedIntegrationTest extends RunLimitsScenarios {

    @Override
    int stepsStopAfter() {
        return 6;
    }

    @Override
    int runTokensStopAfter() {
        return 5;
    }

    @Override
    int dailyTokensStopAfter() {
        return 4;
    }
}
