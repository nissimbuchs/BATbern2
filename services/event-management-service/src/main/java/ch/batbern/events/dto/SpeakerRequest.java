package ch.batbern.events.dto;

import ch.batbern.events.domain.SpeakerAvailability;
import ch.batbern.shared.types.SpeakerWorkflowState;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request DTO for creating/updating a Speaker - Story 6.0.
 *
 * ADR-003/ADR-004: Speaker references User via username.
 * User-owned fields (email, name, bio, photo) are NOT included here.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SpeakerRequest {

    /**
     * Username identifying the User entity (ADR-003).
     * Must match pattern: lowercase.firstname.lastname
     */
    @NotBlank(message = "Username is required")
    @Pattern(regexp = "^[a-z]+\\.[a-z]+(\\.[0-9]+)?$", message = "Username must be in format: firstname.lastname")
    @Size(max = 100, message = "Username cannot exceed 100 characters")
    private String username;

    /**
     * Speaker availability status.
     */
    private SpeakerAvailability availability;

    /**
     * Speaker workflow state (for admin updates).
     */
    private SpeakerWorkflowState workflowState;

    /**
     * Areas of technical expertise.
     */
    private List<String> expertiseAreas;

    /**
     * Topics the speaker can present on.
     */
    private List<String> speakingTopics;

    /**
     * LinkedIn profile URL.
     */
    @Size(max = 500, message = "LinkedIn URL cannot exceed 500 characters")
    private String linkedInUrl;

    /**
     * Twitter/X handle.
     */
    @Size(max = 100, message = "Twitter handle cannot exceed 100 characters")
    private String twitterHandle;

    /**
     * Professional certifications.
     */
    private List<String> certifications;

    /**
     * Languages speaker can present in.
     */
    private List<String> languages;
}
