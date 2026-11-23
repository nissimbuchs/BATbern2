package ch.batbern.agent.security;

import ch.batbern.shared.events.SpringDomainEventPublisher;
import ch.batbern.shared.test.LogbackConfigUnderTest;
import ch.qos.logback.classic.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * #1202: no e-mail address reaches CloudWatch from agent-service. Loads the production
 * {@code logback-spring.xml} into a context of its own: Spring Boot's plain-text console line, with
 * message text, argument, exception message and {@code Caused by:} lines masked, and the literal
 * markers the CDK metric filters match ({@code infrastructure/lib/constructs/ecs-service-alarms.ts})
 * still in the line.
 */
class LogEncoderMaskingTest {

    private LogbackConfigUnderTest config;

    @BeforeEach
    void load() {
        // The production pattern: Spring Boot passes logging.pattern.* from application.yml/.properties.
        config = LogbackConfigUnderTest.load("logback-spring.xml",
                LogbackConfigUnderTest.springLoggingProperties(Path.of("src/main/resources")));
    }

    @AfterEach
    void close() {
        config.close();
    }

    @Test
    void should_maskAddress_when_inMessageArgumentAndException() {
        assertThat(config.source().toString()).endsWith("/main/logback-spring.xml");
        Logger log = config.logger("ch.batbern.agent.run.AgentRunQueryService");
        log.warn("registration for anna.meier@example.ch failed, notifying {}", "bert.keller@example.org",
                new IllegalStateException("could not send to carla.rossi@example.net",
                        new RuntimeException("Key (email)=(dora.frei%40example.ch) already exists")));

        String out = config.output();
        assertThat(out)
                .doesNotContain("anna.meier@").doesNotContain("bert.keller@")
                .doesNotContain("carla.rossi@").doesNotContain("dora.frei%40")
                // agent-service's logging.pattern.console: date, " - ", the message (unchanged by #1202).
                .containsPattern("(?m)^\\d{4}-\\d\\d-\\d\\d \\d\\d:\\d\\d:\\d\\d - "
                        + "registration for a\\*\\*\\*@example\\.ch failed, notifying b\\*\\*\\*@example\\.org$")
                .contains("java.lang.IllegalStateException: could not send to c***@example.net")
                .contains("Caused by: java.lang.RuntimeException: Key (email)=(d***%40example.ch) already exists")
                .contains("at ch.batbern.agent.security.LogEncoderMaskingTest");
    }

    @Test
    void should_keepMetricFilterMarker_when_listenerFailureLineIsMasked() {
        // Same call shape as SpringDomainEventPublisher.publish (the shared listener-failure marker).
        var listenerFailure = new IllegalStateException("no user eva.huber@example.ch");
        config.logger(SpringDomainEventPublisher.class.getName()).error(
                SpringDomainEventPublisher.LOG_MARKER
                        + " listener={} event_type={} aggregate={} {}: the caller continues",
                "RunListener", "RunEnded", "AgentRun", "eva.huber@example.ch", listenerFailure);

        String out = config.output();
        assertThat(out).doesNotContain("eva.huber@")
                .contains("DOMAIN_EVENT_LISTENER_FAILED listener=RunListener event_type=RunEnded "
                        + "aggregate=AgentRun e***@example.ch: the caller continues");
    }
}
