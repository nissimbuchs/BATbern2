package ch.batbern.companyuser.exception;

/**
 * #1244 — the address is the login of another account, so it cannot become this account's login
 * address. Maps to HTTP 409 {@code EMAIL_HAS_OTHER_LOGIN}; the way out is the account merge.
 */
public class EmailHasOtherLoginException extends RuntimeException {
    public EmailHasOtherLoginException(String maskedEmail) {
        super("The address is the login of another account: " + maskedEmail);
    }
}
