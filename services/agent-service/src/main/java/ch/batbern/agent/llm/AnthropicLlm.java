package ch.batbern.agent.llm;

import ch.batbern.agent.config.AgentProperties;
import ch.batbern.agent.loop.LlmContent;
import ch.batbern.agent.loop.LlmMessage;
import ch.batbern.agent.loop.LlmPort;
import ch.batbern.agent.loop.LlmRequest;
import ch.batbern.agent.loop.LlmResponse;
import ch.batbern.agent.loop.LlmStopReason;
import ch.batbern.agent.loop.LlmToolSpec;
import ch.batbern.agent.loop.LlmUnavailableException;
import ch.batbern.agent.loop.LlmUsage;
import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.errors.AnthropicException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.models.ErrorType;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.MessageParam;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.RedactedThinkingBlockParam;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.ThinkingBlockParam;
import com.anthropic.models.messages.Tool;
import com.anthropic.models.messages.ToolResultBlockParam;
import com.anthropic.models.messages.ToolUseBlockParam;
import com.anthropic.models.messages.Usage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The production {@link LlmPort} (story 3, AD-14): the Messages API through {@code anthropic-java}
 * 3.12.0, non-beta {@code client.messages()}, one request per call. No tool runner, no server tools,
 * no streaming, no fallback model: the loop owns the conversation, the request and the answer map
 * 1:1 onto the port's types.
 *
 * <p>Thinking is adaptive, the model's default, so the request sends no {@code thinking} field (the
 * model rejects {@code disabled}); its depth is {@code output_config.effort}. No {@code tool_choice}
 * either: the default {@code auto} is the only choice the model accepts besides {@code none}.
 * Thinking blocks the model returns come back to it unchanged on the next request.
 *
 * <p>Every SDK failure becomes {@link LlmUnavailableException} whose message carries the HTTP status
 * and the error type only: never the response body, never the key, and no cause, whose message would
 * carry the body. The SDK retries 408, 409, 429, 5xx and connection errors itself, up to
 * {@code agent.anthropic.max-retries}; after that the run is handed to the organizers.
 */
public class AnthropicLlm implements LlmPort, AutoCloseable {

    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

    private final AnthropicClient client;
    private final String model;
    private final OutputConfig.Effort effort;

    AnthropicLlm(AnthropicClient client, String model, String effort) {
        this.client = Objects.requireNonNull(client, "client");
        this.model = Objects.requireNonNull(model, "model");
        this.effort = OutputConfig.Effort.of(Objects.requireNonNull(effort, "effort"));
    }

    /** An adapter with its own HTTP client, from the configuration; the key must be installed. */
    public static AnthropicLlm create(AgentProperties.Anthropic config) {
        AnthropicOkHttpClient.Builder builder = AnthropicOkHttpClient.builder()
                .apiKey(config.key())
                .timeout(config.timeout())
                .maxRetries(config.maxRetries());
        if (!config.baseUrl().isEmpty()) {
            builder.baseUrl(config.baseUrl());
        }
        return new AnthropicLlm(builder.build(), config.model(), config.effort());
    }

    /** The model id every request names. */
    public String model() {
        return model;
    }

    @Override
    public LlmResponse complete(LlmRequest request) {
        MessageCreateParams params = params(request);
        try {
            // The mapping stays inside the try: the SDK validates fields lazily, and its
            // AnthropicInvalidDataException quotes the received value
            return response(client.messages().create(params));
        } catch (AnthropicServiceException e) {
            throw new LlmUnavailableException("The model API answered HTTP " + e.statusCode() + " ("
                    + e.type().map(ErrorType::asString).orElse("no error type") + ")");
        } catch (AnthropicException e) {
            // Connection refused, timeout, an unreadable answer: the class says which, no message
            throw new LlmUnavailableException("The model API could not be reached or read ("
                    + e.getClass().getSimpleName() + ")");
        }
    }

    @Override
    public void close() {
        client.close();
    }

    MessageCreateParams params(LlmRequest request) {
        MessageCreateParams.Builder builder = MessageCreateParams.builder()
                .model(model)
                .maxTokens(request.maxTokens())
                .outputConfig(OutputConfig.builder().effort(effort).build());
        if (!request.system().isEmpty()) {
            builder.system(request.system());
        }
        for (LlmToolSpec spec : request.tools()) {
            builder.addTool(tool(spec));
        }
        for (LlmMessage message : request.messages()) {
            List<ContentBlockParam> blocks = message.content().stream().map(AnthropicLlm::block).toList();
            builder.addMessage(message.role() == LlmMessage.Role.USER
                    ? MessageParam.ofUserOfContentBlockParams(blocks)
                    : MessageParam.ofAssistantOfContentBlockParams(blocks));
        }
        return builder.build();
    }

