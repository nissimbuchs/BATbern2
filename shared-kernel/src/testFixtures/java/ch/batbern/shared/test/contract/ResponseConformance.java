package ch.batbern.shared.test.contract;

import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MvcResult;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Response conformance gate (Epic 17.0 follow-up, owner decision 2026-11-09): every 2xx JSON
 * response an integration test receives through {@code MockMvc} from an operation of the
 * service's own specs must be valid against that operation's response schema.
 *
 * <p>Registered for every IT by {@link ch.batbern.shared.test.ResponseConformanceSetup} (imported
 * by {@link ch.batbern.shared.test.AbstractIntegrationTest}). Validation is the 17.0b contract
 * harness's: {@link OpenApiContract#responseViolations}, with {@link GeneratedCodeSemantics}
 * applied, so there is one validator.
 *
 * <p>A violation is reduced to a baseline key, {@code operationId pointer kind}, with array
 * indices in the pointer written as {@code *} (a pattern mismatch at {@code /data/3/createdBy}
 * of {@code listCompanies} is {@code listCompanies /data/x/createdBy pattern} with {@code *} for
 * {@code x}; see {@link #key}). A key in the service's committed
 * baseline ({@code src/test/resources/response-conformance-baseline.txt}) is accepted and
 * recorded as hit; any other fails the request that produced it, inside its test. The Gradle side
 * ({@code gradle/response-conformance.gradle}) fails a full run on a STALE baseline line (one no
 * response produced any more) and rewrites the baseline with
 * {@code -PupdateResponseConformanceBaseline}.
 *
 * <p>Configured by system properties the Gradle script sets; without
 * {@value #SPECS_PROPERTY} the gate is off (a run outside Gradle).
 */
public final class ResponseConformance {

    static final String SPECS_PROPERTY = "batbern.responseConformance.specs";
    static final String BASELINE_PROPERTY = "batbern.responseConformance.baseline";
    static final String RECORD_DIR_PROPERTY = "batbern.responseConformance.recordDir";
    static final String UPDATE_PROPERTY = "batbern.responseConformance.update";

    private static final Pattern ARRAY_INDEX = Pattern.compile("/\\d+(?=/|$)");
    private static final Pattern QUOTED_NAME = Pattern.compile("'([^']+)'");
    private static final Pattern STATUS = Pattern.compile("status (\\d{3})");

    private static volatile ResponseConformance instance;

    private final List<OpenApiContract> contracts;
    private final Set<String> baseline;
    private final Path recordDir;
    private final boolean update;
    private final AtomicInteger checked = new AtomicInteger();

    ResponseConformance(List<OpenApiContract> contracts, Set<String> baseline, Path recordDir, boolean update) {
        this.contracts = contracts;
        this.baseline = baseline;
        this.recordDir = recordDir;
        this.update = update;
    }

    /** Number of responses validated so far (reported by the Gradle script after the run). */
    int checked() {
        return checked.get();
    }

    /** The gate as the system properties configure it, or empty when they do not. */
    static Optional<ResponseConformance> configured() {
        ResponseConformance current = instance;
        if (current == null) {
            synchronized (ResponseConformance.class) {
                current = instance;
                if (current == null) {
                    current = fromSystemProperties();
                    instance = current;
                    if (current.recordDir != null) {
                        ResponseConformance gate = current;
                        Runtime.getRuntime().addShutdownHook(new Thread(gate::writeCheckedCount));
                    }
                }
            }
        }
        return current.contracts.isEmpty() ? Optional.empty() : Optional.of(current);
    }

    private static ResponseConformance fromSystemProperties() {
        String specs = System.getProperty(SPECS_PROPERTY, "").trim();
        if (specs.isEmpty()) {
            return new ResponseConformance(List.of(), Set.of(), null, false);
        }
        List<OpenApiContract> contracts = Arrays.stream(specs.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(OpenApiContract::load)
                .toList();
        String baselineFile = System.getProperty(BASELINE_PROPERTY);
        String recordDir = System.getProperty(RECORD_DIR_PROPERTY);
        return new ResponseConformance(contracts,
                baselineFile == null ? Set.of() : readBaseline(Path.of(baselineFile)),
                recordDir == null ? null : Path.of(recordDir),
                Boolean.getBoolean(UPDATE_PROPERTY));
    }

    /** The keys of a baseline file: one per line, blank lines and {@code #} comments ignored. */
    static Set<String> readBaseline(Path file) {
        if (!Files.isRegularFile(file)) {
            return Set.of();
        }
        try {
            return Files.readAllLines(file, StandardCharsets.UTF_8).stream()
                    .map(String::trim)
                    .filter(l -> !l.isEmpty() && !l.startsWith("#"))
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** {@code MockMvc} result handler: checks the response when the gate is configured. */
    public static void check(MvcResult result) {
        configured().ifPresent(gate -> gate.verify(result));
    }

    /**
     * Checks one response. Fails with an {@link AssertionError} naming each violation the
     * baseline does not list (unless updating); records the accepted ones.
     */
    void verify(MvcResult result) {
        MockHttpServletResponse response = result.getResponse();
        int status = response.getStatus();
        String type = response.getContentType();
        if (status < 200 || status > 299 || type == null || !isJson(type)) {
            return;
        }
        String body = new String(response.getContentAsByteArray(), StandardCharsets.UTF_8);
        if (body.isEmpty()) {
            return;
        }
        String method = result.getRequest().getMethod();
        String path = result.getRequest().getRequestURI();
        Optional<OpenApiContract> owner = owner(method, path);
        if (owner.isEmpty()) {
            return; // not an operation of this service's specs (actuator, dev-only endpoints)
        }
        OpenApiContract contract = owner.get();
        checked.incrementAndGet();
        String operationId = contract.operationId(method, path);
        List<String> accepted = new ArrayList<>();
        List<String> rejected = new ArrayList<>();
        for (OpenApiContract.Violation v : contract.responseViolations(method, path, status, body)) {
            String key = key(operationId, v);
            if (update || baseline.contains(key)) {
                accepted.add(key);
            } else {
                rejected.add(key + "\n      " + v.message());
            }
        }
        record(accepted);
        if (!rejected.isEmpty()) {
            throw new AssertionError("Response of " + method + " " + path + " (" + status + ") violates "
                    + contract.specPath() + " (response conformance gate, docs/architecture/coding-standards.md"
                    + " \"Test fidelity rules\"):\n  - " + String.join("\n  - ", rejected)
                    + "\nFix the response, the spec or the test data. Only a violation that needs an owner"
                    + " decision goes into src/test/resources/response-conformance-baseline.txt."
                    + "\nBody: " + abbreviate(body));
        }
    }

    /**
     * The spec serving the request: the one with the most specific template, so
     * {@code /events/attendance-summary} (analytics) wins over {@code /events/{eventCode}} (core).
     */
    Optional<OpenApiContract> owner(String method, String path) {
        return contracts.stream()
                .filter(c -> c.operationVariables(method, path).isPresent())
                .min(Comparator.comparingInt(c -> c.operationVariables(method, path).getAsInt()));
    }

    /** The baseline key of a violation: {@code operationId pointer kind}. */
    static String key(String operationId, OpenApiContract.Violation v) {
        String pointer = ARRAY_INDEX.matcher(v.pointer()).replaceAll("/*");
        if (pointer.isEmpty()) {
            pointer = "/";
        }
        String kind = v.key().replaceFirst("^validation\\.response\\.(body\\.schema\\.)?", "");
        if (kind.equals("required") || kind.equals("additionalProperties")) {
            Matcher m = QUOTED_NAME.matcher(v.message());
            if (m.find()) {
                kind = kind + ":" + m.group(1);
            }
        } else if (kind.equals("status.unknown")) {
            Matcher m = STATUS.matcher(v.message());
            if (m.find()) {
                kind = kind + ":" + m.group(1);
            }
        }
        return operationId + " " + pointer + " " + kind;
    }

    private void record(List<String> keys) {
        if (keys.isEmpty() || recordDir == null) {
            return;
        }
        try {
            Files.createDirectories(recordDir);
            Path file = recordDir.resolve("hits-" + ProcessHandle.current().pid() + ".txt");
            Files.writeString(file, String.join("\n", keys) + "\n", StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void writeCheckedCount() {
        try {
            Files.createDirectories(recordDir);
            Files.writeString(recordDir.resolve("checked-" + ProcessHandle.current().pid() + ".txt"),
                    checked.get() + "\n", StandardCharsets.UTF_8);
        } catch (IOException e) {
            // reporting only; the gate itself has already run
        }
    }

    private static boolean isJson(String contentType) {
        String base = contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        return base.equals("application/json") || (base.startsWith("application/") && base.endsWith("+json"));
    }

    private static String abbreviate(String body) {
        return body.length() <= 2000 ? body : body.substring(0, 2000) + "...";
    }
}
