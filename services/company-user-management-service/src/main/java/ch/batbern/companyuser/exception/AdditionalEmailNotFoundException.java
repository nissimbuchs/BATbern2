package ch.batbern.companyuser.exception;

import java.util.UUID;

/**
 * Story 10.32 — no matching additional email on the caller's profile (by id since 4.10d; by
 * address for a verification token whose row is gone).
 * Maps to HTTP 404.
 */
public class AdditionalEmailNotFoundException extends RuntimeException {
    public AdditionalEmailNotFoundException(String email) {
        super("Additional email not found on this profile: " + email);
    }

    /** Story 4.10d: no additional email with this id on the caller's profile. */
    public AdditionalEmailNotFoundException(UUID id) {
        super("No additional email with id " + id + " on this profile");
    }
}
