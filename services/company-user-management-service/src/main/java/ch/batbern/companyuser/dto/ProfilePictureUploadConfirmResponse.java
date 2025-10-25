package ch.batbern.companyuser.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for profile picture upload confirmation
 * AC10: Return CloudFront URL after successful upload
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfilePictureUploadConfirmResponse {

    /**
     * CloudFront CDN URL for the uploaded profile picture
     */
    private String profilePictureUrl;
}
