package ch.batbern.agent.loop;

import org.springframework.stereotype.Component;

/**
 * The default {@link LlmPort} while no model adapter exists: every call is unavailable, so a run
 * hands itself to the organizers. Story 3 replaces it with the Anthropic adapter.
 */
@Component
public class UnavailableLlm implements LlmPort {

    @Override
    public LlmResponse complete(LlmRequest request) {
        throw new LlmUnavailableException("No model adapter is configured in agent-service");
    }
}
