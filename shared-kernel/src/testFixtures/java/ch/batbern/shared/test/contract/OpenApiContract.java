package ch.batbern.shared.test.contract;

import com.atlassian.oai.validator.OpenApiInteractionValidator;
import com.atlassian.oai.validator.model.Request;
import com.atlassian.oai.validator.model.SimpleRequest;
import com.atlassian.oai.validator.model.SimpleResponse;
import com.atlassian.oai.validator.report.LevelResolver;
import com.atlassian.oai.validator.report.ValidationReport;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * One provider OpenAPI spec from {@code docs/api/}, loaded once per JVM: validates requests and
 * response bodies against it and hands out its examples (Story 17.0b1).
 *
 * <p>Used by {@link ContractServer} on the consumer side and by {@link ProviderContract} on the
 * provider side, so both sides check against the same file. Validation is
 * {@code com.atlassian.oai:openapi-request-validator-core}; every message it reports counts as an
 * error.
 *
 * <p>Paths are the full request paths, base path included. The base path is the path of the
 * spec's first server URL: {@code /api/v1} for the public specs in {@code docs/api/}
 * ({@code /api/v1/companies}), empty for the internal specs in {@code docs/api/internal/}, which
 * declare server {@code /} and full paths ({@code /internal/users/{username}}, Story 17.0b2).
 */
public final class OpenApiContract {

    private static final Map<String, OpenApiContract> CACHE = new ConcurrentHashMap<>();
    private static final ObjectMapper JSON = new ObjectMapper();

    private final String specPath;
    private final String basePath;
    private final OpenAPI openApi;
    private final OpenApiInteractionValidator validator;
    private final List<Route> routes;

    private OpenApiContract(String specPath, Path file) {
        this.specPath = specPath;
        ParseOptions options = new ParseOptions();
        options.setResolve(true);
        options.setResolveFully(true);
        SwaggerParseResult parsed = new OpenAPIV3Parser().readLocation(file.toUri().toString(), null, options);
        if (parsed.getOpenAPI() == null) {
            throw new IllegalStateException("Cannot parse " + specPath + ": " + parsed.getMessages());
        }
        this.openApi = parsed.getOpenAPI();
        this.basePath = basePathOf(openApi, specPath);
        // The spec as written (references and allOf kept): full resolution merges a 3.0
        // `allOf: [$ref]` into the referenced schema and drops the `nullable` next to it.
        ParseOptions asWritten = new ParseOptions();
        asWritten.setResolve(false);
        GeneratedCodeSemantics.apply(openApi,
                new OpenAPIV3Parser().readLocation(file.toUri().toString(), null, asWritten).getOpenAPI());
        this.validator = OpenApiInteractionValidator
                .createFor(openApi)
                .withBasePathOverride(basePath.isEmpty() ? "/" : basePath)
                // Merge allOf into one schema: otherwise the library adds additionalProperties:false to
                // each part and rejects every property of the other parts (EventDetail = Event + more).
                .withResolveCombinators(true)
                // An unknown query parameter is drift (a client sending `size` where the provider
                // reads `limit`); the library ignores it by default.
                .withLevelResolver(LevelResolver.create()
                        .withLevel("validation.request.parameter.query.unexpected", ValidationReport.Level.ERROR)
                        .build())
                .build();
        this.routes = openApi.getPaths().entrySet().stream()
                .map(e -> new Route(e.getKey(), e.getValue()))
                .sorted(Comparator.comparingInt(Route::variables).thenComparing(Route::template))
                .toList();
    }

    /**
     * The spec at {@code specPath}, relative to the repository root (for example
     * {@code docs/api/companies-api.openapi.yml} or
     * {@code docs/api/internal/cums-internal.openapi.yml}). Looked up from the working directory upwards,
     * so it works from the root and from a module directory alike.
     */
    public static OpenApiContract load(String specPath) {
        return CACHE.computeIfAbsent(specPath, p -> new OpenApiContract(p, locate(p)));
    }

