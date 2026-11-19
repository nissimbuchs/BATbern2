package ch.batbern.shared.test;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The harness that loads a service's real logback configuration (#1202). */
class LogbackConfigUnderTestTest {

    @Test
    void should_tolerateSpringElements_when_loadingWithPlainJoran() {
        try (var config = LogbackConfigUnderTest.load("logback-spring-elements-test.xml", Map.of())) {
            config.logger("x").info("to anna@example.ch");
            assertThat(config.output()).contains("a***@example.ch");
        }
    }

    @Test
    void should_fail_when_aRealErrorMentionsSpringProfile() {
        assertThatThrownBy(() -> LogbackConfigUnderTest.load("logback-broken-named-springprofile-test.xml", Map.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ch.batbern.missing.springProfile");
    }

    @Test
    void should_fail_when_aPatternLeavesExceptionsToThePlainConverter() {
        Map<String, String> noThrowableWord = Map.of("CONSOLE_LOG_PATTERN", "%d - %msg%n");
        assertThatThrownBy(() -> LogbackConfigUnderTest.load("logback-unmasked-throwable-test.xml", noThrowableWord))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ThrowableProxyConverter");
    }

    @Test
    void should_readLoggingPatterns_when_baseAndStagingDocumentsSetThem() {
        Map<String, String> props = LogbackConfigUnderTest.springLoggingProperties(
                Path.of("src/test/resources/spring-logging-props"));

        // Production runs base + staging; the local document never applies there.
        assertThat(props).containsExactlyInAnyOrderEntriesOf(Map.of(
                "CONSOLE_LOG_PATTERN", "%d - %msg%n",
                "LOG_EXCEPTION_CONVERSION_WORD", "%wEx",
                "LOG_LEVEL_PATTERN", "%5p [staging]"));
    }
}
