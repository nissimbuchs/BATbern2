package ch.batbern.companyuser.client;

import java.util.List;
import java.util.Map;

/**
 * #1183 C/D: event-management's half of a profile merge. It moves every row it holds under the
 * source username to the target and logs the merge ({@code POST /internal/users/merge}); the preview
 * counts what would move ({@code GET /internal/users/merge-preview}). Both are idempotent.
 */
public interface EventManagementUserMergeClient {

    Preview preview(String sourceUsername, String targetUsername);

    Result merge(MergeCall call);

    /**
     * #1244: the person's primary address changed; event-management moves upcoming registrations
     * made under the old address ({@code POST /internal/users/primary-email}).
     */
    void primaryEmailChanged(String username, String oldEmail, String newEmail);

    record Preview(Map<String, Integer> moves, Map<String, Integer> conflicts, List<String> blockers) {
    }

    record Result(Map<String, Integer> moved, Map<String, Integer> removed) {
    }

    /** {@code trigger} is ORGANIZER or SELF_CLAIM; {@code sourceProfile} goes to the merge log for an undo. */
    record MergeCall(String sourceUsername, String targetUsername, String performedBy, String trigger,
            Map<String, Object> sourceProfile) {
    }

    /** event-management did not complete its part; the profile step must not run. */
    class MergeCallFailedException extends RuntimeException {
        public MergeCallFailedException(String message) {
            super(message);
        }
    }

    /** event-management refused (409): both profiles lead a different talk at the same event. */
    class MergeBlockedException extends RuntimeException {
        public MergeBlockedException(String message) {
            super(message);
        }
    }
}
