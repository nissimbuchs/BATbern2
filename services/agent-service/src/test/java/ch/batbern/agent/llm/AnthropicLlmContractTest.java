package ch.batbern.agent.llm;

import ch.batbern.agent.config.AgentProperties;
import ch.batbern.agent.loop.LlmContent;
import ch.batbern.agent.loop.LlmMessage;
import ch.batbern.agent.loop.LlmRequest;
import ch.batbern.agent.loop.LlmResponse;
import ch.batbern.agent.loop.LlmStopReason;
import ch.batbern.agent.loop.LlmToolSpec;
import ch.batbern.agent.loop.LlmUnavailableException;
import ch.batbern.agent.loop.LlmUsage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The Anthropic adapter against a local JDK {@link HttpServer} that serves answers in the documented
 * Messages response shape (C2: no test reaches the real API, and there is no key to record with).
 * The fixture {@code anthropic/messages-tool-use.json} is hand-built from the documented shape. Every
 * row of the story's I/O matrix that concerns the adapter: tool use round trip, stop reasons, API
 * errors, unreachable.
 */
@DisplayName("AnthropicLlm speaks the Messages API")
class AnthropicLlmContractTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String KEY = "sk-ant-contract-test-key-0042";
    private static final String MODEL = "claude-sonnet-5-5";
    private static final String SYSTEM = "You coordinate BATbern speakers.";

    private HttpServer server;
    private final Deque<Answer> answers = new ArrayDeque<>();
    private final List<Recorded> requests = new CopyOnWriteArrayList<>();

    private record Answer(int status, String body, long delayMillis) {
    }

    private record Recorded(String method, String path, Map<String, List<String>> headers, String body) {
    }

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> {
            try (InputStream in = exchange.getRequestBody()) {
                requests.add(new Recorded(exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
                        Map.copyOf(exchange.getRequestHeaders()), new String(in.readAllBytes(), StandardCharsets.UTF_8)));
            }
            Answer answer;
            synchronized (answers) {
                answer = answers.isEmpty() ? new Answer(599, "{}", 0) : answers.pollFirst();
            }
            if (answer.delayMillis() > 0) {
                try {
                    Thread.sleep(answer.delayMillis());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            byte[] bytes = answer.body().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.getResponseHeaders().add("request-id", "req_contract");
            // The SDK honours retry-after-ms: keeps the retry tests fast
            exchange.getResponseHeaders().add("retry-after-ms", "1");
            exchange.sendResponseHeaders(answer.status(), bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private void answer(int status, String body) {
        answers.addLast(new Answer(status, body, 0));
    }

    private String baseUrl() {
        return "http://" + server.getAddress().getHostString() + ":" + server.getAddress().getPort();
    }

    private AnthropicLlm adapter(int maxRetries, Duration timeout, String baseUrl) {
        return AnthropicLlm.create(new AgentProperties.Anthropic(KEY, MODEL, "medium", timeout, maxRetries, baseUrl));
    }

    private AnthropicLlm adapter(int maxRetries) {
        return adapter(maxRetries, Duration.ofSeconds(10), baseUrl());
    }

    private static String fixture(String name) throws IOException {
        try (InputStream in = AnthropicLlmContractTest.class.getResourceAsStream("/anthropic/" + name)) {
            assertThat(in).as("fixture " + name).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static ObjectNode input(String key, String value) {
        return JSON.createObjectNode().put(key, value);
    }

    private static LlmToolSpec lookupTool() throws IOException {
        JsonNode schema = JSON.readTree("""
                {"type": "object",
                 "properties": {"query": {"type": "string", "description": "event code or title"},
                                "limit": {"type": "integer"}},
                 "required": ["query"],
                 "additionalProperties": false}
                """);
        return new LlmToolSpec("lookup_event", "Looks up a BATbern event.", schema);
    }

    /** A second-step request: the first answer with its thinking block, then a failed tool result. */
    private static LlmRequest secondStepRequest() throws IOException {
        List<LlmMessage> messages = new ArrayList<>();
        messages.add(LlmMessage.user("Which slot is free?"));
        messages.add(LlmMessage.assistant(List.of(
                new LlmContent.Thinking("", "sig-step-1"),
                new LlmContent.RedactedThinking("redacted-step-1"),
                new LlmContent.Text("Let me look."),
                new LlmContent.ToolUse("toolu_prev", "lookup_event", input("query", "BATbern60")))));
        messages.add(LlmMessage.user(List.of(new LlmContent.ToolResult("toolu_prev", "The tool failed.", true))));
        return new LlmRequest(SYSTEM, messages, List.of(lookupTool()), 16000);
    }

    @Test
    @DisplayName("tool use round trip: blocks in order, TOOL_USE, four token counts, model id; Messages request body")
    void should_mapToolUseAndThinking_when_fixtureAnswers() throws IOException {
        answer(200, fixture("messages-tool-use.json"));

        LlmResponse response;
        try (AnthropicLlm llm = adapter(0)) {
            response = llm.complete(secondStepRequest());
        }

        // The answer through the port
        assertThat(response.id()).isEqualTo("msg_01ContractFixture");
        assertThat(response.model()).isEqualTo(MODEL);
        assertThat(response.stopReason()).isEqualTo(LlmStopReason.TOOL_USE);
        assertThat(response.usage()).isEqualTo(new LlmUsage(1200, 340, 512, 2048));
        assertThat(response.content()).hasSize(3);
        assertThat(response.content().get(0))
                .isEqualTo(new LlmContent.Thinking("", "EqQBCkYIBhgCKkDcontractfixturesignature"));
        assertThat(response.content().get(1)).isEqualTo(new LlmContent.Text("I will look up the event first."));
        LlmContent.ToolUse use = (LlmContent.ToolUse) response.content().get(2);
        assertThat(use.id()).isEqualTo("toolu_01Contract");
        assertThat(use.name()).isEqualTo("lookup_event");
        assertThat(use.input().get("query").asText()).isEqualTo("BATbern61");
        assertThat(use.input().get("limit").asInt()).isEqualTo(3);
        assertThat(response.toolUses()).containsExactly(use);

        // The request: one POST /v1/messages with the key header and the Messages API body
        assertThat(requests).hasSize(1);
        Recorded request = requests.get(0);
        assertThat(request.method()).isEqualTo("POST");
        assertThat(request.path()).isEqualTo("/v1/messages");
        // anthropic-java 3.12.0 sends a static credential, the API key included, as a bearer token
        // (AnthropicBackend.Credential.ApiKey -> StaticAuthHeaders), not as x-api-key
        assertThat(header(request, "authorization")).isEqualTo("Bearer " + KEY);
        assertThat(header(request, "anthropic-version")).isNotBlank();
        assertThat(header(request, "anthropic-beta")).as("non-beta endpoint").isNull();

        JsonNode body = JSON.readTree(request.body());
        assertThat(body.get("model").asText()).isEqualTo(MODEL);
        assertThat(body.get("system").asText()).isEqualTo(SYSTEM);
        assertThat(body.get("max_tokens").asInt()).isEqualTo(16000);
        assertThat(body.get("output_config").get("effort").asText()).isEqualTo("medium");
        // Adaptive thinking is the model's default; the model rejects disabled and forced tool choice
        assertThat(body.has("thinking")).isFalse();
        assertThat(body.has("tool_choice")).isFalse();
        assertThat(body.has("stream")).isFalse();

        JsonNode tool = body.get("tools").get(0);
        assertThat(tool.get("name").asText()).isEqualTo("lookup_event");
        assertThat(tool.get("description").asText()).isEqualTo("Looks up a BATbern event.");
        JsonNode schema = tool.get("input_schema");
        assertThat(schema.get("type").asText()).isEqualTo("object");
        assertThat(schema.get("properties").get("query").get("type").asText()).isEqualTo("string");
        assertThat(schema.get("properties").get("limit").get("type").asText()).isEqualTo("integer");
        assertThat(schema.get("required").get(0).asText()).isEqualTo("query");
        assertThat(schema.get("additionalProperties").asBoolean(true)).isFalse();

        JsonNode messages = body.get("messages");
        assertThat(messages).hasSize(3);
        assertThat(messages.get(0).get("role").asText()).isEqualTo("user");
        assertThat(messages.get(0).get("content").get(0).get("type").asText()).isEqualTo("text");
        assertThat(messages.get(0).get("content").get(0).get("text").asText()).isEqualTo("Which slot is free?");

        JsonNode assistant = messages.get(1);
        assertThat(assistant.get("role").asText()).isEqualTo("assistant");
        JsonNode thinking = assistant.get("content").get(0);
        assertThat(thinking.get("type").asText()).isEqualTo("thinking");
        assertThat(thinking.get("thinking").asText()).isEmpty();
        assertThat(thinking.get("signature").asText()).isEqualTo("sig-step-1");
        JsonNode redacted = assistant.get("content").get(1);
        assertThat(redacted.get("type").asText()).isEqualTo("redacted_thinking");
        assertThat(redacted.get("data").asText()).isEqualTo("redacted-step-1");
        assertThat(assistant.get("content").get(2).get("type").asText()).isEqualTo("text");
        JsonNode toolUse = assistant.get("content").get(3);
        assertThat(toolUse.get("type").asText()).isEqualTo("tool_use");
        assertThat(toolUse.get("id").asText()).isEqualTo("toolu_prev");
        assertThat(toolUse.get("name").asText()).isEqualTo("lookup_event");
        assertThat(toolUse.get("input").get("query").asText()).isEqualTo("BATbern60");

        JsonNode result = messages.get(2).get("content").get(0);
        assertThat(messages.get(2).get("role").asText()).isEqualTo("user");
        assertThat(result.get("type").asText()).isEqualTo("tool_result");
        assertThat(result.get("tool_use_id").asText()).isEqualTo("toolu_prev");
        assertThat(result.get("content").asText()).isEqualTo("The tool failed.");
        assertThat(result.get("is_error").asBoolean()).isTrue();
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
        "end_turn, END_TURN",
        "tool_use, TOOL_USE",
        "max_tokens, MAX_TOKENS",
        "stop_sequence, STOP_SEQUENCE",
        "pause_turn, PAUSE_TURN",
        "refusal, REFUSAL",
        "model_context_window_exceeded, UNKNOWN",
        "a_reason_newer_than_the_sdk, UNKNOWN"
    })
    @DisplayName("stop reasons: mapped; a value the adapter does not know is UNKNOWN")
    void should_mapStopReason_when_modelStops(String wire, LlmStopReason expected) throws IOException {
        answer(200, fixture("messages-tool-use.json").replace("\"stop_reason\": \"tool_use\"",
                "\"stop_reason\": \"" + wire + "\""));

        try (AnthropicLlm llm = adapter(0)) {
            assertThat(llm.complete(simpleRequest()).stopReason()).isEqualTo(expected);
        }
    }

    @Test
    @DisplayName("stop reason null: UNKNOWN")
    void should_beUnknown_when_stopReasonNull() throws IOException {
        answer(200, fixture("messages-tool-use.json").replace("\"stop_reason\": \"tool_use\"", "\"stop_reason\": null"));

        try (AnthropicLlm llm = adapter(0)) {
            assertThat(llm.complete(simpleRequest()).stopReason()).isEqualTo(LlmStopReason.UNKNOWN);
        }
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', value = {
        "input_tokens a string | \"input_tokens\": 1200 | \"input_tokens\": \"PLANTED-VALUE-MUST-NOT-LEAK\"",
        "usage missing | \"usage\": { | \"no_usage\": {\"planted\": \"PLANTED-VALUE-MUST-NOT-LEAK\", "
    })
    @DisplayName("200 with a malformed body: LlmUnavailableException naming the class only, no body value, no cause")
    void should_throwUnavailableWithoutBody_when_bodyMalformed(String name, String from, String to) throws IOException {
        String body = fixture("messages-tool-use.json").replace(from, to);
        assertThat(body).contains("PLANTED-VALUE-MUST-NOT-LEAK");
        answer(200, body);

        try (AnthropicLlm llm = adapter(0)) {
            assertThatThrownBy(() -> llm.complete(simpleRequest()))
                    .isInstanceOf(LlmUnavailableException.class)
                    .hasMessageContaining("could not be reached or read")
                    .hasMessageNotContaining("PLANTED-VALUE-MUST-NOT-LEAK")
                    .hasMessageNotContaining(KEY)
                    .hasNoCause();
        }
    }

    @ParameterizedTest(name = "HTTP {0} {1}")
    @CsvSource({
        "400, invalid_request_error",
        "401, authentication_error",
        "429, rate_limit_error",
        "500, api_error",
        "529, overloaded_error"
    })
    @DisplayName("API error: LlmUnavailableException with status and type, no body text, no key, no cause")
    void should_throwUnavailableWithStatusOnly_when_apiAnswersError(int status, String type) throws IOException {
        answer(status, fixture("error-body.json").replace("ERROR_TYPE", type));

        try (AnthropicLlm llm = adapter(0)) {
            assertThatThrownBy(() -> llm.complete(simpleRequest()))
                    .isInstanceOf(LlmUnavailableException.class)
                    .hasMessageContaining(String.valueOf(status))
                    .hasMessageContaining(type)
                    .hasMessageNotContaining("BODY-TEXT-MUST-NOT-LEAK")
                    .hasMessageNotContaining("example.com")
                    .hasMessageNotContaining(KEY)
                    .hasNoCause();
        }
    }

    @Test
    @DisplayName("the SDK retries 5xx per config, then throws: max-retries 2 means three requests")
    void should_retryThenThrow_when_serverKeepsFailing() throws IOException {
        String body = fixture("error-body.json").replace("ERROR_TYPE", "api_error");
        answer(500, body);
        answer(500, body);
        answer(500, body);

        try (AnthropicLlm llm = adapter(2)) {
            assertThatThrownBy(() -> llm.complete(simpleRequest()))
                    .isInstanceOf(LlmUnavailableException.class)
                    .hasMessageContaining("500");
        }
        assertThat(requests).hasSize(3);
    }

    @Test
    @DisplayName("an overloaded API that recovers within the retries answers through the port")
    void should_answer_when_retrySucceeds() throws IOException {
        answer(529, fixture("error-body.json").replace("ERROR_TYPE", "overloaded_error"));
        answer(200, fixture("messages-tool-use.json"));

        try (AnthropicLlm llm = adapter(1)) {
            assertThat(llm.complete(simpleRequest()).stopReason()).isEqualTo(LlmStopReason.TOOL_USE);
        }
        assertThat(requests).hasSize(2);
    }

    @Test
    @DisplayName("unreachable (connection refused): LlmUnavailableException")
    void should_throwUnavailable_when_connectionRefused() throws IOException {
        String deadUrl = baseUrl();
        server.stop(0);

        try (AnthropicLlm llm = adapter(0, Duration.ofSeconds(5), deadUrl)) {
            assertThatThrownBy(() -> llm.complete(simpleRequest()))
                    .isInstanceOf(LlmUnavailableException.class)
                    .hasMessageContaining("could not be reached")
                    .hasMessageNotContaining(KEY)
                    .hasNoCause();
        }
    }

    @Test
    @DisplayName("unreachable (timeout): LlmUnavailableException")
    void should_throwUnavailable_when_timeout() throws IOException {
        answers.addLast(new Answer(200, fixture("messages-tool-use.json"), 3000));

        try (AnthropicLlm llm = adapter(0, Duration.ofMillis(500), baseUrl())) {
            assertThatThrownBy(() -> llm.complete(simpleRequest()))
                    .isInstanceOf(LlmUnavailableException.class)
                    .hasMessageContaining("could not be reached")
                    .hasNoCause();
        }
    }

    private static LlmRequest simpleRequest() {
        return new LlmRequest(SYSTEM, List.of(LlmMessage.user("hello")), List.of(), 64);
    }

    private static String header(Recorded request, String name) {
        return request.headers().entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(name))
                .map(entry -> entry.getValue().get(0))
                .findFirst()
                .orElse(null);
    }
}
