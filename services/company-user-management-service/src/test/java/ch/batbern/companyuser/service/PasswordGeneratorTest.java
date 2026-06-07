package ch.batbern.companyuser.service;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link PasswordGenerator}.
 *
 * Story 11.E.2 (AC4): generated passwords must satisfy the Cognito pool policy
 * from Story 11.E.1.
 */
class PasswordGeneratorTest {

    private final PasswordGenerator generator = new PasswordGenerator();

    @Test
    void should_returnSixteenCharPassword_when_noLengthSpecified() {
        String password = generator.generate();
        assertThat(password).hasSize(16);
    }

    @Test
    void should_includeAllFourCharacterClasses_when_generatedRepeatedly() {
        for (int i = 0; i < 100; i++) {
            String password = generator.generate();
            assertThat(password).matches(".*[a-z].*");
            assertThat(password).matches(".*[A-Z].*");
            assertThat(password).matches(".*\\d.*");
            assertThat(password).matches(".*[!@#$%&?+\\-=_*.].*");
        }
    }

    @Test
    void should_generateUniquePasswords_when_called1000Times() {
        Set<String> passwords = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            passwords.add(generator.generate());
        }
        // 1000 16-char passwords from a 76-char alphabet → collision probability is astronomical.
        assertThat(passwords).hasSize(1000);
    }

    @Test
    void should_returnRequestedLength_when_customLengthProvided() {
        assertThat(generator.generate(8)).hasSize(8);
        assertThat(generator.generate(24)).hasSize(24);
        assertThat(generator.generate(64)).hasSize(64);
    }

    @Test
    void should_throwIllegalArgument_when_lengthLessThan8() {
        assertThatThrownBy(() -> generator.generate(7))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Length must be >= 8");
        assertThatThrownBy(() -> generator.generate(0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> generator.generate(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_excludeAmbiguousSymbols_when_generated() {
        for (int i = 0; i < 100; i++) {
            String password = generator.generate();
            // None of these characters should appear (no quote/slash/backtick/space).
            assertThat(password).doesNotContain("\"", "'", "/", "\\", "`", " ", "<", ">");
        }
    }
}
