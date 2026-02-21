package ch.batbern.events.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for confirming speaker profile photo upload.
 * Story 6.2b: Speaker Profile Update Portal - AC7 (Profile Photo Upload)
 *
 * Returned by POST /api/v1/speaker-portal/profile/photo/confirm
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PhotoConfirmResponse {

    /**
     * CloudFront URL of the uploaded profile picture.
     * This URL is stored in User.profilePictureUrl.
     * Format: https://cdn.batbern.ch/speaker-profiles/{year}/{username}/photo-{id}.{ext}
     */
    private String profilePictureUrl;
}
