package ch.batbern.events.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Response DTO for version history
 * Story BAT-11 (5.7): Slot Assignment & Progressive Publishing
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VersionHistoryResponse {

    @JsonProperty("versions")
    private List<VersionDetail> versions;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VersionDetail {
        @JsonProperty("versionNumber")
        private Integer versionNumber;

        @JsonProperty("phase")
        private String phase;

        @JsonProperty("publishedAt")
        private Instant publishedAt;

        @JsonProperty("publishedBy")
        private String publishedBy;

        @JsonProperty("isCurrent")
        private Boolean isCurrent;
    }
}
