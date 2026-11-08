package ch.batbern.events.architecture;

import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ArchUnit refuses only a missing store folder: a new or renamed rule description is stored on its
 * first run with all its current violations accepted. This test pins the keys of the committed
 * {@code archunit_store/stored.rules} to exactly the frozen rules {@link EmsModuleWallsTest} declares, so a
 * renamed, added or removed rule fails here.
 */
@DisplayName("Frozen ArchUnit rules match the committed store")
class FrozenRuleKeysTest {

    private static final Path STORED_RULES = Path.of("src/test/resources/archunit_store/stored.rules");

    @Test
    @DisplayName("should_matchStoreKeys_when_rulesAreDeclared")
    void should_matchStoreKeys_when_rulesAreDeclared() throws IOException {
        Properties stored = new Properties();
        try (Reader reader = Files.newBufferedReader(STORED_RULES)) {
            stored.load(reader);
        }

        assertThat(declaredRuleDescriptions())
                .as("frozen rules vs keys in %s (a new or renamed rule would accept all its current violations)",
                        STORED_RULES)
                .containsExactlyInAnyOrderElementsOf(stored.stringPropertyNames());
    }

    private static Set<String> declaredRuleDescriptions() {
        return Arrays.stream(EmsModuleWallsTest.class.getDeclaredFields())
                .filter(f -> f.isAnnotationPresent(ArchTest.class) && Modifier.isStatic(f.getModifiers()))
                .map(FrozenRuleKeysTest::rule)
                .map(ArchRule::getDescription)
                .collect(Collectors.toSet());
    }

    private static ArchRule rule(Field field) {
        try {
            field.setAccessible(true);
            return (ArchRule) field.get(null);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }
}
