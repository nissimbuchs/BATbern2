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
 * Calls the user-reference contract at every configured holder through shared-kernel
 * {@link ContractCalls}: the user's JWT is forwarded when there is one (ADR-016 decision 3). The
 * read timeout is long because a merge of a profile with years of history updates a few thousand
 * rows at most, in one transaction per holder.
 */
@Component
public class HttpUserReferenceContract implements UserReferenceContract {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(30);

    private final RestTemplate restTemplate;
    private final ContractCalls calls;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    public HttpUserReferenceContract(RestTemplateBuilder builder, Environment environment) {
        this(builder.connectTimeout(CONNECT_TIMEOUT).readTimeout(READ_TIMEOUT).build(), environment);
    }

    private HttpUserReferenceContract(RestTemplate restTemplate, Environment environment) {
        this(restTemplate, ContractCalls.fromEnvironment(NAME, environment, restTemplate));
    }

    HttpUserReferenceContract(RestTemplate restTemplate, List<String> holders) {
        this(restTemplate, new ContractCalls(NAME, holders, restTemplate));
    }

    private HttpUserReferenceContract(RestTemplate restTemplate, ContractCalls calls) {
        this.restTemplate = restTemplate;
        this.calls = calls;
    }

    /** For the S2S contract harness in tests ({@code UserReferenceHolders}); the contract caller owns it. */
    RestTemplate restTemplate() {
        return restTemplate;
    }

    @Override
    public List<Preview> preview(String sourceUsername, String targetUsername) {
        requireHolders();
        List<String> answers = call(() -> calls.get("/internal/user-references/preview?source={source}&target={target}",
                Map.of("source", sourceUsername, "target", targetUsername), String.class));
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
        request.put("sourceUsername", call.sourceUsername());
        request.put("targetUsername", call.targetUsername());
        request.put("performedBy", call.performedBy());
        request.put("trigger", call.trigger());
        request.put("sourceProfile", call.sourceProfile());
        List<String> answers = call(() -> calls.postForAnswers("/internal/user-references/merge", request,
                String.class));
        List<Result> results = new ArrayList<>();
        for (String answer : answers) {
            JsonNode body = json(answer);
            results.add(new Result(counts(body.path("moved")), counts(body.path("removed"))));
        }
        return results;
    }

    @Override
    public void primaryEmailChanged(String username, String oldEmail, String newEmail) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("username", username);
        request.put("oldEmail", oldEmail);
        request.put("newEmail", newEmail);
        // Every holder is told, also when an earlier one failed; the failures come back as one.
        call(() -> {
            calls.postToEvery("/internal/user-references/primary-email", request);
            return null;
        });
    }

    /**
     * Without a holder a merge would delete the source and leave every row naming it behind: a
     * misconfiguration, so preview and merge fail (502) instead of going nowhere.
     */
    private void requireHolders() {
        if (calls.implementers().isEmpty()) {
            throw new MergeCallFailedException("contract " + NAME + ": no holder configured ("
                    + ContractCalls.PROPERTY_PREFIX + NAME + ".implementers)", null);
        }
    }

    private static <T> T call(Supplier<T> call) {
        try {
            return call.get();
        } catch (ContractRefusedException e) {
            throw new MergeBlockedException(e.getMessage());
        } catch (ContractCallFailedException e) {
            throw new MergeCallFailedException(e.getMessage(), e);
        }
    }

    private JsonNode json(String body) {
        try {
            return objectMapper.readTree(body == null ? "{}" : body);
        } catch (IOException e) {
            throw new MergeCallFailedException("contract " + NAME + ": unreadable answer", e);
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
