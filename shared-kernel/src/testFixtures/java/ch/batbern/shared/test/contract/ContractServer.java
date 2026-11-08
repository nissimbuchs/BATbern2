package ch.batbern.shared.test.contract;

import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.RequestMatcher;
import org.springframework.test.web.client.ResponseCreator;
import org.springframework.test.web.client.UnorderedRequestExpectationManager;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriTemplate;
import org.springframework.web.util.UriUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

/**
 * S2S contract harness (Story 17.0b1): the real client runs on its real {@link RestTemplate},
 * every request it sends is validated against the provider's OpenAPI spec in {@code docs/api/},
 * and the answer comes from that spec's example, or from a test body that is validated against
 * the response schema first.
 *
 * <pre>{@code
 * var server = ContractServer.bindTo(restTemplate, "docs/api/companies-api.openapi.yml");
 * server.expect(GET, "/api/v1/companies").respondWithExample();
 * var companies = client.getAllCompanies();   // request validated, example returned
 * server.verify();
 * }</pre>
 *
 * <p>A request the provider would reject (unknown path or method, a parameter over its maximum, a
 * missing required field, a missing {@code Authorization} where the spec requires one) fails the
 * test with an {@link AssertionError} carrying the validator's message. {@code AssertionError} is
 * not an {@code Exception}, so a client's {@code catch (Exception e)} fallback cannot hide it.
 *
 * <p>Expectations match on method and path (concrete, or a spec template such as
 * {@code /api/v1/companies/{name}}), are unordered, and default to exactly once. In a Spring
 * test, call {@link #close()} after each test: the {@code RestTemplate} bean is shared by the
 * cached context, and {@code close()} gives it back its real request factory.
 */
public final class ContractServer implements AutoCloseable {

    private static final String SECURITY_MISSING = "[validation.request.security.missing]";

    private final RestTemplate restTemplate;
    private final ClientHttpRequestFactory originalFactory;
    private final OpenApiContract contract;
    private final MockRestServiceServer server;
    private boolean checkAuthorization = true;

    private ContractServer(RestTemplate restTemplate, OpenApiContract contract) {
        this.restTemplate = restTemplate;
        this.originalFactory = (ClientHttpRequestFactory) ReflectionTestUtils.getField(restTemplate, "requestFactory");
        this.contract = contract;
        this.server = MockRestServiceServer.bindTo(restTemplate).build(new SpecAwareExpectationManager());
    }

    /**
     * Binds a contract server to the client's {@code RestTemplate}, for the provider spec at
     * {@code specPath} (repository-relative, e.g. {@code docs/api/users-api.openapi.yml}).
     */
    public static ContractServer bindTo(RestTemplate restTemplate, String specPath) {
        return new ContractServer(restTemplate, OpenApiContract.load(specPath));
    }

    /**
     * Skips only the "security parameters missing" check. For integration tests whose caller is
     * a {@code @WithMockUser} (no JWT to forward), never for a client contract test: that one
     * must prove the client sends the JWT the spec asks for.
     */
    public ContractServer ignoringMissingAuthorization() {
        this.checkAuthorization = false;
        return this;
    }

    public OpenApiContract contract() {
        return contract;
    }

    /** Expects one request with this method on this path (concrete or spec template). */
    public Expectation expect(HttpMethod method, String path) {
        contract.requireOperation(method.name(), path);
        return new Expectation(method, path, true);
    }

    /**
     * Expects a call to an {@code /internal/**} path, which has no spec until Story 17.0b2: matched
     * by method and path, answered with {@link Expectation#respondWith(HttpStatus, String)}, but
     * <b>not validated</b>. Only for a dependency of the code under test (a provider test whose
     * controller looks a user up internally), never to test an internal client.
     */
    public Expectation expectInternalWithoutSpec(HttpMethod method, String path) {
        if (!path.startsWith("/internal/")) {
            throw new IllegalArgumentException(path + " is not an /internal path; use expect(...)");
        }
        return new Expectation(method, path, false);
    }

    /** Fails unless every expectation with a minimum count was met. */
    public void verify() {
        server.verify();
    }

    public void reset() {
        server.reset();
    }

    /** Gives the {@code RestTemplate} its real request factory back. */
    @Override
    public void close() {
        restTemplate.setRequestFactory(originalFactory);
    }

    // ------------------------------------------------------------------------------------------

    /** Validates a recorded request against the spec; throws with the validator's message. */
    private void validate(ClientHttpRequest request) {
        URI uri = request.getURI();
        String method = request.getMethod().name();
        String path = UriUtils.decode(uri.getRawPath(), StandardCharsets.UTF_8);
        Map<String, List<String>> query = new LinkedHashMap<>();
        UriComponentsBuilder.fromUri(uri).build().getQueryParams().forEach((name, values) ->
                query.put(decode(name), values.stream().map(v -> v == null ? "" : decode(v)).toList()));
        Map<String, List<String>> headers = new LinkedHashMap<>();
        request.getHeaders().forEach(headers::put);
        String body = request instanceof MockClientHttpRequest mock
                ? mock.getBodyAsString(StandardCharsets.UTF_8)
                : null;

        List<String> errors = contract.validateRequest(method, path, query, headers, body).stream()
                .filter(e -> checkAuthorization || !e.startsWith(SECURITY_MISSING))
                .toList();
        if (!errors.isEmpty()) {
            throw new AssertionError(method + " " + uri + " violates " + contract.specPath() + ":\n  - "
                    + String.join("\n  - ", errors));
        }
    }

