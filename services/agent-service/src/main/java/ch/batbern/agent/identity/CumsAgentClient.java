package ch.batbern.agent.identity;

import ch.batbern.agent.config.AgentProperties;
import ch.batbern.agent.run.AgentRunEntity;
import ch.batbern.agent.run.PrincipalKind;
import ch.batbern.agent.run.RunEndReason;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;

/**
 * agent-service's client of CUMS as its identity provider (story 4, R2, AD-3, AD-15). It calls
 * exactly two operations of {@code docs/api/internal/cums-internal.openapi.yml}, each with
 * agent-service's caller secret in {@code X-Agent-Caller-Secret}:
 * <ul>
 *   <li>{@code POST /internal/agent-grants}: a run grant for an organizer login (the organizer's
 *       Cognito JWT forwarded as {@code Authorization}, on this call only), a linked run or a
 *       scheduler job;</li>
 *   <li>{@code POST /internal/agent-tokens}: an agent token for a grant.</li>
 * </ul>
 * A refusal, a transport error or an unreadable answer throws
 * {@link AgentCredentialsRefusedException} with the CUMS {@code details.code} (or
 * {@code HTTP_<status>}, {@code CUMS_UNREACHABLE}, {@code CALLER_SECRET_MISSING},
 * {@code INVALID_CUMS_RESPONSE}); a code that does not match the end-detail pattern becomes
 * {@code UNKNOWN_CODE}. No retry. Nothing here logs or puts in a message a username, token, grant
 * secret or caller secret, and request and response bodies are never logged.
 */
@Component
public class CumsAgentClient {

    static final String GRANTS = "/internal/agent-grants";
    static final String TOKENS = "/internal/agent-tokens";
    static final String CALLER_SECRET_HEADER = "X-Agent-Caller-Secret";

    /** No secret installed: CUMS is not called. */
    public static final String CALLER_SECRET_MISSING = "CALLER_SECRET_MISSING";
    /** CUMS could not be reached (connect or read failure). */
    public static final String CUMS_UNREACHABLE = "CUMS_UNREACHABLE";
    /** CUMS answered 2xx with a body that is not its contract. */
    public static final String INVALID_CUMS_RESPONSE = "INVALID_CUMS_RESPONSE";

