package ch.batbern.companyuser.client;

import ch.batbern.shared.contract.ContractCallFailedException;
import ch.batbern.shared.contract.ContractCalls;
import ch.batbern.shared.contract.ContractRefusedException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Calls the company-reference contract at every configured holder through shared-kernel
 * {@link ContractCalls}: the user's JWT is forwarded when there is one (ADR-016 decision 3). The
 * read timeout is long because a large company has a few thousand registrations, moved in one
 * transaction per holder.
 */
@Component
public class HttpCompanyReferenceContract implements CompanyReferenceContract {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(30);
    private static final String PREVIEW = "/internal/company-references/preview?source={source}&target={target}";
    private static final String MERGE = "/internal/company-references/merge";

    private final RestTemplate restTemplate;
    private final ContractCalls calls;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    public HttpCompanyReferenceContract(RestTemplateBuilder builder, Environment environment) {
        this(builder.connectTimeout(CONNECT_TIMEOUT).readTimeout(READ_TIMEOUT).build(), environment);
    }

    private HttpCompanyReferenceContract(RestTemplate restTemplate, Environment environment) {
        this(restTemplate, ContractCalls.fromEnvironment(NAME, environment, restTemplate));
    }

    HttpCompanyReferenceContract(RestTemplate restTemplate, List<String> holders) {
        this(restTemplate, new ContractCalls(NAME, holders, restTemplate));
    }

    private HttpCompanyReferenceContract(RestTemplate restTemplate, ContractCalls calls) {
        this.restTemplate = restTemplate;
        this.calls = calls;
    }

    /** For the S2S contract harness in tests ({@code CompanyReferenceHolders}); the contract caller owns it. */
    RestTemplate restTemplate() {
        return restTemplate;
    }

    @Override
    public List<Preview> preview(String sourceKey, String targetKey) {
        requireHolders();
        Map<String, String> keys = Map.of("source", sourceKey, "target", targetKey);
        List<String> answers = call(() -> calls.get(PREVIEW, keys, String.class));
        List<Preview> previews = new ArrayList<>();
        for (String answer : answers) {
            JsonNode body = json(answer);
            previews.add(new Preview(counts(body.path("moves")), counts(body.path("conflicts")),
                    strings(body.path("blockers"))));
        }
        return previews;
    }

    @Override
    public List<Result> merge(MergeCall call) {
        requireHolders();
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("source", call.sourceKey());
        request.put("target", call.targetKey());
        request.put("performedBy", call.performedBy());
        request.put("trigger", call.trigger().name());
        List<String> answers = call(() -> calls.postForAnswers(MERGE, request, String.class));
        List<Result> results = new ArrayList<>();
        for (String answer : answers) {
            JsonNode body = json(answer);
            results.add(new Result(counts(body.path("moved")), counts(body.path("removed")),
                    keys(body.path("movedKeys")), keys(body.path("removedKeys"))));
        }
        return results;
    }

    /**
     * Without a holder a merge would delete the source company and leave every row naming it
     * behind: a misconfiguration, so preview and merge fail instead of going nowhere.
     */
    private void requireHolders() {
        if (calls.implementers().isEmpty()) {
            throw new CallFailedException("contract " + NAME + ": no holder configured ("
                    + ContractCalls.PROPERTY_PREFIX + NAME + ".implementers)", null);
        }
    }

    private static <T> T call(Supplier<T> call) {
        try {
            return call.get();
        } catch (ContractRefusedException e) {
            throw new MoveBlockedException(e.getMessage());
        } catch (ContractCallFailedException e) {
            throw new CallFailedException(e.getMessage(), e);
        }
    }

    private JsonNode json(String body) {
        try {
            return objectMapper.readTree(body == null ? "{}" : body);
        } catch (IOException e) {
            throw new CallFailedException("contract " + NAME + ": unreadable answer", e);
        }
    }

    private static Map<String, Integer> counts(JsonNode node) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        node.properties().forEach(e -> counts.put(e.getKey(), e.getValue().asInt()));
        return counts;
    }

    private static Map<String, List<String>> keys(JsonNode node) {
        Map<String, List<String>> keys = new LinkedHashMap<>();
        node.properties().forEach(e -> keys.put(e.getKey(), strings(e.getValue())));
        return keys;
    }

    private static List<String> strings(JsonNode node) {
        List<String> values = new ArrayList<>();
        node.forEach(n -> values.add(n.asText()));
        return values;
    }
}