    private static Path locate(String specPath) {
        Path dir = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        for (Path d = dir; d != null; d = d.getParent()) {
            Path candidate = d.resolve(specPath);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Spec " + specPath + " not found from " + dir + " upwards");
    }

    public String specPath() {
        return specPath;
    }

    /** The base path every operation path starts with: {@code /api/v1}, or empty (server {@code /}). */
    public String basePath() {
        return basePath;
    }

    /** Path of the first server URL, without a trailing slash; empty for {@code /} or no server. */
    private static String basePathOf(OpenAPI openApi, String specPath) {
        if (openApi.getServers() == null || openApi.getServers().isEmpty()) {
            return "";
        }
        String url = openApi.getServers().get(0).getUrl();
        String path;
        try {
            path = URI.create(url).getPath();
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Server URL " + url + " of " + specPath + " is not a URI", e);
        }
        if (path == null) {
            return "";
        }
        return path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
    }

    // ------------------------------------------------------------------------------------------
    // Operations
    // ------------------------------------------------------------------------------------------

    /** The operation for a concrete request path or a spec template, or empty when none exists. */
    public Optional<Operation> operation(String method, String path) {
        return route(path).map(r -> r.item.readOperationsMap()
                .get(PathItem.HttpMethod.valueOf(method.toUpperCase(Locale.ROOT))));
    }

    /**
     * The number of path variables of the template that serves {@code method path}, or empty
     * when the spec has no such operation. Lets a caller holding several specs pick the most
     * specific match ({@code /events/attendance-summary} over {@code /events/{eventCode}}).
     */
    OptionalInt operationVariables(String method, String path) {
        return route(path)
                .filter(r -> r.item.readOperationsMap()
                        .get(PathItem.HttpMethod.valueOf(method.toUpperCase(Locale.ROOT))) != null)
                .map(r -> OptionalInt.of(r.variables))
                .orElse(OptionalInt.empty());
    }

    /** Every operation of the spec: HTTP method to full path templates (base path included). */
    public Map<String, List<String>> operations() {
        Map<String, List<String>> out = new TreeMap<>();
        openApi.getPaths().forEach((template, item) -> item.readOperationsMap().keySet().forEach(m ->
                out.computeIfAbsent(m.name(), k -> new ArrayList<>()).add(basePath + template)));
        return out;
    }

    /** True when the operation's response {@code status} has an {@code application/json} body. */
    boolean hasJsonBody(String method, String path, int status) {
        ApiResponse response = requireOperation(method, path).getResponses().get(String.valueOf(status));
        return response != null && response.getContent() != null
                && response.getContent().get("application/json") != null;
    }

    /** True when {@code path} (concrete or template, base path included) is a path of the spec. */
    public boolean hasPath(String path) {
        return route(path).isPresent();
    }

    private Optional<Route> route(String path) {
        if (!path.startsWith(basePath + "/")) {
            return Optional.empty();
        }
        String relative = path.substring(basePath.length());
        return routes.stream().filter(r -> r.template.equals(relative) || r.pattern.matcher(relative).matches())
                .findFirst();
    }

    /** The operation, or an {@link AssertionError} naming method, path and spec. */
    Operation requireOperation(String method, String path) {
        return operation(method, path).orElseThrow(() -> new AssertionError(
                method + " " + path + " is not an operation of " + specPath
                        + (hasPath(path) ? " (the path exists, the method does not)" : " (no such path)")));
    }

    /** The lowest 2xx status the operation declares. */
    int successStatus(String method, String path) {
        return requireOperation(method, path).getResponses().keySet().stream()
                .filter(k -> k.matches("2\\d\\d"))
                .mapToInt(Integer::parseInt)
                .min()
                .orElseThrow(() -> new AssertionError(method + " " + path + " declares no 2xx response in "
                        + specPath));
    }

    /** True when the operation declares {@code status} (or a {@code default} response). */
    boolean declaresStatus(String method, String path, int status) {
        var responses = requireOperation(method, path).getResponses();
        return responses.containsKey(String.valueOf(status)) || responses.containsKey("default");
    }

    // ------------------------------------------------------------------------------------------
    // Examples
    // ------------------------------------------------------------------------------------------

    /**
     * The JSON example of the response {@code status}: the named example when {@code name} is
     * given, else the media type's {@code example}, else its first {@code examples} entry.
     */
    public String example(String method, String path, int status, String name) {
        MediaType media = jsonMedia(method, path, status);
        Object value = null;
        if (name != null) {
            Example example = media.getExamples() == null ? null : media.getExamples().get(name);
            if (example == null) {
                throw new AssertionError("No example '" + name + "' for " + method + " " + path + " " + status
                        + " in " + specPath + "; have " + exampleNames(media));
            }
            value = example.getValue();
        } else if (media.getExample() != null) {
            value = media.getExample();
        } else if (media.getExamples() != null && !media.getExamples().isEmpty()) {
            value = media.getExamples().values().iterator().next().getValue();
        }
        if (value == null) {
            throw new AssertionError("No example for " + method + " " + path + " " + status + " in " + specPath
                    + ". Add one to the spec (response content example or examples).");
        }
        return toJson(value);
    }

    /** Every JSON response example of the operation, keyed "status name". */
    public Map<String, String> responseExamples(String method, String path) {
        Map<String, String> out = new LinkedHashMap<>();
        requireOperation(method, path).getResponses().forEach((status, response) -> {
            MediaType media = response.getContent() == null ? null : response.getContent().get("application/json");
            if (media == null) {
                return;
            }
            if (media.getExample() != null) {
                out.put(status + " example", toJson(media.getExample()));
            }
            if (media.getExamples() != null) {
                media.getExamples().forEach((name, ex) -> {
                    if (ex.getValue() != null) {
                        out.put(status + " " + name, toJson(ex.getValue()));
                    }
                });
            }
        });
        return out;
    }

    private MediaType jsonMedia(String method, String path, int status) {
        ApiResponse response = requireOperation(method, path).getResponses().get(String.valueOf(status));
        if (response == null || response.getContent() == null
                || response.getContent().get("application/json") == null) {
            throw new AssertionError(method + " " + path + " declares no application/json response " + status
                    + " in " + specPath);
        }
        return response.getContent().get("application/json");
    }

    private static String exampleNames(MediaType media) {
        return media.getExamples() == null ? "none" : String.join(", ", media.getExamples().keySet());
    }

    private static String toJson(Object value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot serialise example " + value, e);
        }
    }

