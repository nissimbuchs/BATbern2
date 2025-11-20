package ch.batbern.migration.model.target;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.ZonedDateTime;

/**
 * Event DTO for POST to Event Management API
 * Maps to Event entity in Event Management Service
 */
@Data
public class EventDto {

    @JsonProperty("eventCode")
    private String eventCode;  // "BATbern1", "BATbern2", etc.

    @JsonProperty("title")
    private String title;  // Event topic/title

    @JsonProperty("eventNumber")
    private Integer eventNumber;  // BAT number (1, 2, 3, ...)

    @JsonProperty("date")
    private ZonedDateTime date;  // Event date/time (parsed from German date string)

    @JsonProperty("eventType")
    private String eventType;  // "Abend-BAT", etc.

    @JsonProperty("status")
    private String status;  // Default "ARCHIVED" for historical events

    // Optional fields (not in minimal legacy data, but API may require)
    @JsonProperty("venueName")
    private String venueName;  // Default: "Kornhausforum" (historical venue)

    @JsonProperty("venueAddress")
    private String venueAddress;  // Default: "Kornhausplatz 18, 3011 Bern"

    @JsonProperty("venueCapacity")
    private Integer venueCapacity;  // Default: 200

    @JsonProperty("organizerId")
    private String organizerId;  // System user UUID for historical events
}
