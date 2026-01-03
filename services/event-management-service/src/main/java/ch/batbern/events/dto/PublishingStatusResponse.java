package ch.batbern.events.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response DTO for publishing status
 * Story BAT-11 (5.7): Slot Assignment & Progressive Publishing
 *
 * Returns current publishing validation status for all phases
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublishingStatusResponse {

    @JsonProperty("currentPhase")
    private String currentPhase; // 'topic', 'speakers', 'agenda', or null

    @JsonProperty("publishedPhases")
    private List<String> publishedPhases;

    @JsonProperty("topic")
    private ValidationStatus topic;

    @JsonProperty("speakers")
    private ValidationStatus speakers;

    @JsonProperty("sessions")
    private SessionValidationStatus sessions;

    /**
     * Validation status for a publishing phase
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ValidationStatus {
        @JsonProperty("isValid")
        private boolean isValid;

        @JsonProperty("errors")
        private List<String> errors;
    }

    /**
     * Extended validation status for sessions with assignment counts
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SessionValidationStatus {
        @JsonProperty("isValid")
        private boolean isValid;

        @JsonProperty("errors")
        private List<String> errors;

        @JsonProperty("assignedCount")
        private int assignedCount;

        @JsonProperty("totalCount")
        private int totalCount;

        @JsonProperty("unassignedSessions")
        private List<UnassignedSession> unassignedSessions;
    }

    /**
     * Simple session info for unassigned sessions list
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UnassignedSession {
        @JsonProperty("sessionSlug")
        private String sessionSlug;

        @JsonProperty("title")
        private String title;
    }
}
