package ch.batbern.events.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Response DTO for unpublishing a phase
 * Story BAT-11 (5.7): Slot Assignment & Progressive Publishing
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnpublishPhaseResponse {

    @JsonProperty("phase")
    private String phase;

    @JsonProperty("published")
    private Boolean published;

    @JsonProperty("unpublishedAt")
    private Instant unpublishedAt;

    @JsonProperty("cdnInvalidated")
    private Boolean cdnInvalidated;
}
