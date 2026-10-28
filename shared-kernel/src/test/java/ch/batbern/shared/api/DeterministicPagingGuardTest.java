package ch.batbern.shared.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Repo-wide guard for #1131: production code builds every {@code Pageable} through
 * {@link DeterministicPaging}, so no list endpoint can page over a non-total order again.
 *
 * <p>Scans every service's {@code src/main/java} and {@code api-gateway/src/main/java} of the monorepo
 * (shared-kernel's tests run from the repo checkout in CI and locally). A raw
 * {@code PageRequest.of(...)} / {@code PageRequest.ofSize(...)} fails the build with the offending
 * file and line.
 */
@DisplayName("DeterministicPaging guard: no raw PageRequest in production code")
class DeterministicPagingGuardTest {

    private static final Pattern RAW_PAGE_REQUEST = Pattern.compile("\\bPageRequest\\s*\\.\\s*(of|ofSize)\\s*\\(");

    @Test
    @DisplayName("should_buildEveryPageableThroughDeterministicPaging")
    void should_buildEveryPageableThroughDeterministicPaging() throws IOException {
        Path repoRoot = findRepoRoot();
        List<Path> sourceRoots = new ArrayList<>();
        try (Stream<Path> services = Files.list(repoRoot.resolve("services"))) {
            services.map(s -> s.resolve("src/main/java")).filter(Files::isDirectory).forEach(sourceRoots::add);
        }
        sourceRoots.add(repoRoot.resolve("api-gateway/src/main/java"));

        assertThat(sourceRoots).as("service source roots found under %s", repoRoot).hasSizeGreaterThan(2);

        List<String> violations = new ArrayList<>();
        for (Path root : sourceRoots) {
            try (Stream<Path> files = Files.walk(root)) {
                files.filter(p -> p.toString().endsWith(".java")).forEach(file -> scan(repoRoot, file, violations));
            }
        }

        assertThat(violations)
                .as("Build Pageables with ch.batbern.shared.api.DeterministicPaging (unique tie-breaker, #1131)")
                .isEmpty();
    }

    private static void scan(Path repoRoot, Path file, List<String> violations) {
        try {
            List<String> lines = Files.readAllLines(file);
            for (int i = 0; i < lines.size(); i++) {
                if (RAW_PAGE_REQUEST.matcher(lines.get(i)).find()) {
                    violations.add(repoRoot.relativize(file) + ":" + (i + 1) + ": " + lines.get(i).trim());
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Path findRepoRoot() {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null) {
            if (Files.isDirectory(dir.resolve("services")) && Files.isDirectory(dir.resolve("shared-kernel"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("Monorepo root (services/ + shared-kernel/) not found above "
                + Path.of("").toAbsolutePath());
    }
}
