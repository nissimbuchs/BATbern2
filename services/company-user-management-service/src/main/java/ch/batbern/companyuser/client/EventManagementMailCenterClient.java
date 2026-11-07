package ch.batbern.companyuser.client;

import java.util.UUID;

/**
 * #1237: event-management's Mail Center holds the conversation in which organizers decide a profile
 * merge request ({@code POST /internal/mail-center/merge-requests}, {@code .../{id}/done}), and since
 * #1246 the one of a company logo request ({@code /internal/mail-center/logo-requests}). All calls are
 * idempotent.
 */
public interface EventManagementMailCenterClient {

    /** Opens the conversation (status OPEN, no event), with the requester as counterpart. */
    void openMergeRequest(OpenRequest request);

    /** Marks the conversation DONE once the request is decided. */
    void closeMergeRequest(UUID requestId, String decidedBy);

    /** #1246: opens the conversation of a company logo request ({@code /internal/mail-center/logo-requests}). */
    void openLogoRequest(OpenRequest request);

    /** #1246: marks the logo request's conversation DONE once it is decided. */
    void closeLogoRequest(UUID requestId, String decidedBy);

    record OpenRequest(UUID requestId, String requesterName, String requesterEmail, String subject, String note) {
    }

    /** event-management did not answer; the request must not be stored without its conversation. */
    class MailCenterCallFailedException extends RuntimeException {
        public MailCenterCallFailedException(String message) {
            super(message);
        }
    }
}
