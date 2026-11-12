package ch.batbern.companyuser.client;

import java.util.List;
import java.util.Map;

/**
 * The user-reference contract CUMS owns (ADR-016 decision 5, Epic 17 entry 7b):
 * {@code /internal/user-references/{preview,merge,primary-email}} in
 * {@code docs/api/internal/cums-internal.openapi.yml}. Every holder configured under
 * {@code batbern.contracts.user-references.implementers} (a service that stores usernames) counts,
 * moves and follows the rows it keeps under a username. CUMS names no holder in code. Merge and
 * primary-email are idempotent at every holder.
 */
public interface UserReferenceContract {

    /** The configuration name of the contract. */
    String NAME = "user-references";

    /**
     * Every holder's preview, in configured order.
     *
     * @throws MergeCallFailedException when a holder did not answer
     */
    List<Preview> preview(String sourceUsername, String targetUsername);

    /**
     * Every holder moves its rows, in configured order; the first failure stops the call.
     *
     * @throws MergeBlockedException    when a holder refused (409)
     * @throws MergeCallFailedException when a holder did not complete its part
     */
    List<Result> merge(MergeCall call);

    /**
     * #1244: the person's primary address changed; every holder moves what follows the address
     * (event-management: upcoming registrations made under the old address).
     *
     * @throws MergeCallFailedException when a holder did not take the call
     */
    void primaryEmailChanged(String username, String oldEmail, String newEmail);

    record Preview(Map<String, Integer> moves, Map<String, Integer> conflicts, List<String> blockers) {
    }

    record Result(Map<String, Integer> moved, Map<String, Integer> removed) {
    }

    /** {@code trigger} is ORGANIZER or SELF_CLAIM; {@code sourceProfile} goes to a holder's merge log for an undo. */
    record MergeCall(String sourceUsername, String targetUsername, String performedBy, String trigger,
            Map<String, Object> sourceProfile) {
    }

    /** A holder did not complete its part; the profile step must not run. */
    class MergeCallFailedException extends RuntimeException {
        public MergeCallFailedException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** A holder refused (409), e.g. both profiles lead a different talk at the same event. */
    class MergeBlockedException extends RuntimeException {
        public MergeBlockedException(String message) {
            super(message);
        }
    }
}
