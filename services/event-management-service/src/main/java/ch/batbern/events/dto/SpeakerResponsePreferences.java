package ch.batbern.events.dto;

import ch.batbern.events.domain.PreferredTimeSlot;
import ch.batbern.events.domain.TechnicalRequirement;
import ch.batbern.events.domain.TravelRequirement;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Speaker response preferences DTO - Story 6.2.
 *
 * Captures optional preferences when a speaker accepts an invitation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SpeakerResponsePreferences {

    /**
     * Speaker's preferred time slot for presenting.
     */
    private PreferredTimeSlot preferredTimeSlot;

    /**
     * Speaker's travel/accommodation requirements.
     */
    private TravelRequirement travelRequirements;

    /**
     * Technical equipment requirements.
     */
    private List<TechnicalRequirement> technicalRequirements;

    /**
     * Initial presentation title proposed by speaker.
     */
    @Size(max = 200, message = "Presentation title must be at most 200 characters")
    private String initialPresentationTitle;

    /**
     * Additional comments from speaker to organizer.
     */
    @Size(max = 2000, message = "Comments must be at most 2000 characters")
    private String commentsForOrganizer;
}
