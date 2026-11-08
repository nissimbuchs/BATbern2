package ch.batbern.shared.test;

import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("FrozenArchRuleKeys")
class FrozenArchRuleKeysTest {

    static class Rules {
        @ArchTest
        static final ArchRule FIRST = noClasses().should().accessClassesThat().haveSimpleName("A")
                .as("first rule");
        @ArchTest
        static final ArchRule SECOND = noClasses().should().accessClassesThat().haveSimpleName("B")
                .as("second rule");
        static final ArchRule NOT_A_TEST = noClasses().should().accessClassesThat().haveSimpleName("C")
                .as("not declared as test");
    }

    @TempDir
    Path dir;

    private Path store(String... keys) throws IOException {
        StringBuilder text = new StringBuilder();
        for (String key : keys) {
            text.append(key.replace(" ", "\\ ")).append("=").append("some-uuid\n");
        }
        Path file = dir.resolve("stored.rules");
        Files.writeString(file, text.toString());
        return file;
    }

    @Test
    void should_pass_when_storeKeysEqualDeclaredRules() throws IOException {
        Path file = store("first rule", "second rule");

        assertThatCode(() -> FrozenArchRuleKeys.assertStoreMatches(Rules.class, file)).doesNotThrowAnyException();
    }

    @Test
    void should_fail_when_aDeclaredRuleIsMissingFromTheStore() throws IOException {
        Path file = store("first rule");

        assertThatThrownBy(() -> FrozenArchRuleKeys.assertStoreMatches(Rules.class, file))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("second rule");
    }

    @Test
    void should_fail_when_theStoreHoldsARenamedOrRemovedRule() throws IOException {
        Path file = store("first rule", "second rule", "old name of a rule");

        assertThatThrownBy(() -> FrozenArchRuleKeys.assertStoreMatches(Rules.class, file))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("old name of a rule");
    }
}
