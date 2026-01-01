package ch.batbern.events.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Response DTO for change log
 * Story BAT-11 (5.7): Slot Assignment & Progressive Publishing
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChangeLogResponse {

    @JsonProperty("changes")
    private List<ChangeLogEntry> changes;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChangeLogEntry {
        @JsonProperty("field")
        private String field;

        @JsonProperty("oldValue")
        private String oldValue;

        @JsonProperty("newValue")
        private String newValue;

        @JsonProperty("changedAt")
        private Instant changedAt;

        @JsonProperty("changedBy")
        private String changedBy;
    }
}
