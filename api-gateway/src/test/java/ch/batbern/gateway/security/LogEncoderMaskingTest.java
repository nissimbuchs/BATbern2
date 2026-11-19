package ch.batbern.gateway.security;

import ch.batbern.gateway.observability.ClientErrorMetricsFilter;
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
 * #1202: no e-mail address reaches CloudWatch from the gateway. Loads the production
 * {@code logback-spring.xml} (not {@code logback-test.xml}) into a context of its own and renders
 * through its real LogstashEncoder: message text, argument, exception message and stack trace come
 * out masked, and the {@code GATEWAY_API_REQUEST} line still carries every term the CDK metric
 * filters match ({@code infrastructure/lib/constructs/alb-alarms.ts}).
 */
class LogEncoderMaskingTest {

    private final ObjectMapper json = new ObjectMapper();
    private LogbackConfigUnderTest config;

    @BeforeEach
    void load() {
        // The <springProperty> values, plus the logging.pattern.* Spring Boot would pass.
        Map<String, String> properties = new HashMap<>(
                LogbackConfigUnderTest.springLoggingProperties(Path.of("src/main/resources")));
        properties.putAll(Map.of("applicationName", "api-gateway", "environment", "staging"));
        config = LogbackConfigUnderTest.load("logback-spring.xml", properties);
    }

    @AfterEach
    void close() {
        config.close();
    }

    @Test
    void should_loadTheProductionConfig_when_testConfigIsAlsoOnTheClasspath() throws Exception {
        assertThat(config.source().toString()).endsWith("/main/logback-spring.xml");
        config.logger("ch.batbern.gateway.Probe").info("probe");
        JsonNode line = json.readTree(config.output());
        assertThat(line.get("application").asText()).isEqualTo("api-gateway");
        assertThat(line.get("environment").asText()).isEqualTo("staging");
    }

    @Test
    void should_maskAddress_when_inMessageArgumentAndException() throws Exception {
        Logger log = config.logger("ch.batbern.gateway.routing.DomainRouter");
        log.warn("downstream said: anna.meier@example.ch is taken, retry for {}", "bert.keller@example.org",
                new IllegalStateException("conflict for carla.rossi@example.net",
                        new RuntimeException("duplicate key (email)=(dora.frei%40example.ch)")));

        String out = config.output();
        assertThat(out).doesNotContain("anna.meier@").doesNotContain("bert.keller@")
                .doesNotContain("carla.rossi@").doesNotContain("dora.frei%40");
        JsonNode line = json.readTree(out);
        assertThat(line.get("message").asText())
                .isEqualTo("downstream said: a***@example.ch is taken, retry for b***@example.org");
        assertThat(line.get("stack_trace").asText())
                .contains("java.lang.IllegalStateException: conflict for c***@example.net")
                .contains("Caused by: java.lang.RuntimeException: duplicate key (email)=(d***%40example.ch)")
                .contains("at ch.batbern.gateway.security.LogEncoderMaskingTest");
    }

    @Test
    void should_keepMetricFilterTerms_when_apiRequestLineIsMasked() throws Exception {
        // Same call shape as ClientErrorMetricsFilter.record.
        config.logger(ClientErrorMetricsFilter.class.getName()).info("{} status={} clientError={} method={} path={}",
                ClientErrorMetricsFilter.LOG_MARKER, 404, true, "DELETE",
                "/api/v1/users/me/additional-emails/eva.huber@example.ch");

        String out = config.output();
        // alb-alarms.ts: allTerms('GATEWAY_API_REQUEST', '@timestamp') and (…, 'clientError=true')
        assertThat(out).contains("GATEWAY_API_REQUEST").contains("@timestamp")
                .contains(ClientErrorMetricsFilter.CLIENT_ERROR_FIELD).doesNotContain("eva.huber@");
        assertThat(json.readTree(out).get("message").asText()).isEqualTo(
                "GATEWAY_API_REQUEST status=404 clientError=true method=DELETE path=/api/v1/users/me/additional-emails/"
                        + "e***@example.ch");
    }
}
