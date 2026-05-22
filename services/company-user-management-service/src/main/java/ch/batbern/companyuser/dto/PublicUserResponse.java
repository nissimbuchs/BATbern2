package ch.batbern.companyuser.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Public user information for anonymous lookup by username.
 * Story 11.C.1 (AC7): mirror of {@link PublicOrganizerResponse} for any user,
 * with a strictly narrow projection — no email, no bio, no role list, no PII
 * beyond the public name/portrait pair that already appears on event pages.
 *
 * Used by the public archive page's portrait lookup (web-frontend useUserPortrait
 * hook) since GET /api/v1/speakers/{username} was retired with the Speaker entity.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublicUserResponse {

    @JsonProperty("username")
    private String username;

    @JsonProperty("firstName")
    private String firstName;

    @JsonProperty("lastName")
    private String lastName;

    @JsonProperty("profilePictureUrl")
    private String profilePictureUrl;
}
