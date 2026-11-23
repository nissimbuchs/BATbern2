package ch.batbern.agent.log;

/** Where a model call stands; the values of {@code agent_model_calls.status}. */
public enum ModelCallStatus {
    /** Sent, no answer yet: a run that crashed here still shows its prompt. */
    STARTED,
    SUCCEEDED,
    FAILED
}
