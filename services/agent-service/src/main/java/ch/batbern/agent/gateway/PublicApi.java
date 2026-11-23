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
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * The public API as one run's tools see it (story 4, R2, AD-3): every call goes to the Spring
 * gateway under {@code /api/v1} with the run's CUMS-minted agent token as {@code Authorization:
 * Bearer}. A GET reuses the run's token (minted again shortly before it expires); every other
 * method is a write and mints a fresh token first, so a write never reuses one. When CUMS refuses
 * that mint, the write is not sent and {@link ch.batbern.agent.identity.AgentCredentialsRefusedException}
 * is thrown; the run's credentials keep the refusal, so the loop ends the run even if the tool
 * swallows the exception.
 *
 * <p>A tool gets the run's instance {@link #limitedTo limited to} the operations it declares
 * (story 5, AD-6): any other call is refused before a token is touched, so a tool's declared
 * operations, from which its class is derived, are the only ones it can reach.
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
    /** The operations this instance lets through; {@code null}: any. */
    private final Set<Operation> allowed;

    public PublicApi(RestTemplate restTemplate, String gatewayBaseUrl, RunCredentials credentials) {
        this(restTemplate, Objects.requireNonNull(gatewayBaseUrl, "gatewayBaseUrl") + PUBLIC_PREFIX, credentials,
                null);
    }

    private PublicApi(RestTemplate restTemplate, String baseUrl, RunCredentials credentials, Set<Operation> allowed) {
        this.restTemplate = Objects.requireNonNull(restTemplate, "restTemplate");
        this.baseUrl = baseUrl;
        this.credentials = Objects.requireNonNull(credentials, "credentials");
        this.allowed = allowed;
    }

    /**
     * The same run's API, letting through only {@code operations} (paths below {@code /api/v1});
     * any other call throws {@link IllegalStateException} before a token is read or minted. A limit
     * only narrows: on an already limited instance the result lets through only operations both
     * allow, so a tool cannot widen the API it was handed.
     */
    public PublicApi limitedTo(Set<Operation> operations) {
        Set<Operation> narrowed = Set.copyOf(operations);
        if (allowed != null) {
            narrowed = narrowed.stream().filter(allowed::contains).collect(Collectors.toUnmodifiableSet());
        }
        return new PublicApi(restTemplate, baseUrl, credentials, narrowed);
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
        if (allowed != null && allowed.stream().noneMatch(operation -> operation.matches(method, path))) {
            // The path is the tool's text, possibly with model input: method only
            throw new IllegalStateException("The tool called a " + method.name()
                    + " operation it does not declare");
        }
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

    /**
     * The guard looks at the path part only: a query value may carry an encoded slash or dot (a
     * title searched for), which cannot change the route. The query may not hold a fragment.
     */
    private URI uri(String path) {
        int query = path == null ? -1 : path.indexOf('?');
        String pathPart = query < 0 ? path : path.substring(0, query);
        if (pathPart == null || !pathPart.startsWith("/") || pathPart.startsWith("//") || pathPart.contains("..")
                || pathPart.contains("\\") || ENCODED_DOT_SLASH_OR_BACKSLASH.matcher(pathPart).find()
                || path.contains("#")) {
            throw new IllegalArgumentException("A public API path starts with one / and has no dot segment, "
                    + "encoded dot, slash or backslash, and no fragment");
        }
        return URI.create(baseUrl + path);
    }
}
