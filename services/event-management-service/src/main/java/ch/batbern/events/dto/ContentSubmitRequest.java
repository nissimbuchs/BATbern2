package ch.batbern.events.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

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
 * fields yield 400 (Resolved Decision §3).
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
        String token,
        String title,
        String contentAbstract,
        String bio,
        String profilePictureUrl,
        String presentationUploadId
) {
}