    // ------------------------------------------------------------------------------------------
    // Validation
    // ------------------------------------------------------------------------------------------

    /** Validation errors of a request; empty when the request is valid. */
    public List<String> validateRequest(String method, String path, Map<String, List<String>> query,
                                        Map<String, List<String>> headers, String body) {
        SimpleRequest.Builder builder = new SimpleRequest.Builder(method.toUpperCase(Locale.ROOT), path);
        query.forEach(builder::withQueryParam);
        headers.forEach(builder::withHeader);
        if (body != null && !body.isEmpty()) {
            builder.withBody(body);
        }
        List<String> errors = messages(validator.validateRequest(builder.build()));
        errors.addAll(globalSecurity(method, path, headers));
        return errors;
    }

    /**
     * The library checks only operation-level {@code security}; our specs mostly declare it once
     * at the root. An operation without its own block inherits the root one, and an empty list
     * ({@code security: []}) or an empty requirement ({@code - {}}) makes it public.
     */
    private List<String> globalSecurity(String method, String path, Map<String, List<String>> headers) {
        Optional<Operation> op = operation(method, path);
        if (op.isEmpty() || op.get().getSecurity() != null || openApi.getSecurity() == null
                || openApi.getSecurity().isEmpty() || openApi.getSecurity().stream().anyMatch(Map::isEmpty)) {
            return new ArrayList<>();
        }
        boolean hasAuthorization = headers.keySet().stream().anyMatch("Authorization"::equalsIgnoreCase);
        return hasAuthorization ? new ArrayList<>() : new ArrayList<>(List.of(
                "[validation.request.security.missing] " + method.toUpperCase(Locale.ROOT) + " on path '" + path
                        + "' requires security parameters (root security " + openApi.getSecurity()
                        + "). None found."));
    }

