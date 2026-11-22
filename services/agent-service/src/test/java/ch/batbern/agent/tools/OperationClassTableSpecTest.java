package ch.batbern.agent.tools;

import ch.batbern.agent.gateway.Operation;
import ch.batbern.shared.test.contract.OpenApiContract;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every operation the class table names is an operation of {@code docs/api/*.openapi.yml}, by
 * method plus path (story 5): the table cannot class an operation that does not exist, nor keep one
 * after the spec drops it. The test tables (identity tests, check fixtures) are held to the same.
 */
@DisplayName("The operation class table names only operations of docs/api")
class OperationClassTableSpecTest {

    @Test
    @DisplayName("every table operation exists in a public spec")
    void should_findEveryOperation_when_specsAreRead() throws IOException {
        List<OpenApiContract> specs = publicSpecs();
        assertThat(specs).as("the public specs were found").hasSizeGreaterThan(10);

        for (OperationClassTable table : List.of(OperationClassTable.standard(), TestToolRegistry.TABLE,
                ToolClassCheckTest.WRITES)) {
            assertThat(table.entries()).isNotEmpty();
            for (Operation operation : table.entries().keySet()) {
                assertThat(specs.stream().anyMatch(spec -> exists(spec, operation)))
                        .as("%s is in docs/api", operation).isTrue();
            }
        }
    }

    @Test
    @DisplayName("positive control: an operation no spec has is not found")
    void should_notFindOperation_when_noSpecHasIt() throws IOException {
        List<OpenApiContract> specs = publicSpecs();

        assertThat(specs.stream().anyMatch(spec -> exists(spec, Operation.of("DELETE /events")))).isFalse();
        assertThat(specs.stream().anyMatch(spec -> exists(spec, Operation.of("GET /events/{eventCode}/nonsense"))))
                .isFalse();
        assertThat(specs.stream().anyMatch(spec -> exists(spec, Operation.of("GET /events")))).isTrue();
    }

    private static boolean exists(OpenApiContract spec, Operation operation) {
        return spec.operation(operation.method().name(), spec.basePath() + operation.path()).isPresent();
    }

    /** {@code docs/api/*.openapi.yml}, found from the working directory upwards. */
    private static List<OpenApiContract> publicSpecs() throws IOException {
        Path root = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (root != null && !Files.isDirectory(root.resolve("docs/api"))) {
            root = root.getParent();
        }
        assertThat(root).as("docs/api above the working directory").isNotNull();
        try (Stream<Path> files = Files.list(root.resolve("docs/api"))) {
            return files.map(Path::getFileName).map(Path::toString)
                    .filter(name -> name.endsWith(".openapi.yml") && !name.startsWith("_"))
                    .sorted()
                    .map(name -> OpenApiContract.load("docs/api/" + name))
                    .toList();
        }
    }
}
