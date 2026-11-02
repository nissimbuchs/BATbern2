package ch.batbern.events.mailcenter.compose;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Story 16.5 AC2: own text may only name variables the audience has. */
class PlaceholderValidatorTest {

    private static final Set<String> ALLOWED = Set.of("speakerName", "deadline", "eventTitle");

    @Test
    @DisplayName("known names pass, in subject and body")
    void known() {
        assertThat(PlaceholderValidator.unknown(ALLOWED, "Re {{eventTitle}}", "<p>Hi {{speakerName}}, {{deadline}}</p>"))
                .isEmpty();
    }

    @Test
    @DisplayName("a typo is reported by name, once, from subject or body")
    void typo() {
        assertThat(PlaceholderValidator.unknown(ALLOWED, "{{deadlin}}", "<p>{{deadlin}} {{speakerNme}}</p>"))
                .containsExactly("deadlin", "speakerNme");
    }

    @Test
    @DisplayName("block tags are checked like plain placeholders")
    void blocks() {
        assertThat(PlaceholderValidator.unknown(ALLOWED, null, "{{#deadline}}until {{deadline}}{{/deadline}}"))
                .isEmpty();
        assertThat(PlaceholderValidator.unknown(ALLOWED, null, "{{#agenda}}x{{/agenda}}")).containsExactly("agenda");
    }

    @Test
    @DisplayName("spaces inside the braces make it unknown: replaceVariables matches the exact {{name}}")
    void whitespace() {
        assertThat(PlaceholderValidator.unknown(ALLOWED, null, "{{ speakerName }}")).containsExactly(" speakerName ");
    }
}
