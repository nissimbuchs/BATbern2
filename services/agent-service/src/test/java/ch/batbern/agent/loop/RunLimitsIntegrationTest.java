package ch.batbern.agent.loop;

import org.junit.jupiter.api.DisplayName;
import org.springframework.test.context.TestPropertySource;

/**
 * The four limits with small values: 4 steps, 40 run tokens (three calls of 15), 1 reply, a daily
 * budget of 100 (80 spent by another run, so two calls of 15); the gate's values moved to 3 approvals
 * and 2 recipients.
 */
@DisplayName("Caps and the daily budget stop a run and hand it to the organizers")
@TestPropertySource(properties = {"agent.enabled=true", "agent.limits.run-steps=4", "agent.limits.run-tokens=40",
    "agent.limits.run-replies=1", "agent.limits.daily-tokens=100", "agent.limits.binding-approvals=3",
    "agent.limits.non-organizer-recipients=2"})
class RunLimitsIntegrationTest extends RunLimitsScenarios {

    @Override
    int stepsStopAfter() {
        return 4;
    }

    @Override
    int runTokensStopAfter() {
        return 3;
    }

    @Override
    int dailyTokensStopAfter() {
        return 2;
    }
}
