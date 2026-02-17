package ch.batbern.companyuser.exception;

/**
 * Thrown when an organizer tries to generate a pairing code but already has 2 watches paired.
 * Story W2.1: AC2 — Max Watches Enforcement (NFR19)
 */
public class MaxWatchesExceededException extends RuntimeException {

    public MaxWatchesExceededException() {
        super("Maximum 2 watches paired. Unpair a device first.");
    }
}
