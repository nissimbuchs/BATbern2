package ch.batbern.agent.loop;

/**
 * The only way to the model (AD-14). {@link AgentLoop} is its only caller: tools never call the
 * model, and nothing else in agent-service does. The types map 1:1 onto the Messages API tool use of
 * {@code anthropic-java} 3.12.0; the production adapter arrives in story 3, until then
 * {@link UnavailableLlm} answers.
 */
public interface LlmPort {

    /**
     * Sends one request to the model and returns its answer.
     *
     * @throws LlmUnavailableException when the model cannot be reached or answers with an error;
     *                                 the loop does not retry and hands the run to the organizers
     */
    LlmResponse complete(LlmRequest request);
}
