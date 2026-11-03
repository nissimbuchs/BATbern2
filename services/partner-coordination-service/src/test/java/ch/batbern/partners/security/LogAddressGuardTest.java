package ch.batbern.partners.security;

import ch.batbern.shared.test.LogAddressGuard;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * #1184: no log line passes an e-mail address unmasked; use {@code LoggingUtils.maskEmail}. See
 * {@link LogAddressGuard}.
 */
@DisplayName("Log lines mask e-mail addresses")
class LogAddressGuardTest {

    @Test
    @DisplayName("should_maskEveryAddress_when_logged")
    void should_maskEveryAddress_when_logged() {
        assertThat(LogAddressGuard.unmaskedAddressLogs(Path.of("src/main/java")))
                .as("log calls passing an address without LoggingUtils.maskEmail")
                .isEmpty();
    }
}
