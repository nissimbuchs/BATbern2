package ch.batbern.agent.loop;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Objects;

/**
 * A tool the model may choose: the Messages API's {@code name}, {@code description} and
 * {@code input_schema}.
 *
 * @param inputSchema a JSON Schema of {@code "type": "object"}
 */
public record LlmToolSpec(String name, String description, JsonNode inputSchema) {

    public LlmToolSpec {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(inputSchema, "inputSchema");
    }
}
