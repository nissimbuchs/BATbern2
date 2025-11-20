package ch.batbern.migration.model.target;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.UUID;

/**
 * Response from Event Management API after creating event
 */
@Data
public class EventResponse {

    @JsonProperty("id")
    private UUID id;

    @JsonProperty("eventCode")
    private String eventCode;

    @JsonProperty("title")
    private String title;

    @JsonProperty("eventNumber")
    private Integer eventNumber;
}
