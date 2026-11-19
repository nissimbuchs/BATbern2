package ch.batbern.companyuser.security;

import ch.batbern.shared.test.LogbackConfigUnderTest;
import ch.qos.logback.classic.Logger;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * #1202: no e-mail address reaches CloudWatch from CUMS. Loads the production
 * {@code logback-spring.xml} into a context of its own and renders through its real
 * LogstashEncoder: message text, argument, MDC, exception message and stack trace come out masked,
 * field names stay.
 */
class LogEncoderMaskingTest {

    private final ObjectMapper json = new ObjectMapper();
    private LogbackConfigUnderTest config;

    @BeforeEach
    void load() {
        // The <springProperty> values, plus the logging.pattern.* Spring Boot would pass.
        Map<String, String> properties = new HashMap<>(
                LogbackConfigUnderTest.springLoggingProperties(Path.of("src/main/resources")));
        properties.putAll(Map.of("springAppName", "company-user-management-service", "springProfile", "staging"));
        config = LogbackConfigUnderTest.load("logback-spring.xml", properties);
    }

    @AfterEach
    void close() {
        config.close();
    }

    @Test
    void should_loadTheProductionConfig_when_rendering() throws Exception {
        assertThat(config.source().toString()).endsWith("/main/logback-spring.xml");
        config.logger("ch.batbern.companyuser.Probe").info("probe");
        JsonNode line = json.readTree(config.output());
        assertThat(line.get("application").asText()).isEqualTo("company-user-management-service");
    }

    @Test
    void should_maskAddress_when_inMessageArgumentMdcAndException() throws Exception {
        Logger log = config.logger("ch.batbern.companyuser.service.UserService");
        config.mdc().put("correlationId", "corr-1 for frida.graf@example.ch");
        try {
            log.error("could not add anna.meier@example.ch for {}", "bert.keller@example.org",
                    new IllegalStateException("Email 'carla.rossi@example.net' is already registered",
                            new RuntimeException("Key (email)=(jürg.müller@bücher.example.ch) already exists")));
        } finally {
            config.mdc().remove("correlationId");
        }

        String out = config.output();
        assertThat(out).doesNotContain("anna.meier@").doesNotContain("bert.keller@")
                .doesNotContain("carla.rossi@").doesNotContain("jürg.müller@").doesNotContain("frida.graf@");
        JsonNode line = json.readTree(out);
        assertThat(line.get("message").asText()).isEqualTo("could not add a***@example.ch for b***@example.org");
        assertThat(line.get("correlationId").asText()).isEqualTo("corr-1 for f***@example.ch");
        assertThat(line.get("stack_trace").asText())
                .contains("java.lang.IllegalStateException: Email 'c***@example.net' is already registered")
                .contains("Caused by: java.lang.RuntimeException: "
                        + "Key (email)=(j***@bücher.example.ch) already exists");
    }
}
