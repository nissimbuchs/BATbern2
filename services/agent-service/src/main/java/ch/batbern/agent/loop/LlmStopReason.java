package ch.batbern.agent.loop;

/**
 * Why the model stopped: the Messages API's {@code stop_reason}. An adapter maps a value it does not
 * know to {@link #UNKNOWN}.
 */
public enum LlmStopReason {
    END_TURN,
    TOOL_USE,
    MAX_TOKENS,
    STOP_SEQUENCE,
    PAUSE_TURN,
    REFUSAL,
    UNKNOWN
}
