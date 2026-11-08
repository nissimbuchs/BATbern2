package ch.batbern.shared.unit.config;

import ch.batbern.shared.config.SharedObjectMapperConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Story 17.2 part B: pins the settings of the services' ObjectMapper, which moved here unchanged
 * from the EventBridge configuration. API payloads depend on them (ISO dates, indented output).
 */
class SharedObjectMapperConfigTest {

    private final ObjectMapper mapper = new SharedObjectMapperConfig().objectMapper();

    @Test
    @DisplayName("should_writeIsoDatesIndented_when_serializing")
    void should_writeIsoDatesIndented_when_serializing() throws Exception {
        String json = mapper.writeValueAsString(new Sample("id-1", Instant.parse("2024-01-15T10:30:00Z")));

        assertThat(json).contains("\"timestamp\" : \"2024-01-15T10:30:00Z\"");
        assertThat(json).contains("\n");
        assertThat(mapper.getSerializationConfig().isEnabled(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS))
            .isFalse();
        assertThat(mapper.getSerializationConfig().isEnabled(SerializationFeature.INDENT_OUTPUT)).isTrue();
    }

    @Test
    @DisplayName("should_roundTripInstant_when_javaTimeModuleRegistered")
    void should_roundTripInstant_when_javaTimeModuleRegistered() throws Exception {
        Instant now = Instant.parse("2024-01-15T10:30:00Z");

        Sample back = mapper.readValue(mapper.writeValueAsString(new Sample("id-1", now)), Sample.class);

        assertThat(back.timestamp).isEqualTo(now);
    }

    static class Sample {
        public String id;
        public Instant timestamp;

        Sample() {
        }

        Sample(String id, Instant timestamp) {
            this.id = id;
            this.timestamp = timestamp;
        }
    }
}
