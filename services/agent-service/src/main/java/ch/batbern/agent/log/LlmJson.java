package ch.batbern.agent.log;

import ch.batbern.agent.loop.LlmContent;
import ch.batbern.agent.loop.LlmMessage;
import ch.batbern.agent.loop.LlmRequest;
import ch.batbern.agent.loop.LlmResponse;
import ch.batbern.agent.loop.LlmToolSpec;
import ch.batbern.agent.loop.LlmUsage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Locale;

/**
 * Writes the port's types as the decision log stores them: in the Messages API's own JSON shape
 * ({@code system}, {@code messages}, {@code tools}, {@code tool_use}, {@code tool_result}, snake
 * case), so a logged prompt reads like the request the model got.
 */
final class LlmJson {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private LlmJson() {
    }

    static String prompt(LlmRequest request) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("system", request.system());
        node.put("max_tokens", request.maxTokens());
        ArrayNode tools = node.putArray("tools");
        for (LlmToolSpec tool : request.tools()) {
            tools.addObject()
                    .put("name", tool.name())
                    .put("description", tool.description())
                    .set("input_schema", tool.inputSchema());
        }
        ArrayNode messages = node.putArray("messages");
        for (LlmMessage message : request.messages()) {
            ObjectNode m = messages.addObject();
            m.put("role", message.role().name().toLowerCase(Locale.ROOT));
            ArrayNode content = m.putArray("content");
            message.content().forEach(block -> content.add(block(block)));
        }
        return write(node);
    }

    static String response(LlmResponse response) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("id", response.id());
        node.put("model", response.model());
        node.put("stop_reason", response.stopReason().name().toLowerCase(Locale.ROOT));
        ArrayNode content = node.putArray("content");
        response.content().forEach(block -> content.add(block(block)));
        LlmUsage usage = response.usage();
        node.putObject("usage")
                .put("input_tokens", usage.inputTokens())
                .put("output_tokens", usage.outputTokens())
                .put("cache_creation_input_tokens", usage.cacheCreationInputTokens())
                .put("cache_read_input_tokens", usage.cacheReadInputTokens());
        return write(node);
    }

    /** The tools the model chose: {@code [{"id": ..., "name": ...}]}. */
    static String chosenTools(LlmResponse response) {
        ArrayNode node = MAPPER.createArrayNode();
        response.toolUses().forEach(use -> node.addObject().put("id", use.id()).put("name", use.name()));
        return write(node);
    }

    static String json(JsonNode node) {
        return write(node);
    }

    /** A copy of {@code node} with U+0000 replaced in every string value and field name. */
    private static JsonNode sanitize(JsonNode node) {
        if (node.isTextual()) {
            return MAPPER.getNodeFactory().textNode(storable(node.textValue()));
        }
        if (node.isObject()) {
            ObjectNode copy = MAPPER.createObjectNode();
            node.properties().forEach(field -> copy.set(storable(field.getKey()), sanitize(field.getValue())));
            return copy;
        }
        if (node.isArray()) {
            ArrayNode copy = MAPPER.createArrayNode();
            node.forEach(element -> copy.add(sanitize(element)));
            return copy;
        }
        return node;
    }

    private static ObjectNode block(LlmContent block) {
        ObjectNode node = MAPPER.createObjectNode();
        switch (block) {
            case LlmContent.Text text -> node.put("type", "text").put("text", text.text());
            case LlmContent.ToolUse use -> node.put("type", "tool_use")
                    .put("id", use.id())
                    .put("name", use.name())
                    .set("input", use.input());
            case LlmContent.ToolResult result -> node.put("type", "tool_result")
                    .put("tool_use_id", result.toolUseId())
                    .put("content", result.content())
                    .put("is_error", result.isError());
        }
        return node;
    }

    /**
     * PostgreSQL rejects U+0000 in text and in jsonb ({@code \u0000}); the decision log stores it as
     * U+FFFD, so a NUL in model, tool or inbound text cannot make a write fail. {@code null} stays null.
     */
    static String storable(String text) {
        return text == null ? null : text.replace('\u0000', '\uFFFD');
    }

    private static String write(JsonNode node) {
        try {
            // The serialized form escapes NUL as the six characters \u0000, which jsonb rejects too
            return MAPPER.writeValueAsString(sanitize(node));
        } catch (JsonProcessingException e) {
            // A tree of plain nodes always serializes; this would be a Jackson defect
            throw new IllegalStateException("Could not write a decision log JSON tree", e);
        }
    }
}
