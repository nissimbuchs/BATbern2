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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

/**
 * S2S contract harness (Story 17.0b1, internal specs 17.0b2): the real client runs on its real
 * {@link RestTemplate}, every request it sends is validated against the provider's OpenAPI spec
 * ({@code docs/api/} for the public API, {@code docs/api/internal/} for {@code /internal/**}), and
 * the answer comes from that spec's example, or from a test body that is validated against the
 * response schema first.
 *
 * <pre>{@code
 * var server = ContractServer.bindTo(restTemplate, "docs/api/internal/cums-internal.openapi.yml");
 * server.expect(GET, "/internal/companies/{name}").respondWithExample();
 * var name = client.getCompanyDisplayName("swissitsolut");   // request validated, example returned
 * server.verify();
 * }</pre>
 *
 * <p>A request the provider would reject (unknown path or method, a parameter over its maximum, a
 * missing required field, a missing {@code Authorization} where the spec requires one) fails the
 * test with an {@link AssertionError} carrying the validator's message. {@code AssertionError} is
 * not an {@code Exception}, so a client's {@code catch (Exception e)} fallback cannot hide it.
 *
 * <p>A client whose one {@code RestTemplate} talks to several specs (CUMS public and internal, for
 * instance) binds them all: {@code bindTo(restTemplate, USERS, CUMS_INTERNAL)}. Each request is
 * checked against the spec that has its path; a path no bound spec has fails, naming method, path
 * and specs. There is no unvalidated way to answer a call: every {@code /internal} operation has
 * its spec in {@code docs/api/internal/}.
 *
 * <p>Expectations match on method and path (concrete, or a spec template such as
 * {@code /internal/companies/{name}}), are unordered, and default to exactly once. In a Spring
 * test, call {@link #close()} after each test: the {@code RestTemplate} bean is shared by the
 * cached context, and {@code close()} gives it back its real request factory.
 */
public final class ContractServer implements AutoCloseable {

    private static final String SECURITY_MISSING = "[validation.request.security.missing]";

    private final RestTemplate restTemplate;
    private final ClientHttpRequestFactory originalFactory;
    private final List<OpenApiContract> contracts;
    private final MockRestServiceServer server;
    private boolean checkAuthorization = true;

    private ContractServer(RestTemplate restTemplate, List<OpenApiContract> contracts) {
        this.restTemplate = restTemplate;
        this.originalFactory = (ClientHttpRequestFactory) ReflectionTestUtils.getField(restTemplate, "requestFactory");
        this.contracts = contracts;
        this.server = MockRestServiceServer.bindTo(restTemplate).build(new SpecAwareExpectationManager());
    }

