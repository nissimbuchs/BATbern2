package ch.batbern.companyuser.client;

import ch.batbern.shared.contract.ContractCallFailedException;
import ch.batbern.shared.contract.ContractCalls;
import ch.batbern.shared.contract.ContractRefusedException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Calls the Mail Center request contract at every configured implementer through shared-kernel
 * {@link ContractCalls}: the user's JWT is forwarded when there is one (ADR-016 decision 3). An
 * opening stops at the first failing implementer (the filing fails); a decision goes to every
 * implementer, also after one failed.
 */
@Component
public class HttpMailCenterRequestContract implements MailCenterRequestContract {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);

    private final RestTemplate restTemplate;
    private final ContractCalls calls;

    @Autowired
    public HttpMailCenterRequestContract(RestTemplateBuilder builder, Environment environment) {
        this(builder.connectTimeout(CONNECT_TIMEOUT).readTimeout(READ_TIMEOUT).build(), environment);
    }

    private HttpMailCenterRequestContract(RestTemplate restTemplate, Environment environment) {
        this(restTemplate, ContractCalls.fromEnvironment(NAME, environment, restTemplate));
    }

    HttpMailCenterRequestContract(RestTemplate restTemplate, List<String> implementers) {
        this(restTemplate, new ContractCalls(NAME, implementers, restTemplate));
    }

    private HttpMailCenterRequestContract(RestTemplate restTemplate, ContractCalls calls) {
        this.restTemplate = restTemplate;
        this.calls = calls;
    }

    /** For the S2S contract harness in tests ({@code MailCenterContract}); the contract caller owns it. */
    RestTemplate restTemplate() {
        return restTemplate;
    }

    @Override
    public void opened(Opened request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("kind", request.kind().name());
        body.put("requestId", request.requestId());
        body.put("requesterName", request.requesterName());
        body.put("requesterEmail", request.requesterEmail());
        body.put("subject", request.subject());
        body.put("note", request.note());
        call(() -> calls.post("/internal/mail-center-requests", body));
    }

    @Override
    public void decided(UUID requestId, Decided decision) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("kind", decision.kind().name());
        body.put("status", decision.status());
        body.put("decidedBy", decision.decidedBy());
        body.put("decidedAt", decision.decidedAt().toString());
        call(() -> calls.postToEvery("/internal/mail-center-requests/{requestId}/decision", body, requestId));
    }

    private static void call(Runnable call) {
        try {
            call.run();
        } catch (ContractCallFailedException | ContractRefusedException e) {
            throw new MailCenterCallFailedException(e.getMessage(), e);
        }
    }
}
