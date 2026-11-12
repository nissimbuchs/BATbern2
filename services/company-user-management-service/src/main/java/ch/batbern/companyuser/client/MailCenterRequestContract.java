package ch.batbern.companyuser.client;

import java.time.Instant;
import java.util.UUID;

/**
 * The Mail Center request contract CUMS owns (ADR-016 decision 4, Epic 17 entry 7):
 * {@code POST /internal/mail-center-requests} and {@code .../{requestId}/decision} in
 * {@code docs/api/internal/cums-internal.openapi.yml}. Every implementer configured under
 * {@code batbern.contracts.mail-center-requests.implementers} opens the conversation in which
 * organizers decide a profile merge request (#1237) or a company logo request (#1246), and closes it
 * once decided. CUMS names no implementing service. Both calls are idempotent at the implementer.
 */
public interface MailCenterRequestContract {

    /** The configuration name of the contract. */
    String NAME = "mail-center-requests";

    enum Kind { MERGE, LOGO }

    /** A request was filed; throws {@link MailCenterCallFailedException} when an implementer did not take it. */
    void opened(Opened request);

    /**
     * A request was decided (or withdrawn); every implementer is told, also after one failed. Throws
     * {@link MailCenterCallFailedException} naming every implementer that did not take it.
     */
    void decided(UUID requestId, Decided decision);

    record Opened(Kind kind, UUID requestId, String requesterName, String requesterEmail, String subject,
            String note) {
    }

    /** {@code status} APPROVED or DECLINED, or WITHDRAWN when the filing rolled back after the open. */
    record Decided(Kind kind, String status, String decidedBy, Instant decidedAt) {
    }

    /** An implementer did not take the call; at filing, the request must not be stored without its conversation. */
    class MailCenterCallFailedException extends RuntimeException {
        public MailCenterCallFailedException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
