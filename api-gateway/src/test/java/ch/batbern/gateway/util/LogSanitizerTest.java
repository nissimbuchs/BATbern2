package ch.batbern.gateway.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("LogSanitizer")
class LogSanitizerTest {

    @Test
    @DisplayName("should_removeLineBreaks_when_logged (log injection)")
    void should_removeLineBreaks_when_logged() {
        assertThat(LogSanitizer.sanitize("a\nb\rc\td")).isEqualTo("a b c d");
        assertThat(LogSanitizer.sanitize((String) null)).isEqualTo("null");
    }

    // #1184 second pass: seen in production 2026-11-03, the gateway logged request paths such as
    // /api/v1/users/me/additional-emails/{address} and downstream error bodies with the address.
    @Test
    @DisplayName("should_maskAddresses_in_pathsAndErrorBodies")
    void should_maskAddresses_in_pathsAndErrorBodies() {
        assertThat(LogSanitizer.sanitize("/api/v1/users/me/additional-emails/anna.muster@example.ch"))
                .isEqualTo("/api/v1/users/me/additional-emails/a***@example.ch");
        assertThat(LogSanitizer.sanitize("/api/v1/users/search?query=anna%40example.ch"))
                .isEqualTo("/api/v1/users/search?query=a***%40example.ch");
        assertThat(LogSanitizer.sanitize("{\n \"message\": \"Email 'bert@example.org' is already registered\"\n}"))
                .isEqualTo("{  \"message\": \"Email 'b***@example.org' is already registered\" }");
        assertThat(LogSanitizer.sanitize("/api/v1/events/BATbern60")).isEqualTo("/api/v1/events/BATbern60");
    }
}
