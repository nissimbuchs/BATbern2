package ch.batbern.companyuser.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * #1183 C/D: calls event-management's {@code /internal/users/merge*}. No JWT: service to service in
 * the VPC through Service Connect, like partner-coordination's {@code HttpOutboundMailRecorder};
 * {@code /internal/**} is not routed by the API gateway. The read timeout is long because a merge of
 * a profile with years of history updates a few thousand rows at most, in one transaction.
 */
@Component
@Slf4j
public class HttpEventManagementUserMergeClient implements EventManagementUserMergeClient {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(30);

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    public HttpEventManagementUserMergeClient(RestTemplateBuilder builder,
            @Value("${event-management-service.base-url}") String baseUrl) {
        this(builder.connectTimeout(CONNECT_TIMEOUT).readTimeout(READ_TIMEOUT).build(), baseUrl);
    }

    HttpEventManagementUserMergeClient(RestTemplate restTemplate, String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
    }

    /** For the S2S contract harness in tests ({@code EventManagementContract}); the client owns it. */
    RestTemplate restTemplate() {
        return restTemplate;
    }

    @Override
    public Preview preview(String sourceUsername, String targetUsername) {
        // Strictly encoded (17.0b2): built without encode() before, so a '+' arrived as a space and
        // a '&' or '#' cut the query.
        URI url = UriComponentsBuilder.fromUriString(baseUrl + "/internal/users/merge-preview")
                .queryParam("source", "{source}").queryParam("target", "{target}")
                .encode().buildAndExpand(sourceUsername, targetUsername).toUri();
        JsonNode body = call(() -> restTemplate.getForObject(url, String.class));
        return new Preview(counts(body.path("moves")), counts(body.path("conflicts")), strings(body.path("blockers")));
    }

    @Override
    public Result merge(MergeCall call) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("sourceUsername", call.sourceUsername());
        request.put("targetUsername", call.targetUsername());
        request.put("performedBy", call.performedBy());
        request.put("trigger", call.trigger());
        request.put("sourceProfile", call.sourceProfile());
        JsonNode body = call(() -> restTemplate.postForObject(baseUrl + "/internal/users/merge", request,
                String.class));
        return new Result(counts(body.path("moved")), counts(body.path("removed")));
    }

    @Override
    public void primaryEmailChanged(String username, String oldEmail, String newEmail) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("username", username);
        request.put("oldEmail", oldEmail);
        request.put("newEmail", newEmail);
        call(() -> restTemplate.postForObject(baseUrl + "/internal/users/primary-email", request, String.class));
    }

    private JsonNode call(java.util.function.Supplier<String> request) {
        try {
            String body = request.get();
            return objectMapper.readTree(body == null ? "{}" : body);
        } catch (HttpStatusCodeException e) {
            if (e.getStatusCode().value() == 409) {
                String message = e.getResponseBodyAsString();
                try {
                    message = objectMapper.readTree(message).path("message").asText(message);
                } catch (Exception ignored) {
                    // keep the raw body
                }
                throw new MergeBlockedException(message);
            }
            log.warn("event-management merge call failed: status={}", e.getStatusCode().value());
            throw new MergeCallFailedException("event-management answered " + e.getStatusCode().value());
        } catch (MergeBlockedException e) {
            throw e;
        } catch (RuntimeException | java.io.IOException e) {
            log.warn("event-management merge call failed: cause={}", e.getClass().getSimpleName());
            throw new MergeCallFailedException("event-management not reachable: " + e.getClass().getSimpleName());
        }
    }

    private static Map<String, Integer> counts(JsonNode node) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        node.properties().forEach(e -> counts.put(e.getKey(), e.getValue().asInt()));
        return counts;
    }

    private static List<String> strings(JsonNode node) {
        List<String> values = new ArrayList<>();
        node.forEach(n -> values.add(n.asText()));
        return values;
    }
}
