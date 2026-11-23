package ch.batbern.agent.tools;

import ch.batbern.agent.gateway.PublicApi;

import java.util.Objects;
import java.util.UUID;

/**
 * What a tool gets besides its input (story 4).
 *
 * @param runId the run in {@code agent_runs}
 * @param api   the public API with the run's minted token; the only way a tool reaches a service
 */
public record ToolContext(UUID runId, PublicApi api) {

    public ToolContext {
        Objects.requireNonNull(runId, "runId");
        Objects.requireNonNull(api, "api");
    }
}