    private static String decode(String value) {
        return UriUtils.decode(value, StandardCharsets.UTF_8);
    }

    /** Unordered matching; an unmatched request reports the spec's verdict on it as well. */
    private final class SpecAwareExpectationManager extends UnorderedRequestExpectationManager {
        @Override
        protected AssertionError createUnexpectedRequestError(ClientHttpRequest request) {
            AssertionError base = super.createUnexpectedRequestError(request);
            try {
                validate(request);
                return base;
            } catch (AssertionError specError) {
                return new AssertionError(base.getMessage() + "\n" + specError.getMessage());
            }
        }
    }

    /** One expected request. Nothing is registered until a {@code respond...} method is called. */
    public final class Expectation {
        private final HttpMethod method;
        private final String path;
        private final boolean specified;
        private final Map<String, String> queryParams = new LinkedHashMap<>();
        private ExpectedCount count = ExpectedCount.once();
        private Consumer<ClientHttpRequest> check = r -> { };

        private Expectation(HttpMethod method, String path, boolean specified) {
            this.method = method;
            this.path = path;
            this.specified = specified;
        }

        /** Also match on a query parameter value (for paged calls with one answer per page). */
        public Expectation withQueryParam(String name, String value) {
            queryParams.put(name, value);
            return this;
        }

        public Expectation times(ExpectedCount expectedCount) {
            this.count = expectedCount;
            return this;
        }

        /** Extra assertions on the request (headers, body), run after spec validation. */
        public Expectation andExpect(Consumer<ClientHttpRequest> assertion) {
            this.check = assertion;
            return this;
        }

        /** Answers with the success status's example from the spec. */
        public void respondWithExample() {
            int status = contract.successStatus(method.name(), path);
            respondWithJson(status, contract.example(method.name(), path, status, null));
        }

        /** Answers with the named example of the success status. */
        public void respondWithExample(String exampleName) {
            int status = contract.successStatus(method.name(), path);
            respondWithJson(status, contract.example(method.name(), path, status, exampleName));
        }

        /** Answers with a test body, after checking it against the response schema of {@code status}. */
        public void respondWith(HttpStatus status, String jsonBody) {
            if (!specified) {
                respondWithJson(status.value(), jsonBody);
                return;
            }
            String concrete = concretePath();
            List<String> errors = contract.validateResponse(method.name(), concrete, status.value(), jsonBody);
            if (!errors.isEmpty()) {
                throw new AssertionError("Test body for " + method + " " + path + " " + status.value()
                        + " violates " + contract.specPath() + ":\n  - " + String.join("\n  - ", errors));
            }
            respondWithJson(status.value(), jsonBody);
        }

        /** Answers with an error status the operation declares, without a body. */
        public void respondWithStatus(HttpStatus status) {
            if (specified && !contract.declaresStatus(method.name(), path, status.value())) {
                throw new AssertionError(method + " " + path + " does not declare status " + status.value()
                        + " in " + contract.specPath());
            }
            register(withStatus(status));
        }

        /**
         * Answers with a custom creator, for transport failures (throw a
         * {@code ResourceAccessException}). The request is still validated first.
         */
        public void respondWith(ResponseCreator creator) {
            register(creator);
        }

        private void respondWithJson(int status, String body) {
            register(withStatus(HttpStatus.valueOf(status)).contentType(MediaType.APPLICATION_JSON).body(body));
        }

        private void register(ResponseCreator creator) {
            RequestMatcher matcher = request -> {
                if (!request.getMethod().equals(method) || !pathMatches(request.getURI())) {
                    throw new AssertionError("not " + method + " " + path);
                }
                Map<String, List<String>> actual = UriComponentsBuilder.fromUri(request.getURI()).build()
                        .getQueryParams();
                queryParams.forEach((name, value) -> {
                    List<String> values = actual.get(name);
                    if (values == null || values.stream().map(v -> v == null ? "" : decode(v))
                            .noneMatch(value::equals)) {
                        throw new AssertionError("query " + name + " != " + value);
                    }
                });
            };
            server.expect(count, matcher).andRespond(request -> {
                if (specified) {
                    validate(request);
                }
                check.accept(request);
                return creator.createResponse(request);
            });
        }

        private boolean pathMatches(URI uri) {
            String actual = UriUtils.decode(uri.getRawPath(), StandardCharsets.UTF_8);
            return path.contains("{") ? new UriTemplate(path).matches(actual) : path.equals(actual);
        }

        /** A concrete path for response validation; template variables become "x". */
        private String concretePath() {
            return path.replaceAll("\\{[^}]+}", "x");
        }
    }
}
