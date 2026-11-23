package ch.batbern.agent.loop;

/**
 * The {@link LlmPort} while no model key is installed in production: every call is unavailable, so a
 * run hands itself to the organizers. Chosen at startup by {@code llm/LlmConfig}; the key secret
 * holding {@code none} selects it.
 */
public class UnavailableLlm implements LlmPort {

    @Override
    public LlmResponse complete(LlmRequest request) {
        throw new LlmUnavailableException("No model adapter is configured in agent-service");
    }
}
