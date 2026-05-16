package ch.batbern.events.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Speaker self-service content submission via the magic-link portal.
 * Story 6.3 AC5: Content submission.
 *
 * <p>Story 11.C.2 added optional {@code bio}, {@code profilePictureUrl},
 * {@code presentationUploadId} so the speaker can patch their User profile + attach an
 * uploaded presentation in a single submit. Phase E (Story 11.E.3) will remove the
 * {@code token} field once the portal moves to Cognito Bearer auth.
 *
 * <p>{@link JsonIgnoreProperties#ignoreUnknown()} is {@code false} so unknown payload
 * fields yield 400 (Resolved Decision §3). Bean-validation `@Size`/`@NotBlank` on each
 * field added by Story 11.C.2 review patch to bring the portal endpoint to parity with
 * the organizer endpoint (AC8 "identical downstream effects").
 *
 * @param token                Magic link token (Phase E migrates this to Cognito Bearer)
 * @param title                Presentation title (required, max 200 chars)
 * @param contentAbstract      Presentation abstract (required, max 1000 chars)
 * @param bio                  Optional speaker bio; patched onto {@code User.bio}
 * @param profilePictureUrl    Optional portrait URL; patched onto {@code User.profile_picture_url}
 * @param presentationUploadId Optional upload ID from a separate presigned-URL upload
 */
@JsonIgnoreProperties(ignoreUnknown = false)
public record ContentSubmitRequest(
        @NotBlank(message = "Token is required")
        String token,
        @NotBlank(message = "Title is required")
        @Size(max = 200, message = "Title must be ≤ 200 characters")
        String title,
        @NotBlank(message = "Abstract is required")
        @Size(max = 1000, message = "Abstract must be ≤ 1000 characters")
        String contentAbstract,
        @Size(max = 5000, message = "Bio must be ≤ 5000 characters")
        String bio,
        @Size(max = 2048, message = "Profile picture URL must be ≤ 2048 characters")
        String profilePictureUrl,
        @Size(max = 200, message = "Presentation upload ID must be ≤ 200 characters")
        String presentationUploadId
) {
}
