package ch.batbern.shared.logging;

import ch.batbern.shared.test.LogbackConfigUnderTest;
import ch.qos.logback.classic.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * #1202: the plain-text console line (EMS, PCS) masks every address once, in the encoder, through
 * the shared conversion rules, and keeps Spring Boot's line shape. Real logback, real Spring Boot
 * {@code defaults.xml}; nothing replaced.
 */
class EmailMaskingConvertersTest {

    private LogbackConfigUnderTest config;
    private Logger log;

    @BeforeEach
    void load() {
        config = LogbackConfigUnderTest.load("logback-masking-text-test.xml", Map.of());
        log = config.logger("ch.batbern.test.Masking");
    }

    @AfterEach
    void close() {
        config.close();
    }

    @Test
    void should_maskAddress_when_inMessageTextOrArgument() {
        log.info("invited anna.meier@example.ch and {} via {}", "bert@example.org", "c%40example.net");

        assertThat(config.output())
                .contains("invited a***@example.ch and b***@example.org via c***%40example.net")
                .doesNotContain("anna.meier@").doesNotContain("bert@").doesNotContain("c%40");
    }

    @Test
    void should_maskAddress_when_inExceptionMessageAndCause() {
        var cause = new IllegalStateException("duplicate key (email)=(root.cause@example.ch)");
        log.error("listener failed", new RuntimeException("could not invite top.level@example.ch", cause));

        String out = config.output();
        assertThat(out)
                .contains("java.lang.RuntimeException: could not invite t***@example.ch")
                .contains("Caused by: java.lang.IllegalStateException: duplicate key (email)=(r***@example.ch)")
                .contains("\tat ch.batbern.shared.logging.EmailMaskingConvertersTest")
                .doesNotContain("top.level@").doesNotContain("root.cause@");
    }

    @Test
    void should_keepSpringBootLineShape_when_masking() {
        log.warn("MARKER_X item={} done", "x@example.ch");

        assertThat(config.output().strip())
                .matches("\\d{4}-\\d\\d-\\d\\dT\\d\\d:\\d\\d:\\d\\d\\.\\d{3}[^ ]+ +WARN .*--- \\[.*\\] "
                        + "ch\\.batbern\\.test\\.Masking +: MARKER_X item=x\\*\\*\\*@example\\.ch done");
    }

    @Test
    void should_leaveTextAlone_when_alreadyMaskedOrNoAddress() {
        log.info("already a***@example.ch, path /api/v1/events/BATbern60, user {}", (Object) null);
        log.info(null);

        assertThat(config.output())
                .contains(": already a***@example.ch, path /api/v1/events/BATbern60, user null")
                .contains(": null");
    }
}
