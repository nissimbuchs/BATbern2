package ch.batbern.companyuser.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * #1237: calls event-management's {@code /internal/mail-center/merge-requests}. No JWT, service to
 * service in the VPC, like {@link HttpEventManagementUserMergeClient}.
 */
@Component
@Slf4j
public class HttpEventManagementMailCenterClient implements EventManagementMailCenterClient {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    @Autowired
    public HttpEventManagementMailCenterClient(RestTemplateBuilder builder,
            @Value("${event-management-service.base-url}") String baseUrl) {
        this(builder.connectTimeout(CONNECT_TIMEOUT).readTimeout(READ_TIMEOUT).build(), baseUrl);
    }

    HttpEventManagementMailCenterClient(RestTemplate restTemplate, String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
    }

    @Override
    public void openMergeRequest(OpenRequest request) {
        post(baseUrl + "/internal/mail-center/merge-requests", body(request));
    }

    @Override
    public void closeMergeRequest(UUID requestId, String decidedBy) {
        post(baseUrl + "/internal/mail-center/merge-requests/" + requestId + "/done", Map.of("decidedBy", decidedBy));
    }

    @Override
    public void openLogoRequest(OpenRequest request) {
        post(baseUrl + "/internal/mail-center/logo-requests", body(request));
    }

    @Override
    public void closeLogoRequest(UUID requestId, String decidedBy) {
        post(baseUrl + "/internal/mail-center/logo-requests/" + requestId + "/done", Map.of("decidedBy", decidedBy));
    }

    private static Map<String, Object> body(OpenRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("requestId", request.requestId());
        body.put("requesterName", request.requesterName());
        body.put("requesterEmail", request.requesterEmail());
        body.put("subject", request.subject());
        body.put("note", request.note());
        return body;
    }

    private void post(String url, Object body) {
        try {
            restTemplate.postForEntity(url, body, Void.class);
        } catch (RestClientException e) {
            log.warn("event-management mail-center call failed: cause={}", e.getClass().getSimpleName());
            throw new MailCenterCallFailedException("event-management not reachable: " + e.getClass().getSimpleName());
        }
    }
}
