package ch.batbern.events.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for version rollback
 * Story BAT-11 (5.7): Slot Assignment & Progressive Publishing
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RollbackResponse {

    @JsonProperty("rolledBack")
    private Boolean rolledBack;

    @JsonProperty("currentVersion")
    private Integer currentVersion;

    @JsonProperty("cdnInvalidated")
    private Boolean cdnInvalidated;
}
