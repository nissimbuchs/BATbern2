package ch.batbern.shared.logging;

import ch.batbern.shared.test.LogbackConfigUnderTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * #1202: the JSON console line (gateway, CUMS) masks every string value through the shared
 * {@link EmailValueMasker}, registered in {@code MaskingJsonGeneratorDecorator}; field names stay.
 */
class EmailValueMaskerTest {

    private final EmailValueMasker masker = new EmailValueMasker();

    @Test
    void should_maskString_when_itHoldsAnAddress() {
        assertThat(masker.mask(null, "to anna@example.ch, bert%40example.org"))
                .isEqualTo("to a***@example.ch, b***%40example.org");
    }

    @Test
    void should_returnNull_when_nothingToMask() {
        // null tells the generator to write the original value.
        assertThat(masker.mask(null, "a***@example.ch")).isNull();
        assertThat(masker.mask(null, "/api/v1/events")).isNull();
        assertThat(masker.mask(null, null)).isNull();
        assertThat(masker.mask(null, 42)).isNull();
    }

    @Test
    void should_maskMessageArgumentsStackTraceAndMdc_when_renderedByLogstashEncoder() throws Exception {
        try (var config = LogbackConfigUnderTest.load("logback-masking-json-test.xml", Map.of())) {
            config.mdc().put("contactEmail", "mdc.person@example.ch");
            try {
                config.logger("ch.batbern.test.Json").error("invite {} failed", "arg.person@example.ch",
                        new IllegalStateException("exists: ex.person@example.ch",
                                new RuntimeException("cause.person@example.ch")));
            } finally {
                config.mdc().remove("contactEmail");
            }

            String out = config.output();
            assertThat(out).doesNotContain("arg.person@").doesNotContain("ex.person@")
                    .doesNotContain("cause.person@").doesNotContain("mdc.person@");
            JsonNode line = new ObjectMapper().readTree(out);
            assertThat(line.get("message").asText()).isEqualTo("invite a***@example.ch failed");
            assertThat(line.get("contactEmail").asText()).isEqualTo("m***@example.ch");
            assertThat(line.get("stack_trace").asText())
                    .contains("java.lang.IllegalStateException: exists: e***@example.ch")
                    .contains("Caused by: java.lang.RuntimeException: c***@example.ch");
            assertThat(line.has("@timestamp")).isTrue();
        }
    }
}
