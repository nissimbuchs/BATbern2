package ch.batbern.events.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Response DTO for auto-publish schedule configuration
 * Story BAT-11 (5.7): Slot Assignment & Progressive Publishing
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AutoPublishScheduleResponse {

    @JsonProperty("scheduled")
    private Boolean scheduled;

    @JsonProperty("phase2Enabled")
    private Boolean phase2Enabled;

    @JsonProperty("phase2DaysBeforeEvent")
    private Integer phase2DaysBeforeEvent;

    @JsonProperty("phase2TriggerDate")
    private Instant phase2TriggerDate;

    @JsonProperty("phase3Enabled")
    private Boolean phase3Enabled;

    @JsonProperty("phase3DaysBeforeEvent")
    private Integer phase3DaysBeforeEvent;

    @JsonProperty("phase3TriggerDate")
    private Instant phase3TriggerDate;
}
