package ch.batbern.events.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Preferences DTO for speaker accept response.
 * Story 6.2a: Invitation Response Portal - AC3
 *
 * All fields are optional. Captured during accept flow
 * to help organizers with logistics planning.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SpeakerResponsePreferences {

    /**
     * Preferred presentation time slot.
     * Values: "morning", "afternoon", "no_preference"
     */
    private String timeSlot;

    /**
     * Travel requirements for the event.
     * Values: "local" (no accommodation needed), "accommodation" (needs hotel),
     *         "virtual" (attending remotely)
     */
    private String travelRequirements;

    /**
     * Technical requirements for presentation.
     * Multi-select array. Values: "mac_adapter", "remote_option", "special_av"
     */
    private String[] technicalRequirements;

    /**
     * Preliminary presentation title.
     * Speaker's initial title idea (may be refined later).
     */
    private String initialTitle;

    /**
     * Additional comments for the organizer.
     * Free-form text field for any other notes.
     */
    private String comments;
}
