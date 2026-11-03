package ch.batbern.events.security;

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

    @Test
    @DisplayName("should_runNoFrameworkLoggerAtDebug_inProduction")
    void should_runNoFrameworkLoggerAtDebug_inProduction() {
        // production runs the staging profile; DEBUG framework logs wrote request URLs with addresses
        assertThat(LogAddressGuard.verboseProductionLevels(Path.of("src/main/resources")))
                .as("DEBUG/TRACE loggers active in production (application.yml base/staging, logback-spring.xml)")
                .isEmpty();
    }
}
