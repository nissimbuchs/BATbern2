package ch.batbern.agent.loop;

/** The loop was asked to run while {@code agent.enabled} is false; no run row is written. */
public class AgentSwitchedOffException extends RuntimeException {

    public AgentSwitchedOffException() {
        super("The agent is switched off (agent.enabled=false)");
    }
}