    /** Validation errors of a JSON response body; empty when the response is valid. */
    public List<String> validateResponse(String method, String path, int status, String body) {
        return messages(responseReport(method, path, status, body));
    }

    /**
     * The same validation as {@link #validateResponse}, one {@link Violation} per error: its
     * message key, the JSON pointer into the body (empty for the whole body) and the message.
     * Used by {@link ResponseConformance}.
     */
    List<Violation> responseViolations(String method, String path, int status, String body) {
        List<Violation> out = new ArrayList<>();
        for (ValidationReport.Message m : responseReport(method, path, status, body).getMessages()) {
            if (m.getLevel() == ValidationReport.Level.IGNORE || m.getLevel() == ValidationReport.Level.INFO) {
                continue;
            }
            String pointer = m.getContext().flatMap(ValidationReport.MessageContext::getPointers)
                    .map(ValidationReport.MessageContext.Pointers::getInstance)
                    .orElse("");
            out.add(new Violation(m.getKey(), pointer, m.getMessage()));
        }
        return out;
    }

    /** One response validation error: message key, JSON pointer, message. */
    record Violation(String key, String pointer, String message) {
    }

    /** The operationId of the operation, or {@code METHOD template} when it declares none. */
    String operationId(String method, String path) {
        Operation op = requireOperation(method, path);
        if (op.getOperationId() != null && !op.getOperationId().isBlank()) {
            return op.getOperationId();
        }
        return method.toUpperCase(Locale.ROOT) + "_" + route(path).map(Route::template).orElse(path);
    }

    private ValidationReport responseReport(String method, String path, int status, String body) {
        SimpleResponse.Builder builder = SimpleResponse.Builder.status(status);
        if (body != null && !body.isEmpty()) {
            builder.withContentType("application/json").withBody(body);
        }
        return validator.validateResponse(path, Request.Method.valueOf(method.toUpperCase(Locale.ROOT)),
                builder.build());
    }

    private static List<String> messages(ValidationReport report) {
        List<String> out = new ArrayList<>();
        for (ValidationReport.Message m : report.getMessages()) {
            if (m.getLevel() == ValidationReport.Level.IGNORE || m.getLevel() == ValidationReport.Level.INFO) {
                continue;
            }
            String nested = m.getNestedMessages().stream().map(n -> "\n      " + n.getMessage())
                    .collect(Collectors.joining());
            String pointer = m.getContext().flatMap(ValidationReport.MessageContext::getPointers)
                    .map(ValidationReport.MessageContext.Pointers::getInstance)
                    .filter(p -> !p.isEmpty())
                    .map(p -> " at " + p)
                    .orElse("");
            String parameter = m.getContext().flatMap(ValidationReport.MessageContext::getParameter)
                    .map(p -> " (parameter '" + p.getName() + "')")
                    .orElse("");
            out.add("[" + m.getKey() + "] " + m.getMessage() + parameter + pointer + nested);
        }
        return out;
    }

    private record Route(String template, PathItem item, Pattern pattern, int variables) {
        Route(String template, PathItem item) {
            this(template, item, compile(template), (int) template.chars().filter(c -> c == '{').count());
        }

        private static Pattern compile(String template) {
            StringBuilder regex = new StringBuilder();
            int i = 0;
            while (i < template.length()) {
                int open = template.indexOf('{', i);
                if (open < 0) {
                    regex.append(Pattern.quote(template.substring(i)));
                    break;
                }
                regex.append(Pattern.quote(template.substring(i, open))).append("[^/]+");
                i = template.indexOf('}', open) + 1;
            }
            return Pattern.compile(regex.toString());
        }
    }
}
