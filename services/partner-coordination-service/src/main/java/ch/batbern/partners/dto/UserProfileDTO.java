package ch.batbern.partners.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for User Profile data from User Service API.
 *
 * Used to enrich partner contacts with user details.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileDTO {
    private String username;
    private String email;
    private String firstName;
    private String lastName;
    private String profilePictureUrl;
}
