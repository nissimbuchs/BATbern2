package ch.batbern.events.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for configuring auto-publish schedule
 * Story BAT-11 (5.7): Slot Assignment & Progressive Publishing
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AutoPublishScheduleRequest {

    @JsonProperty("phase2Enabled")
    private Boolean phase2Enabled;

    @JsonProperty("phase2DaysBeforeEvent")
    @Min(1)
    private Integer phase2DaysBeforeEvent;

    @JsonProperty("phase3Enabled")
    private Boolean phase3Enabled;

    @JsonProperty("phase3DaysBeforeEvent")
    @Min(1)
    private Integer phase3DaysBeforeEvent;
}
