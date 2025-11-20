package ch.batbern.migration.model.legacy;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Legacy event data model from topics.json
 */
@Data
public class LegacyEvent {

    @JsonProperty("bat")
    private Integer bat;  // Event number (BAT 1, BAT 2, etc.)

    @JsonProperty("topic")
    private String topic;

    @JsonProperty("datum")
    private String datum;  // German date string in various formats

    @JsonProperty("eventType")
    private String eventType;  // "Abend-BAT" or other types
}
