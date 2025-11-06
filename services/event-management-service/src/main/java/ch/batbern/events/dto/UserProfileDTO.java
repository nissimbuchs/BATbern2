package ch.batbern.events.dto;

import lombok.*;

import java.util.UUID;

/**
 * User profile data transfer object.
 *
 * Represents user profile information retrieved from the User Management Service API.
 * Matches the response schema from GET /api/v1/users/{username}.
 *
 * Used to replace direct database access to user_profiles table with REST API calls.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserProfileDTO {

    /**
     * Internal UUID - used for database foreign keys
     */
    private UUID id;

    /**
     * Username - public identifier (e.g., "john.doe")
     */
    private String username;

    /**
     * User's email address
     */
    private String email;

    /**
     * User's first name
     */
    private String firstName;

    /**
     * User's last name
     */
    private String lastName;

    /**
     * Company identifier/name (e.g., "GoogleZH")
     */
    private String companyId;

    /**
     * Profile picture URL (CDN location)
     */
    private String profilePictureUrl;

    /**
     * User biography/description
     */
    private String bio;

    /**
     * Account active status
     */
    private Boolean active;
}
