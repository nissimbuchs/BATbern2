package ch.batbern.shared.test;

import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
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
 * Pins the keys of a committed ArchUnit store to the frozen rules a test class declares.
 *
 * <p>ArchUnit refuses only a missing store folder: a new or renamed rule description is stored on
 * its first run with all its current violations accepted. Calling {@link #assertStoreMatches} from
 * one test per service makes a renamed, added or removed rule fail instead (Story 17.5). Shared
 * here so the services do not copy it, like {@link EndpointAuthorizationGuard}.
 */
public final class FrozenArchRuleKeys {

    /** The store every service commits, relative to the service's project directory. */
    public static final Path DEFAULT_STORE = Path.of("src/test/resources/archunit_store/stored.rules");

    private FrozenArchRuleKeys() {
    }

    /**
     * Asserts that the static {@code @ArchTest ArchRule} fields of {@code rulesHolder} describe
     * exactly the keys of {@code storedRules}.
     */
    public static void assertStoreMatches(Class<?> rulesHolder, Path storedRules) {
        assertThat(declaredRuleDescriptions(rulesHolder))
                .as("frozen rules of %s vs keys in %s (a new or renamed rule would accept all its "
                        + "current violations)", rulesHolder.getSimpleName(), storedRules)
                .containsExactlyInAnyOrderElementsOf(storedKeys(storedRules));
    }

    static Set<String> storedKeys(Path storedRules) {
        Properties stored = new Properties();
        try (Reader reader = Files.newBufferedReader(storedRules)) {
            stored.load(reader);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return stored.stringPropertyNames();
    }

    static Set<String> declaredRuleDescriptions(Class<?> rulesHolder) {
        return Arrays.stream(rulesHolder.getDeclaredFields())
                .filter(f -> f.isAnnotationPresent(ArchTest.class) && Modifier.isStatic(f.getModifiers()))
                .map(FrozenArchRuleKeys::rule)
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
