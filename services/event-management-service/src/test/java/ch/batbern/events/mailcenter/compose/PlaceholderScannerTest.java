package ch.batbern.events.mailcenter.compose;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Story 16.8a Task 4: placeholders left in a rendered mail are reported, once each, in order. */
@DisplayName("Placeholder scanner (Story 16.8a)")
class PlaceholderScannerTest {

    @Test
    @DisplayName("finds simple, section-open and section-close placeholders in subject and body")
    void findsLeftovers() {
        assertThat(PlaceholderScanner.scan("Hi {{speakerName}}", "<p>{{deadlin}}</p>{{#notes}}x{{/notes}}{{deadlin}}"))
                .containsExactly("{{speakerName}}", "{{deadlin}}", "{{#notes}}", "{{/notes}}");
    }

    @Test
    @DisplayName("a fully rendered mail has none; null parts are ignored")
    void cleanMail() {
        assertThat(PlaceholderScanner.scan("Reminder", null, "<p>Hello { name } and {{ }}</p>")).isEmpty();
    }
}