    private static final Logger LOG = LoggerFactory.getLogger(CumsAgentClient.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final RestTemplate restTemplate;
    private final AgentProperties.Cums cums;

    public CumsAgentClient(RestTemplate restTemplate, AgentProperties properties) {
        this.restTemplate = restTemplate;
        this.cums = properties.cums();
    }

    /** Whether the caller secret is installed; without it every run is handed to the organizers. */
    public boolean hasCallerSecret() {
        return cums.hasCallerSecret();
    }

    @EventListener(ApplicationReadyEvent.class)
    void warnWithoutCallerSecret() {
        if (!cums.hasCallerSecret()) {
            LOG.warn("No CUMS caller secret (agent.cums.caller-secret is blank or none): every agent run is "
                    + "handed to the organizers with {}", CALLER_SECRET_MISSING);
        }
    }

    /**
     * Obtains a run grant from CUMS for {@code source}.
     *
     * @throws AgentCredentialsRefusedException GRANT_REFUSED with the code
     * @throws IllegalArgumentException         for a {@link GrantSource.HandedOver} source, which needs no grant call
     */
    public RunGrant grant(GrantSource source) {
        ObjectNode body = JSON.createObjectNode();
        HttpHeaders headers = jsonHeaders();
        switch (source) {
            case GrantSource.OrganizerLogin login -> {
                body.put("trigger", "ORGANIZER_LOGIN");
                headers.setBearerAuth(login.cognitoJwt());
            }
            case GrantSource.LinkedRun linked -> body.put("trigger", "LINKED_RUN")
                    .put("linkedRunId", linked.linkedRunId())
                    .put("linkedRunGrantSecret", linked.linkedRunGrantSecret());
            case GrantSource.SchedulerJob job -> body.put("trigger", "SCHEDULER_JOB").put("jobName", job.jobName());
            case GrantSource.HandedOver handedOver ->
                    throw new IllegalArgumentException("A handed-over grant needs no grant call");
        }
        JsonNode answer = post(GRANTS, body, headers, RunEndReason.GRANT_REFUSED);
        try {
            return new RunGrant(UUID.fromString(answer.path("runId").asText()), requiredText(answer, "grantSecret"),
                    PrincipalKind.valueOf(requiredText(answer, "principalKind")));
        } catch (IllegalArgumentException e) {
            throw new AgentCredentialsRefusedException(RunEndReason.GRANT_REFUSED, INVALID_CUMS_RESPONSE);
        }
    }

    /**
     * Mints an agent token for {@code grant}; no {@code Authorization} header is sent.
     *
     * @throws AgentCredentialsRefusedException TOKEN_REFUSED with the code
     */
    public AgentToken mint(RunGrant grant) {
        ObjectNode body = JSON.createObjectNode()
                .put("runId", grant.runId().toString())
                .put("grantSecret", grant.grantSecret());
        JsonNode answer = post(TOKENS, body, jsonHeaders(), RunEndReason.TOKEN_REFUSED);
        try {
            return new AgentToken(requiredText(answer, "token"), Instant.parse(requiredText(answer, "expiresAt")));
        } catch (IllegalArgumentException | DateTimeParseException e) {
            throw new AgentCredentialsRefusedException(RunEndReason.TOKEN_REFUSED, INVALID_CUMS_RESPONSE);
        }
    }

    private JsonNode post(String path, ObjectNode body, HttpHeaders headers, RunEndReason refusal) {
        if (!cums.hasCallerSecret()) {
            throw new AgentCredentialsRefusedException(refusal, CALLER_SECRET_MISSING);
        }
        headers.set(CALLER_SECRET_HEADER, cums.secret());
        ResponseEntity<String> response;
        try {
            response = restTemplate.exchange(URI.create(cums.baseUrl() + path), HttpMethod.POST,
                    new HttpEntity<>(body.toString(), headers), String.class);
        } catch (RestClientResponseException e) {
            // The exception's message carries the response body: never passed on
            throw new AgentCredentialsRefusedException(refusal, codeOf(e.getStatusCode().value(),
                    e.getResponseBodyAsString()));
        } catch (ResourceAccessException e) {
            throw new AgentCredentialsRefusedException(refusal, CUMS_UNREACHABLE);
        } catch (RestClientException e) {
            throw new AgentCredentialsRefusedException(refusal, INVALID_CUMS_RESPONSE);
        }
        try {
            JsonNode answer = response.getBody() == null ? null : JSON.readTree(response.getBody());
            if (answer == null || !answer.isObject()) {
                throw new AgentCredentialsRefusedException(refusal, INVALID_CUMS_RESPONSE);
            }
            return answer;
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new AgentCredentialsRefusedException(refusal, INVALID_CUMS_RESPONSE);
        }
    }

    /** {@code details.code} of the CUMS error body, else {@code HTTP_<status>}; never free text. */
    static String codeOf(int status, String body) {
        String code = null;
        if (body != null && !body.isBlank()) {
            try {
                JsonNode details = JSON.readTree(body).path("details").path("code");
                if (details.isTextual()) {
                    code = details.asText();
                }
            } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                // Not JSON: the status speaks
            }
        }
        if (code == null) {
            return "HTTP_" + status;
        }
        return AgentRunEntity.END_DETAIL.matcher(code).matches() ? code : AgentRunEntity.UNKNOWN_CODE;
    }

    private static String requiredText(JsonNode answer, String field) {
        JsonNode value = answer.path(field);
        if (!value.isTextual() || value.asText().isBlank()) {
            throw new IllegalArgumentException("missing " + field);
        }
        return value.asText();
    }

    private static HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        return headers;
    }
}
