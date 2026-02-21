package ch.batbern.companyuser.exception;

/**
 * Thrown when a watch pairing record cannot be found.
 * Story W2.1: AC4 — Unpair Watch
 */
public class WatchPairingNotFoundException extends RuntimeException {

    public WatchPairingNotFoundException(String deviceName) {
        super("Watch device not found: " + deviceName);
    }
}
