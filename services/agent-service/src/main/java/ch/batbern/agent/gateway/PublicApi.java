package ch.batbern.agent.gateway;

import ch.batbern.agent.identity.RunCredentials;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * The public API as one run's tools see it (story 4, R2, AD-3): every call goes to the Spring
 * gateway under {@code /api/v1} with the run's CUMS-minted agent token as {@code Authorization:
 * Bearer}. A GET reuses the run's token (minted again shortly before it expires); every other
 * method is a write and mints a fresh token first, so a write never reuses one. When CUMS refuses
 * that mint, the write is not sent and {@link ch.batbern.agent.identity.AgentCredentialsRefusedException}
 * is thrown; the run's credentials keep the refusal, so the loop ends the run even if the tool
 * swallows the exception.
 *
 * <p>An HTTP error of the API is returned as an {@link ApiResponse}, not thrown; a transport error
 * is thrown. One instance per run. Not a {@code client} package: the S2S URL guard reserves those
 * for service-to-service clients, which must call {@code /internal}; this is the agent acting as a
 * user, which the module map requires to use the public API.
 */
public final class PublicApi {

    /** The public API's prefix behind the gateway. */
    static final String PUBLIC_PREFIX = "/api/v1";

    /** {@code %2e}, {@code %2f}, {@code %5c} in any case: a dot, slash or backslash smuggled past the check. */
    private static final Pattern ENCODED_DOT_SLASH_OR_BACKSLASH = Pattern.compile("%(2e|2f|5c)",
            Pattern.CASE_INSENSITIVE);

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final RunCredentials credentials;

    public PublicApi(RestTemplate restTemplate, String gatewayBaseUrl, RunCredentials credentials) {
        this.restTemplate = Objects.requireNonNull(restTemplate, "restTemplate");
        this.baseUrl = Objects.requireNonNull(gatewayBaseUrl, "gatewayBaseUrl") + PUBLIC_PREFIX;
        this.credentials = Objects.requireNonNull(credentials, "credentials");
    }

    /** A read. {@code path} is below {@code /api/v1}, already encoded, query included: {@code /companies?limit=5}. */
    public ApiResponse get(String path) {
        return exchange(HttpMethod.GET, path, null);
    }

    /** A write with a JSON body. */
    public ApiResponse post(String path, JsonNode body) {
        return exchange(HttpMethod.POST, path, body);
    }

    /** A write with a JSON body. */
    public ApiResponse put(String path, JsonNode body) {
        return exchange(HttpMethod.PUT, path, body);
    }

    /** A write with a JSON body. */
    public ApiResponse patch(String path, JsonNode body) {
        return exchange(HttpMethod.PATCH, path, body);
    }

    /** A write without a body. */
    public ApiResponse delete(String path) {
        return exchange(HttpMethod.DELETE, path, null);
    }

    /**
     * Any call: GET is a read, every other method a write.
     *
     * @param body the JSON body, or {@code null} for none
     */
    public ApiResponse exchange(HttpMethod method, String path, JsonNode body) {
        URI uri = uri(path);
        // The token first: a refused mint throws before anything is sent
        String token = HttpMethod.GET.equals(method) ? credentials.tokenForRead() : credentials.tokenForWrite();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        if (body != null) {
            headers.setContentType(MediaType.APPLICATION_JSON);
        }
        try {
            ResponseEntity<String> response = restTemplate.exchange(uri, method,
                    new HttpEntity<>(body == null ? null : body.toString(), headers), String.class);
            return new ApiResponse(response.getStatusCode().value(), response.getBody());
        } catch (RestClientResponseException e) {
            String errorBody = e.getResponseBodyAsString();
            return new ApiResponse(e.getStatusCode().value(), errorBody.isEmpty() ? null : errorBody);
        }
    }

    private URI uri(String path) {
        if (path == null || !path.startsWith("/") || path.startsWith("//") || path.contains("..")
                || path.contains("\\") || ENCODED_DOT_SLASH_OR_BACKSLASH.matcher(path).find()) {
            throw new IllegalArgumentException(
                    "A public API path starts with one / and has no dot segment, encoded dot, slash or backslash");
        }
        return URI.create(baseUrl + path);
    }
}
