package ch.batbern.companyuser.exception;

/**
 * Get-or-create was asked to create a user for an email that is stored as another user's
 * UNVERIFIED additional email. The DB trigger {@code enforce_primary_email_not_other_additional}
 * would reject the insert, and without verification the address cannot be attributed to the
 * owner either. Maps to HTTP 409 with error code {@code EMAIL_LINKED_TO_ACCOUNT}.
 */
public class EmailLinkedToAccountException extends RuntimeException {
    public EmailLinkedToAccountException(String email) {
        super("Email '" + email + "' is linked to an existing account; sign in to continue");
    }
}
