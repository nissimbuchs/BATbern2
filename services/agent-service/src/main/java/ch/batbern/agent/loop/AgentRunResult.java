package ch.batbern.agent.loop;

import ch.batbern.agent.run.AgentRunStatus;
import ch.batbern.agent.run.RunEndReason;

import java.util.UUID;

/**
 * How a run ended.
 *
 * @param runId  the run's id in {@code agent_runs}
 * @param status DONE, FAILED or HANDED_TO_ORGANIZERS
 * @param reason why
 * @param steps  the steps it took (model calls made or attempted)
 */
public record AgentRunResult(UUID runId, AgentRunStatus status, RunEndReason reason, int steps) {
}
