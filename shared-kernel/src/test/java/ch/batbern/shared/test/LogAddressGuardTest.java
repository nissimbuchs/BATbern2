package ch.batbern.shared.test;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("LogAddressGuard (#1184)")
class LogAddressGuardTest {

    private static List<String> scan(String source) {
        List<String> found = new ArrayList<>();
        LogAddressGuard.scan("X.java", source, found);
        return found;
    }

    @Test
    @DisplayName("flags an unmasked address argument, with file, line and expression")
    void flagsUnmasked() {
        assertThat(scan("class X {\n void a() {\n  log.info(\"Subscribed {}\", email);\n }\n}"))
                .containsExactly("X.java:3: email");
        assertThat(scan("log.warn(\"Bounce for {} ({})\", request.getEmail(), reason);"))
                .containsExactly("X.java:1: request.getEmail()");
        assertThat(scan("log.error(\"Failed to send to {}: {}\",\n    to, e.getMessage(), e);"))
                .containsExactly("X.java:1: to");
    }

    @Test
    @DisplayName("accepts masked addresses, non-address arguments, the message itself and opt-outs")
    void acceptsTheRest() {
        assertThat(scan("log.info(\"Subscribed {}\", LoggingUtils.maskEmail(email));")).isEmpty();
        assertThat(scan("log.info(\"Sent {} mails to {} recipients\", count, recipients.size());")).isEmpty();
        assertThat(scan("log.info(\"email verified: {}\", emailVerified);")).isEmpty();
        assertThat(scan("log.info(\"Mail {} stored\", mailKey);")).isEmpty();
        assertThat(scan("log.info(\"to {}\", to); // log-address-ok: local dev capture only")).isEmpty();
        assertThat(scan("log.error(\"Failed\", e);")).isEmpty();
        // a state transition, not an address
        assertThat(scan("log.info(\"Speaker moved from {} to {}\", from, to);")).isEmpty();
    }

    @Test
    @DisplayName("also scans LOG / logger / LOGGER fields and camel-case address names (seen in prod 2026-11-03)")
    void scansOtherLoggerNamesAndCamelCase() {
        assertThat(scan("LOG.info(\"Outreach by {}\", email);")).containsExactly("X.java:1: email");
        assertThat(scan("logger.warn(\"x {}\", email);")).containsExactly("X.java:1: email");
        assertThat(scan("LOGGER.debug(\"x {}\", email);")).containsExactly("X.java:1: email");
        assertThat(scan("log.info(\"Provisioning: email={}, role={}\", normalizedEmail, role);"))
                .containsExactly("X.java:1: normalizedEmail");
        assertThat(scan("log.info(\"x {}\", primaryEmail);")).containsExactly("X.java:1: primaryEmail");
        assertThat(scan("log.info(\"x {}\", isEmailVerified);")).isEmpty();
        assertThat(scan("dialog.info(\"x {}\", email);")).isEmpty();
    }

    @Test
    @DisplayName("production log levels: no DEBUG/TRACE framework logger in the base or staging profile")
    void verboseLevelsInProduction(@org.junit.jupiter.api.io.TempDir Path dir) throws Exception {
        java.nio.file.Files.writeString(dir.resolve("application.yml"), """
                logging:
                  level:
                    root: INFO
                    ch.batbern: ${LOG_LEVEL:INFO}
                    org.springframework.security: DEBUG
                ---
                spring:
                  config:
                    activate:
                      on-profile: local
                logging:
                  level:
                    org.hibernate.SQL: DEBUG
                ---
                spring:
                  config:
                    activate:
                      on-profile: staging
                logging:
                  level:
                    org.hibernate.SQL: TRACE
                """);
        java.nio.file.Files.writeString(dir.resolve("logback-spring.xml"), """
                <configuration>
                    <logger name="ch.batbern.companyuser" level="DEBUG" additivity="false"/>
                    <logger name="org.springframework" level="INFO"/>
                </configuration>
                """);

        assertThat(LogAddressGuard.verboseProductionLevels(dir)).containsExactlyInAnyOrder(
                "application.yml [base]: org.springframework.security=DEBUG",
                "application.yml [staging]: org.hibernate.SQL=TRACE",
                "logback-spring.xml: ch.batbern.companyuser=DEBUG");
    }

    @Test
    @DisplayName("shared-kernel itself logs no unmasked address")
    void sharedKernelIsClean() {
        assertThat(LogAddressGuard.unmaskedAddressLogs(Path.of("src/main/java"))).isEmpty();
    }
}
