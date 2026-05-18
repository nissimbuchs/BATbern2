package ch.batbern.events.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * Result DTO for speaker invitation response.
 * Story 6.2a: Invitation Response Portal
 *
 * Returned after successful response submission.
 * Contains confirmation details and next steps for the speaker.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SpeakerResponseResult {

    /**
     * Whether the response was processed successfully.
     */
    private boolean success;

    /**
     * Speaker's display name.
     */
    private String speakerName;

    /**
     * Event title.
     */
    private String eventName;

    /**
     * Formatted event date (e.g., "20. November 2025").
     */
    private String eventDate;

    /**
     * Session title if speaker is assigned to a session.
     */
    private String sessionTitle;

    /**
     * List of next steps for the speaker.
     * e.g., ["Complete your profile", "Submit title and abstract"]
     */
    private List<String> nextSteps;

    /**
     * Content submission deadline (if accepted).
     */
    private LocalDate contentDeadline;

    /**
     * URL to the speaker dashboard (if accepted).
     */
    private String dashboardUrl;

    /**
     * Error message if response failed.
     */
    private String errorMessage;

    /**
     * Create a success result with basic info.
     */
    public static SpeakerResponseResult success(String speakerName, String eventName) {
        return SpeakerResponseResult.builder()
                .success(true)
                .speakerName(speakerName)
                .eventName(eventName)
                .build();
    }

    /**
     * Create a failure result with error message.
     */
    public static SpeakerResponseResult failure(String errorMessage) {
        return SpeakerResponseResult.builder()
                .success(false)
                .errorMessage(errorMessage)
                .build();
    }
}
