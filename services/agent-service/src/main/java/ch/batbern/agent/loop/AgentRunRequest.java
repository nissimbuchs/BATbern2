package ch.batbern.agent.loop;

import ch.batbern.agent.identity.GrantSource;
import ch.batbern.agent.log.InboundMessage;
import ch.batbern.agent.run.RunTrigger;

import java.util.Objects;

/**
 * What one run of the loop is given. The tools come from the tool registry (story 5), not from the
 * caller.
 *
 * @param grant       where the run's grant comes from; it fixes the run's trigger
 * @param system      the system prompt
 * @param userMessage the first user message
 * @param inbound     the message that started the run, logged in full; {@code null} when there is none
 */
public record AgentRunRequest(GrantSource grant, String system, String userMessage, InboundMessage inbound) {

    public AgentRunRequest {
        Objects.requireNonNull(grant, "grant");
        Objects.requireNonNull(system, "system");
        Objects.requireNonNull(userMessage, "userMessage");
    }

    /** A run without an inbound message. */
    public AgentRunRequest(GrantSource grant, String system, String userMessage) {
        this(grant, system, userMessage, null);
    }

    /** What started the run, following from its grant source. */
    public RunTrigger trigger() {
        return grant.trigger();
    }

    /** Never shows the grant's proof (the source masks it) nor the prompt. */
    @Override
    public String toString() {
        return "AgentRunRequest[grant=" + grant + "]";
    }
}