    private static Tool tool(LlmToolSpec spec) {
        JsonNode schema = spec.inputSchema();
        Tool.InputSchema.Builder inputSchema = Tool.InputSchema.builder();
        Map<String, JsonValue> extra = new LinkedHashMap<>();
        schema.properties().forEach(field -> {
            switch (field.getKey()) {
                // "type" is always "object", the builder's default
                case "type" -> { }
                case "properties" -> {
                    Tool.InputSchema.Properties.Builder properties = Tool.InputSchema.Properties.builder();
                    field.getValue().properties().forEach(property ->
                            properties.putAdditionalProperty(property.getKey(),
                                    JsonValue.fromJsonNode(property.getValue())));
                    inputSchema.properties(properties.build());
                }
                case "required" -> {
                    List<String> required = new ArrayList<>();
                    field.getValue().forEach(name -> required.add(name.asText()));
                    inputSchema.required(required);
                }
                default -> extra.put(field.getKey(), JsonValue.fromJsonNode(field.getValue()));
            }
        });
        inputSchema.putAllAdditionalProperties(extra);
        return Tool.builder()
                .name(spec.name())
                .description(spec.description())
                .inputSchema(inputSchema.build())
                .build();
    }

    private static ContentBlockParam block(LlmContent content) {
        return switch (content) {
            case LlmContent.Text text -> ContentBlockParam.ofText(text.text());
            case LlmContent.Thinking thinking -> ContentBlockParam.ofThinking(ThinkingBlockParam.builder()
                    .thinking(thinking.thinking())
                    .signature(thinking.signature())
                    .build());
            case LlmContent.RedactedThinking redacted -> ContentBlockParam.ofRedactedThinking(
                    RedactedThinkingBlockParam.builder().data(redacted.data()).build());
            case LlmContent.ToolUse use -> ContentBlockParam.ofToolUse(ToolUseBlockParam.builder()
                    .id(use.id())
                    .name(use.name())
                    .input(toolInput(use.input()))
                    .build());
            case LlmContent.ToolResult result -> ContentBlockParam.ofToolResult(ToolResultBlockParam.builder()
                    .toolUseId(result.toolUseId())
                    .content(result.content())
                    .isError(result.isError())
                    .build());
        };
    }

    private static ToolUseBlockParam.Input toolInput(JsonNode input) {
        ToolUseBlockParam.Input.Builder builder = ToolUseBlockParam.Input.builder();
        input.properties().forEach(field ->
                builder.putAdditionalProperty(field.getKey(), JsonValue.fromJsonNode(field.getValue())));
        return builder.build();
    }

    private static LlmResponse response(Message message) {
        List<LlmContent> content = new ArrayList<>();
        for (ContentBlock block : message.content()) {
            content.add(content(block));
        }
        Usage usage = message.usage();
        return new LlmResponse(message.id(), message.model().asString(), content,
                stopReason(message), new LlmUsage(usage.inputTokens(), usage.outputTokens(),
                usage.cacheCreationInputTokens().orElse(0L), usage.cacheReadInputTokens().orElse(0L)));
    }

    private static LlmContent content(ContentBlock block) {
        if (block.isText()) {
            return new LlmContent.Text(block.asText().text());
        }
        if (block.isThinking()) {
            return new LlmContent.Thinking(block.asThinking().thinking(), block.asThinking().signature());
        }
        if (block.isRedactedThinking()) {
            return new LlmContent.RedactedThinking(block.asRedactedThinking().data());
        }
        if (block.isToolUse()) {
            JsonNode input = block.asToolUse()._input().convert(JsonNode.class);
            return new LlmContent.ToolUse(block.asToolUse().id(), block.asToolUse().name(),
                    input == null || input.isNull() ? NODES.objectNode() : input);
        }
        // agent-service declares no server tools, so no other block can come. Dropping one would
        // break the append-only history; the run is handed over instead.
        throw new LlmUnavailableException("The model answered with a content block agent-service does not handle");
    }

    private static LlmStopReason stopReason(Message message) {
        return message.stopReason().map(AnthropicLlm::stopReason).orElse(LlmStopReason.UNKNOWN);
    }

    private static LlmStopReason stopReason(StopReason reason) {
        return switch (reason.value()) {
            case END_TURN -> LlmStopReason.END_TURN;
            case TOOL_USE -> LlmStopReason.TOOL_USE;
            case MAX_TOKENS -> LlmStopReason.MAX_TOKENS;
            case STOP_SEQUENCE -> LlmStopReason.STOP_SEQUENCE;
            case PAUSE_TURN -> LlmStopReason.PAUSE_TURN;
            case REFUSAL -> LlmStopReason.REFUSAL;
            // model_context_window_exceeded and any value newer than the SDK
            default -> LlmStopReason.UNKNOWN;
        };
    }
}
