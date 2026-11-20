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

    /** A holder did not complete its part; CUMS must not change its own rows. */
    class CallFailedException extends RuntimeException {
        public CallFailedException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** A holder refused (409), e.g. both companies are partners. */
    class MoveBlockedException extends RuntimeException {
        public MoveBlockedException(String message) {
            super(message);
        }
    }
}