    /**
     * Binds a contract server to the client's {@code RestTemplate}, for the provider spec(s) at
     * {@code specPaths} (repository-relative, e.g. {@code docs/api/users-api.openapi.yml},
     * {@code docs/api/internal/cums-internal.openapi.yml}).
     */
    public static ContractServer bindTo(RestTemplate restTemplate, String specPath, String... moreSpecPaths) {
        List<OpenApiContract> contracts = new ArrayList<>();
        contracts.add(OpenApiContract.load(specPath));
        for (String more : moreSpecPaths) {
            contracts.add(OpenApiContract.load(more));
        }
        return new ContractServer(restTemplate, List.copyOf(contracts));
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

    /** The first bound spec. */
    public OpenApiContract contract() {
        return contracts.get(0);
    }

    /**
     * Expects one request with this method on this path (concrete or spec template). Fails at once
     * when no bound spec has the operation.
     */
    public Expectation expect(HttpMethod method, String path) {
        OpenApiContract owner = contractFor(path).orElseThrow(() -> new AssertionError(
                method + " " + path + " is not an operation of " + specNames() + " (no such path)"));
        owner.requireOperation(method.name(), path);
        return new Expectation(owner, method, path);
    }

    private Optional<OpenApiContract> contractFor(String path) {
        return contracts.stream().filter(c -> c.hasPath(path)).findFirst();
    }

    private String specNames() {
        return contracts.stream().map(OpenApiContract::specPath).collect(Collectors.joining(", "));
    }

    /** Fails unless every expectation with a minimum count was met. */
    public void verify() {
        server.verify();
    }

    public void reset() {
        server.reset();
    }

    /**
     * Sends only the requests {@code routed} accepts to this contract server; every other request
     * goes through the {@code RestTemplate}'s real request factory, exactly as without the
     * harness. For a harness bound once to a {@code RestTemplate} that a whole Spring test context
     * shares with other clients (EMS: CUMS {@code /internal} on the harness, PCS untouched).
     */
    public ContractServer onlyFor(Predicate<URI> routed) {
        ClientHttpRequestFactory mockFactory =
                (ClientHttpRequestFactory) ReflectionTestUtils.getField(restTemplate, "requestFactory");
        restTemplate.setRequestFactory((uri, httpMethod) -> routed.test(uri)
                ? mockFactory.createRequest(uri, httpMethod)
                : originalFactory.createRequest(uri, httpMethod));
        return this;
    }

    /** Gives the {@code RestTemplate} its real request factory back. */
    @Override
    public void close() {
        restTemplate.setRequestFactory(originalFactory);
    }

    // ------------------------------------------------------------------------------------------

    /** One request as a per-request answer sees it: decoded path variables, query, body. */
    public record Call(Map<String, String> pathVariables, Map<String, List<String>> query, String body) {
        /** The first value of a query parameter, or {@code null}. */
        public String queryParam(String name) {
            List<String> values = query.get(name);
            return values == null || values.isEmpty() ? null : values.get(0);
        }
    }

    /** A per-request answer: status and JSON body ({@code null} for none). */
    public record Reply(HttpStatus status, String body) {
        public static Reply ok(String json) {
            return new Reply(HttpStatus.OK, json);
        }
    }

    /** Validates a recorded request against the spec; throws with the validator's message. */
    private void validate(ClientHttpRequest request) {
        URI uri = request.getURI();
        String method = request.getMethod().name();
        String path = decodedPath(uri);
        Map<String, List<String>> query = new LinkedHashMap<>();
        UriComponentsBuilder.fromUri(uri).build().getQueryParams().forEach((name, values) ->
                query.put(decode(name), values.stream().map(v -> v == null ? "" : decode(v)).toList()));
        Map<String, List<String>> headers = new LinkedHashMap<>();
        request.getHeaders().forEach(headers::put);
        String body = request instanceof MockClientHttpRequest mock
                ? mock.getBodyAsString(StandardCharsets.UTF_8)
                : null;

        OpenApiContract contract = contractFor(path).orElseThrow(() -> new AssertionError(
                method + " " + path + " is not an operation of " + specNames() + " (no such path; request "
                        + uri + ")"));
        List<String> errors;
        try {
            errors = contract.validateRequest(method, path, query, headers, body).stream()
                    .filter(e -> checkAuthorization || !e.startsWith(SECURITY_MISSING))
                    .toList();
        } catch (RuntimeException e) {
            // An Error, so a client's catch (Exception e) cannot turn a harness failure into a fallback.
            throw new AssertionError("Cannot validate " + method + " " + uri + " against " + contract.specPath(), e);
        }
        if (!errors.isEmpty()) {
            throw new AssertionError(method + " " + uri + " violates " + contract.specPath() + ":\n  - "
                    + String.join("\n  - ", errors));
        }
    }

    private static String decode(String value) {
        return UriUtils.decode(value, StandardCharsets.UTF_8);
    }

    /**
     * The path with each segment decoded, so {@code /companies/Swiss%20IT} is checked as
     * {@code /companies/Swiss IT}. A {@code /} or {@code %} inside a segment stays encoded
     * ({@code %2F}, {@code %25}): the segment is one path parameter, as the provider sees it, and
     * the validator's own decoding gets it back intact.
     */
    private static String decodedPath(URI uri) {
        String[] segments = uri.getRawPath().split("/", -1);
        StringBuilder path = new StringBuilder();
        for (int i = 0; i < segments.length; i++) {
            if (i > 0) {
                path.append('/');
            }
            path.append(UriUtils.decode(segments[i], StandardCharsets.UTF_8).replace("%", "%25").replace("/", "%2F"));
        }
        return path.toString();
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
        private final OpenApiContract contract;
        private final HttpMethod method;
        private final String path;
        private final Map<String, String> queryParams = new LinkedHashMap<>();
        private String host;
        private ExpectedCount count = ExpectedCount.once();
        private Consumer<ClientHttpRequest> check = r -> { };

        private Expectation(OpenApiContract contract, HttpMethod method, String path) {
            this.contract = contract;
            this.method = method;
            this.path = path;
        }

        /** Also match on a query parameter value (for paged calls with one answer per page). */
        public Expectation withQueryParam(String name, String value) {
            queryParams.put(name, value);
            return this;
        }

        /**
         * Also match on the host, for a contract with several implementers configured on one
         * {@code RestTemplate} (each implementer answers its own call).
         */
        public Expectation atHost(String expectedHost) {
            this.host = expectedHost;
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

        /**
         * Answers with the example of a declared non-success status (an error the client must
         * handle, such as 409 {@code MERGE_BLOCKED}); the first example when there are several.
         */
        public void respondWithExample(HttpStatus status) {
            respondWithJson(status.value(), contract.example(method.name(), path, status.value(), null));
        }

        /** Answers with a test body, after checking it against the response schema of {@code status}. */
        public void respondWith(HttpStatus status, String jsonBody) {
            checkBody(status, jsonBody);
            respondWithJson(status.value(), jsonBody);
        }

        /**
         * Answers each request with a body built from the {@link Call} (decoded path variables of
         * the expectation's template, such as {@code /internal/users/{username}}, query parameters,
         * body). Every body is checked against the response schema of {@code status} before it is
         * sent. For shared integration-test harnesses that answer many different users.
         */
        public void respondPerRequest(HttpStatus status, Function<Call, String> body) {
            respondPerRequest(call -> new Reply(status, body.apply(call)));
        }

        /**
         * Like {@link #respondPerRequest(HttpStatus, Function)}, with the status chosen per call
         * (a 404 for an unknown user). A {@code null} body answers without one; the status must
         * then be one the operation declares.
         */
        public void respondPerRequest(Function<Call, Reply> answer) {
            UriTemplate template = new UriTemplate(path);
            register(request -> {
                Map<String, String> vars = new LinkedHashMap<>();
                template.match(decodedPath(request.getURI())).forEach((k, v) -> vars.put(k, decode(v)));
                Map<String, List<String>> query = new LinkedHashMap<>();
                UriComponentsBuilder.fromUri(request.getURI()).build().getQueryParams().forEach((name, values) ->
                        query.put(decode(name), values.stream().map(v -> v == null ? "" : decode(v)).toList()));
                String requestBody = request instanceof MockClientHttpRequest mock
                        ? mock.getBodyAsString(StandardCharsets.UTF_8) : null;
                Reply reply = answer.apply(new Call(vars, query, requestBody));
                if (reply.body() == null) {
                    if (!contract.declaresStatus(method.name(), path, reply.status().value())) {
                        throw new AssertionError(method + " " + path + " does not declare status "
                                + reply.status().value() + " in " + contract.specPath());
                    }
                    return withStatus(reply.status()).createResponse(request);
                }
                checkBody(reply.status(), reply.body());
                return withStatus(reply.status()).contentType(MediaType.APPLICATION_JSON).body(reply.body())
                        .createResponse(request);
            });
        }

        private void checkBody(HttpStatus status, String jsonBody) {
            List<String> errors = contract.validateResponse(method.name(), concretePath(), status.value(), jsonBody);
            if (!errors.isEmpty()) {
                throw new AssertionError("Test body for " + method + " " + path + " " + status.value()
                        + " violates " + contract.specPath() + ":\n  - " + String.join("\n  - ", errors));
            }
        }

        /** Answers with an error status the operation declares, without a body. */
        public void respondWithStatus(HttpStatus status) {
            if (!contract.declaresStatus(method.name(), path, status.value())) {
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
                if (host != null && !host.equals(request.getURI().getHost())) {
                    throw new AssertionError("not at host " + host);
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
                validate(request);
                check.accept(request);
                return creator.createResponse(request);
            });
        }

        private boolean pathMatches(URI uri) {
            String actual = decodedPath(uri);
            return path.contains("{") ? new UriTemplate(path).matches(actual) : path.equals(actual);
        }

        /** A concrete path for response validation; template variables become "x". */
        private String concretePath() {
            return path.replaceAll("\\{[^}]+}", "x");
        }
    }
}
