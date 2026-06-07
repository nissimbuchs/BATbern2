package ch.batbern.companyuser.exception;

/**
 * Story 10.32 — the caller has reached the per-user cap on additional emails.
 * Maps to HTTP 422 with error code {@code ADDITIONAL_EMAIL_LIMIT_REACHED}.
 */
public class AdditionalEmailLimitReachedException extends RuntimeException {

    private final int max;

    public AdditionalEmailLimitReachedException(int max) {
        super("Additional email limit reached: maximum " + max + " per user");
        this.max = max;
    }

    public int getMax() {
        return max;
    }
}
