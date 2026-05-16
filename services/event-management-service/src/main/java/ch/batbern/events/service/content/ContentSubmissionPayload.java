package ch.batbern.events.service.content;

/**
 * Internal record passed to the consolidated
 * {@link ch.batbern.events.service.ContentSubmissionService#submit
 * ContentSubmissionService.submit(...)} method by both the organizer-on-behalf and
 * speaker-self HTTP endpoints (Story 11.C.2 — AC4).
 *
 * <p>Per ADR-009 §"Cross-cutting: two data-entry flows, one service layer", the two
 * endpoints remain separate (different auth scopes) but they share a single backend
 * service. This record is the principal-agnostic shape the shared service consumes.
 *
 * <p>Fields:
 * <ul>
 *   <li>{@code title} — required, max 200 chars.</li>
 *   <li>{@code contentAbstract} — required, max 1000 chars.</li>
 *   <li>{@code bio} — optional. When non-null, patched onto {@code User.bio} via
 *       {@code UserApiClient.patchUserProfile} per AR14 (User Profile is the single
 *       source of truth — ADR-007 + ADR-009 §"Decision 2").</li>
 *   <li>{@code profilePictureUrl} — optional. When non-null, patched onto
 *       {@code User.profile_picture_url}.</li>
 *   <li>{@code presentationUploadId} — optional. Upload ID from a separate presigned-URL
 *       upload (Story 6.3 materials flow). When present, the service should link the
 *       uploaded file to the session via the existing materials-confirm path.</li>
 * </ul>
 */
public record ContentSubmissionPayload(
        String title,
        String contentAbstract,
        String bio,
        String profilePictureUrl,
        String presentationUploadId
) {
}
