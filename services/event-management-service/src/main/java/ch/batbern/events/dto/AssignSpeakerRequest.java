package ch.batbern.events.dto;

import ch.batbern.events.domain.SessionUser.SpeakerRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for assigning a speaker to a session
 * Story 1.15a.1b: Session-User Many-to-Many Relationship
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssignSpeakerRequest {

    @NotBlank(message = "Username is required")
    private String username; // User's public identifier

    @NotNull(message = "Speaker role is required")
    private SpeakerRole speakerRole; // primary_speaker, co_speaker, moderator, panelist

    private String presentationTitle; // Optional speaker-specific presentation title
}
