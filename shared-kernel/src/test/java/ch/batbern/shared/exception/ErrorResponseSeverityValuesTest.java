package ch.batbern.shared.exception;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every {@code ErrorResponse.severity("...")} literal in the backend sources is a value of the
 * shared {@code ErrorResponse.severity} enum ({@code docs/api/_shared.openapi.yml}, the same values
 * as {@link BATbernException.Severity}).
 *
 * <p>Found 2026-11-10 (Epic 17 entry 6): CUMS answered a 404 with {@code "severity": "WARNING"},
 * which the spec rejects; the response conformance gate checks only 2xx answers, so 15 handlers
 * sent {@code WARNING} or {@code ERROR} unnoticed. A source scan, because an error handler must not
 * throw on a bad value at runtime.
 */
class ErrorResponseSeverityValuesTest {

    private static final Pattern SEVERITY_LITERAL = Pattern.compile("\\.severity\\(\"([^\"]*)\"\\)");

    @Test
    void should_useOnlySpecSeverities_inEveryErrorResponse() throws IOException {
        Set<String> allowed = Arrays.stream(BATbernException.Severity.values())
                .map(Enum::name).collect(Collectors.toSet());
        Path repo = Path.of("").toAbsolutePath().getParent();
        List<Path> roots = new ArrayList<>(List.of(repo.resolve("shared-kernel/src/main/java"),
                repo.resolve("api-gateway/src/main/java")));
        try (Stream<Path> services = Files.list(repo.resolve("services"))) {
            services.map(s -> s.resolve("src/main/java")).filter(Files::isDirectory).forEach(roots::add);
        }

        List<String> violations = new ArrayList<>();
        int literals = 0;
        for (Path root : roots) {
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(root)) {
                for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                    List<String> lines = Files.readAllLines(file);
                    for (int i = 0; i < lines.size(); i++) {
                        Matcher m = SEVERITY_LITERAL.matcher(lines.get(i));
                        while (m.find()) {
                            literals++;
                            if (!allowed.contains(m.group(1))) {
                                violations.add(repo.relativize(file) + ":" + (i + 1) + " severity \"" + m.group(1) + "\"");
                            }
                        }
                    }
                }
            }
        }

        assertThat(literals).as("severity literals found (scan reached the services)").isGreaterThan(50);
        assertThat(violations).as("severity values outside " + allowed).isEmpty();
    }
}
