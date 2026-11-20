package ch.batbern.companyuser.client;

import java.util.List;
import java.util.Map;

/**
 * The company-reference contract CUMS owns (ADR-016 decision 5, issue sweep epic 4, #1251):
 * {@code /internal/company-references/{preview,merge}} in
 * {@code docs/api/internal/cums-internal.openapi.yml}. Every holder configured under
 * {@code batbern.contracts.company-references.implementers} (a service that stores company keys)
 * counts and moves the rows it keeps under a company key. A company merge and a rename call it; CUMS
 * names no holder in code. A merge is idempotent at every holder.
 */
public interface CompanyReferenceContract {

    /** The configuration name of the contract. */
    String NAME = "company-references";

    /**
     * Every holder's preview, in configured order.
     *
     * @throws CallFailedException when a holder did not answer
     */
    List<Preview> preview(String sourceKey, String targetKey);

    /**
     * Every holder moves its rows from the source key to the target key, in configured order; the
     * first failure stops the call.
     *
     * @throws MoveBlockedException when a holder refused (409)
     * @throws CallFailedException  when a holder did not complete its part
     */
    List<Result> merge(MergeCall call);

    /** Rows per {@code table.column} ({@code conflicts} per table) and why a holder refuses. */
    record Preview(Map<String, Integer> moves, Map<String, Integer> conflicts, List<String> blockers) {
    }

    /** What a holder moved and removed: counts and the row ids per {@code table.column}, for the merge log. */
    record Result(Map<String, Integer> moved, Map<String, Integer> removed, Map<String, List<String>> movedKeys,
            Map<String, List<String>> removedKeys) {
    }

    /** Why the references move. */
    enum Trigger { MERGE, RENAME }

    record MergeCall(String sourceKey, String targetKey, String performedBy, Trigger trigger) {
    }

    /**
     * A holder did not complete its part; CUMS must not change its own rows. {@link #completed()} holds
     * the answers of the holders that moved their rows before it (for the merge log; empty by default).
     */
    class CallFailedException extends RuntimeException {
        private final List<Result> completed;

        public CallFailedException(String message, Throwable cause) {
            this(message, cause, List.of());
        }

        public CallFailedException(String message, Throwable cause, List<Result> completed) {
            super(message, cause);
            this.completed = List.copyOf(completed);
        }

        /** The answers of the holders that completed their part before this one failed. */
        public List<Result> completed() {
            return completed;
        }
    }

    /**
     * A holder refused: its preview named a blocker (e.g. {@code MERGE_BLOCKED_BOTH_PARTNERS}, in
     * {@link #blockers()}), or it answered the merge itself with 409 (a race; no blockers then).
     */
    class MoveBlockedException extends RuntimeException {
        private final List<String> blockers;

        public MoveBlockedException(String message) {
            this(message, List.of());
        }

        public MoveBlockedException(String message, List<String> blockers) {
            super(message);
            this.blockers = List.copyOf(blockers);
        }

        /** The preview's blockers of every holder; empty when a holder refused the merge call itself. */
        public List<String> blockers() {
            return blockers;
        }
    }
}
