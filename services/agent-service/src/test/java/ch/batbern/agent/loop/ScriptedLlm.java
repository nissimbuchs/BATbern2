package ch.batbern.agent.loop;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Supplier;

/**
 * The model in tests: a scripted {@link LlmPort}. The model is a process boundary, so this is within
 * the mock rule; the loop, the decision log and the database are real. Each call takes the next
 * scripted step (a response, or an exception to throw) and records the request it got. Calling past
 * the end of the script is a test bug and throws {@link IllegalStateException}.
 */
@Primary
@Component
public class ScriptedLlm implements LlmPort {

    public static final String MODEL = "scripted-model";

    private final Deque<Supplier<LlmResponse>> script = new ArrayDeque<>();
    private final List<LlmRequest> requests = new ArrayList<>();
    private int nextMessageId = 1;

    @Override
    public synchronized LlmResponse complete(LlmRequest request) {
        requests.add(request);
        Supplier<LlmResponse> step = script.pollFirst();
        if (step == null) {
            throw new IllegalStateException("The script has no answer for model call " + requests.size());
        }
        return step.get();
    }

    /** Next call answers {@code response}. */
    public synchronized ScriptedLlm then(LlmResponse response) {
        script.addLast(() -> response);
        return this;
    }

    /** Next call throws {@code error}. */
    public synchronized ScriptedLlm thenThrow(RuntimeException error) {
        script.addLast(() -> {
            throw error;
        });
        return this;
    }

    /** A response with {@code stopReason}, {@code content} and the given token counts. */
    public synchronized LlmResponse response(LlmStopReason stopReason, LlmUsage usage, LlmContent... content) {
        return new LlmResponse("msg_" + (nextMessageId++), MODEL, List.of(content), stopReason, usage);
    }

    /** The requests the loop sent, in order. */
    public synchronized List<LlmRequest> requests() {
        return List.copyOf(requests);
    }

    /** Whether every scripted step was used. */
    public synchronized boolean exhausted() {
        return script.isEmpty();
    }

    public synchronized void reset() {
        script.clear();
        requests.clear();
        nextMessageId = 1;
    }
}
