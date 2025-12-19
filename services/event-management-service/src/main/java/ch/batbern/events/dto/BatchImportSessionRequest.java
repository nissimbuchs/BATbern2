package ch.batbern.events.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request DTO for batch importing sessions from legacy JSON (sessions.json)
 *
 * Maps from legacy format:
 * {
 *   "bat": 142,
 *   "pdf": "BAT01_RTC_IBISDesktop.pdf",
 *   "title": "IBIS Desktop",
 *   "abstract": "Die RTC hat sich...",
 *   "authoren": "",
 *   "referenten": [...]
 * }
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchImportSessionRequest {

    /**
     * Event number (e.g., 142 for BATbern142)
     * Used to lookup the event for this session
     */
    @NotNull(message = "Event number (bat) is required")
    private Integer bat;

    /**
     * Session title
     */
    @NotBlank(message = "Title is required")
    private String title;

    /**
     * Session description (from "abstract" field in legacy JSON)
     * Using "sessionAbstract" because "abstract" is a Java reserved keyword
     * @JsonProperty maps the JSON field "abstract" to this Java field
     */
    @JsonProperty("abstract")
    private String sessionAbstract;

    /**
     * PDF filename from legacy data
     * Will be appended to description for reference
     */
    private String pdf;

    /**
     * Moderator names (sometimes empty string in legacy data)
     */
    private String authoren;

    /**
     * List of speakers for this session
     * Empty list means no speakers (will assign event organizer as moderator)
     */
    private List<LegacySpeaker> referenten;

    /**
     * Nested DTO for legacy speaker data
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LegacySpeaker {
        /**
         * Full name with company (e.g., "Nissim J. Buchs, RTC AG")
         */
        private String name;

        /**
         * Speaker biography
         */
        private String bio;

        /**
         * Company identifier (lowercase, e.g., "rtc", "mobiliar")
         */
        private String company;

        /**
         * Portrait image filename
         */
        private String portrait;

        /**
         * Speaker ID (used to match to username in user_profiles)
         * This is the key field for speaker assignment
         */
        private String speakerId;
    }
}
