package ch.batbern.events.service;

import ch.batbern.events.domain.SessionContentVersion;
import ch.batbern.shared.types.SpeakerWorkflowState;

import java.util.Optional;

/**
 * Derives the legacy {@code contentStatus} string from the workflow state + the latest
 * {@link SessionContentVersion} row. Story 11.E.8 dropped the denormalized
 * {@code speaker_pool.content_status} column; the same JSON field is preserved on the
 * public API (organizer kanban chip labels, frontend revision UI), but its value now
 * comes from this computation instead of a column read.
 *
 * <p>Possible values mirror the prior column ({@code PENDING}, {@code SUBMITTED},
 * {@code REVISION_NEEDED}, {@code APPROVED}):
 *
 * <ul>
 *   <li>{@code PENDING} — the speaker has not submitted yet ({@link Optional#empty()}).
 *   <li>{@code SUBMITTED} — at least one version exists, the latest is awaiting review
 *       (no {@code reviewerFeedback}).
 *   <li>{@code REVISION_NEEDED} — the latest version has {@code reviewerFeedback}
 *       (moderator rejected); the speaker is expected to revise and resubmit.
 *   <li>{@code APPROVED} — the workflow has progressed to {@code QUALITY_REVIEWED}.
 *       {@code reviewerFeedback} may or may not be present on the latest row.
 * </ul>
 *
 * <p>{@code APPROVED} dominates {@code REVISION_NEEDED} — if the workflow has moved past
 * review, the prior rejection feedback is historical (the speaker resubmitted and the
 * moderator subsequently approved). The latest row's {@code reviewerFeedback} field is
 * informational only at that point.
 */
public final class ContentStatusDeriver {

    private ContentStatusDeriver() {
        // utility — no instances
    }

    public static String derive(SpeakerWorkflowState workflowState,
                                 Optional<SessionContentVersion> latestVersion) {
        if (workflowState == SpeakerWorkflowState.QUALITY_REVIEWED) {
            return "APPROVED";
        }
        if (latestVersion.isEmpty()) {
            return "PENDING";
        }
        String feedback = latestVersion.get().getReviewerFeedback();
        if (feedback != null && !feedback.isBlank()) {
            return "REVISION_NEEDED";
        }
        return "SUBMITTED";
    }
}
