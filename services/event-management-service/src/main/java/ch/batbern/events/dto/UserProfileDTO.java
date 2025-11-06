package ch.batbern.events.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

/**
 * User profile data transfer object.
 *
 * Represents user profile information retrieved from the User Management Service API.
 * Matches the UserResponse schema from docs/api/users-api.openapi.yml
 *
 * Per ADR-003 and Story 1.16.2: The 'id' field contains username (not UUID).
 * This DTO matches the OpenAPI spec exactly.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)  // Ignore any fields from API not in this DTO
public class UserProfileDTO {

    /**
     * Username - public identifier (e.g., "john.doe")
     * Per ADR-003: This is returned as 'id' in the API response.
     *
     * The @JsonProperty annotation maps the API's 'id' field to our 'username' field.
     */
    @JsonProperty("id")
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
     * Per ADR-003: Company name, not UUID
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
