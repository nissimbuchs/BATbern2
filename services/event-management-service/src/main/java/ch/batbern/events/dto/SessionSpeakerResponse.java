package ch.batbern.events.dto;

import ch.batbern.events.domain.SessionUser.SpeakerRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for session speakers (enriched with User data)
 * Story 1.15a.1b: Session-User Many-to-Many Relationship
 *
 * Combines data from SessionUser entity and User entity:
 * - User fields: username, firstName, lastName, company, profilePictureUrl
 * - SessionUser fields: speakerRole, presentationTitle, isConfirmed
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionSpeakerResponse {

    // From User entity (joined via userId)
    private String username;
    private String firstName;
    private String lastName;
    private String company;
    private String profilePictureUrl;

    // From SessionUser entity
    private SpeakerRole speakerRole;
    private String presentationTitle; // Optional speaker-specific title
    private boolean isConfirmed;
}
