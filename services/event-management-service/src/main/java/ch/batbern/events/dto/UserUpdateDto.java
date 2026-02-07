package ch.batbern.events.dto;

import lombok.Builder;
import lombok.Data;

/**
 * DTO for updating user profile fields in Company Service.
 * Story 6.2b: Speaker Profile Update Portal (AC10)
 *
 * Used for cross-service sync: Event Service -> Company Service.
 * Null fields are ignored (patch semantics).
 */
@Data
@Builder
public class UserUpdateDto {

    private String firstName;
    private String lastName;
    private String bio;
    private String profilePictureUrl;

    /**
     * Check if any user fields are set for update.
     */
    public boolean hasUpdates() {
        return firstName != null || lastName != null || bio != null || profilePictureUrl != null;
    }
}
