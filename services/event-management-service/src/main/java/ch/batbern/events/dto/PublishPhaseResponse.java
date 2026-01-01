package ch.batbern.events.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Response DTO for publishing a phase
 * Story BAT-11 (5.7): Slot Assignment & Progressive Publishing
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublishPhaseResponse {

    @JsonProperty("phase")
    private String phase; // topic, speakers, agenda

    @JsonProperty("published")
    private Boolean published;

    @JsonProperty("publishedAt")
    private Instant publishedAt;

    @JsonProperty("version")
    private Integer version;

    @JsonProperty("cdnInvalidated")
    private Boolean cdnInvalidated;
}
