package ch.batbern.agent.tools.read;

import ch.batbern.agent.gateway.ApiResponse;
import ch.batbern.agent.tools.AgentTool;
import ch.batbern.agent.tools.OperationClass;
import ch.batbern.agent.tools.ToolContext;
import ch.batbern.agent.tools.ToolInput.InvalidToolInputException;
import ch.batbern.agent.tools.ToolOutcome;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.function.Function;

/**
 * A {@code read} tool over the public API (story 5): checks its input, makes its GETs with the run's
 * token (reads reuse it, story 4), and hands the model a projection of the answer. Bad input and an
 * HTTP error of the API are error results the model sees; nothing is retried.
 */
abstract class PublicApiReadTool implements AgentTool {

    static final ObjectMapper JSON = new ObjectMapper();

    @Override
    public final OperationClass operationClass() {
        return OperationClass.READ;
    }

    @Override
    public final ToolOutcome execute(JsonNode input, ToolContext context) {
        try {
            return read(input, context);
        } catch (InvalidToolInputException e) {
            return ToolOutcome.error("Invalid input: " + e.getMessage());
        }
    }

    /** The tool's work; throws {@link InvalidToolInputException} for input it refuses. */
    abstract ToolOutcome read(JsonNode input, ToolContext context);

    /**
     * The model's answer for {@code response}: the projection of its JSON body when 2xx, else an
     * error naming the status and the API's message.
     */
    static ToolOutcome answer(ApiResponse response, Function<JsonNode, JsonNode> projection) {
        if (!response.ok()) {
            String message = message(response.body());
            return ToolOutcome.error("The BATbern API answered " + response.status()
                    + (message == null ? "" : ": " + message));
        }
        try {
            JsonNode body = response.body() == null ? JSON.nullNode() : JSON.readTree(response.body());
            return ToolOutcome.ok(JSON.writeValueAsString(projection.apply(body)));
        } catch (JsonProcessingException e) {
            return ToolOutcome.error("The BATbern API answered " + response.status() + " with a body that is not JSON");
        }
    }

    /** A query value, percent-encoded (a space is {@code %20}, never {@code +}). */
    static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String message(String body) {
        if (body == null) {
            return null;
        }
        try {
            JsonNode message = JSON.readTree(body).get("message");
            return message != null && message.isTextual() ? message.asText() : null;
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}
