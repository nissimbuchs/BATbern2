package ch.batbern.events.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Organizer-on-behalf request to submit speaker content (Story 5.5 AC6-10).
 *
 * <p>Story 11.C.2 refactor:
 * <ul>
 *   <li><b>Removed</b> legacy ad-hoc user-identity fields
 *       ({@code username}, {@code speakerName}, {@code email}, {@code company}). The speaker's
 *       identity is established on {@code speaker_pool} at CONTACTED → READY per ADR-009;
 *       the consolidated {@code ContentSubmissionService} reads {@code speaker.username}
 *       from the pool entry rather than from the request body.</li>
 *   <li><b>Added</b> optional {@code bio}, {@code profilePictureUrl}, {@code presentationUploadId}
 *       so the organizer drawer can submit on-behalf content carrying CV/portrait/upload
 *       reference. When present, {@code bio} and {@code profilePictureUrl} are patched onto
 *       {@code User.bio} / {@code User.profile_picture_url} per AR14.</li>
 *   <li><b>Strict validation</b>: {@link JsonIgnoreProperties#ignoreUnknown()} is
 *       {@code false} so payloads carrying stale fields (the removed legacy four, or any
 *       other unknown property) yield a 400 via Jackson's
 *       {@code FAIL_ON_UNKNOWN_PROPERTIES} (Resolved Decision §3). Mirrors the
 *       {@code additionalProperties: false} guarantee on the OpenAPI schema.</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = false)
public class SubmitContentRequest {

    /**
     * Presentation title (required, becomes session.title).
     */
    @NotBlank(message = "Presentation title is required")
    @Size(max = 255, message = "Title must not exceed 255 characters")
    private String presentationTitle;

    /**
     * Presentation abstract (required, becomes session.description).
     * Max 1000 characters with character counter on the frontend.
     */
    @NotBlank(message = "Presentation abstract is required")
    @Size(max = 1000, message = "Abstract must not exceed 1000 characters")
    private String presentationAbstract;

    /**
     * Optional speaker bio. When present, patched onto {@code User.bio} for the
     * resolved speaker username via {@code UserApiClient.patchUserProfile} (Story 11.C.2 — AR14).
     */
    @Size(max = 5000, message = "Bio must not exceed 5000 characters")
    private String bio;

    /**
     * Optional speaker portrait URL (typically a CloudFront URL from a separate
     * presigned-upload flow). When present, patched onto {@code User.profile_picture_url}.
     */
    @Size(max = 2048, message = "Profile picture URL must not exceed 2048 characters")
    private String profilePictureUrl;

    /**
     * Optional upload ID from a separate presigned-URL upload (Story 6.3 materials flow).
     * Story 11.D.4 wires the organizer-on-behalf auto-link path; for 11.C.2 the field is
     * accepted on the API surface.
     */
    private String presentationUploadId;
}
