package ch.batbern.shared.mail;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("MailRedaction: no credential reaches the mail record (Story 16.1a)")
class MailRedactionTest {

    @Test
    @DisplayName("token query parameters are masked in every link")
    void tokenParameters_masked() {
        String html = "<a href=\"https://www.batbern.ch/deregister?token=0b6e3f8a-1d2c\">x</a>"
                + " https://www.batbern.ch/events/BATbern60/confirm-registration?token=eyJhbGciOi.abc.def&lang=de"
                + " https://www.batbern.ch/unsubscribe?lang=de&amp;token=abc123";

        String redacted = MailRedaction.redact(html, List.of());

        assertThat(redacted)
                .doesNotContain("0b6e3f8a-1d2c", "eyJhbGciOi.abc.def", "abc123")
                .contains("deregister?token=[redacted]\"", "confirm-registration?token=[redacted]&lang=de",
                        "&amp;token=[redacted]");
    }

    @Test
    @DisplayName("named secrets are masked raw and in their HTML-escaped form")
    void namedSecrets_masked() {
        String html = "<p>Passwort: a&lt;b&amp;C9x</p><p>Code BATbern60-reg-7KQ2</p> plain a<b&C9x";

        String redacted = MailRedaction.redact(html, List.of("a<b&C9x", "BATbern60-reg-7KQ2"));

        assertThat(redacted).doesNotContain("a&lt;b&amp;C9x", "a<b&C9x", "BATbern60-reg-7KQ2")
                .contains("Passwort: [redacted]", "Code [redacted]");
    }

    @Test
    @DisplayName("null text stays null; very short secrets are ignored so ordinary words survive")
    void edgeCases() {
        assertThat(MailRedaction.redact(null, List.of("secret"))).isNull();
        assertThat(MailRedaction.redact("an apple", List.of("a"))).isEqualTo("an apple");
    }
}
