package ch.batbern.companyuser.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Public organizer information for display on About page
 * Only includes publicly shareable fields
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublicOrganizerResponse {

    @JsonProperty("id")
    private String id; // Username

    @JsonProperty("firstName")
    private String firstName;

    @JsonProperty("lastName")
    private String lastName;

    @JsonProperty("email")
    private String email;

    @JsonProperty("bio")
    private String bio;

    @JsonProperty("profilePictureUrl")
    private String profilePictureUrl;

    @JsonProperty("companyId")
    private String companyId;
}
