package ch.batbern.agent.llm;

import ch.batbern.agent.loop.LlmContent;
import ch.batbern.agent.loop.LlmPort;
import ch.batbern.agent.loop.LlmRequest;
import ch.batbern.agent.loop.LlmResponse;
import ch.batbern.agent.loop.LlmStopReason;
import ch.batbern.agent.loop.LlmUsage;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The {@link LlmPort} of local development without a key (profile {@code local}): a canned answer
 * that ends the turn at once, so a run can be clicked through locally without reaching the model
 * (Operational Envelope, "scripted adapter"). It lives in main because local development has no test
 * classpath; production never selects it.
 */
public class LocalDevLlm implements LlmPort {

    /** The model id this adapter answers with, logged in place of a real one. */
    public static final String MODEL = "local-scripted";

    static final String ANSWER = "Local development: no model is configured, so this canned answer ends the run.";

    private final AtomicLong nextId = new AtomicLong(1);

    @Override
    public LlmResponse complete(LlmRequest request) {
        return new LlmResponse("local_" + nextId.getAndIncrement(), MODEL, List.of(new LlmContent.Text(ANSWER)),
                LlmStopReason.END_TURN, new LlmUsage(0, 0, 0, 0));
    }
}
