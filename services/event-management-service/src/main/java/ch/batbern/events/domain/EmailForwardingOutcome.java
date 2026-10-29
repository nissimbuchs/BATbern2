package ch.batbern.events.domain;

/**
 * Result of forwarding one inbound alias mail, persisted in {@code email_forwarding_log.outcome}.
 *
 * <p>Enum value flow: UPPER_CASE in Java, {@code lowercase} in the DB via
 * {@link ch.batbern.events.converter.EmailForwardingOutcomeConverter}.
 */
public enum EmailForwardingOutcome {
    /** Claimed, sending not finished (a crash leaves it here; it is never retried). */
    PROCESSING,
    /** Every resolved recipient was handed to SES. */
    FORWARDED,
    /** Some recipients failed; the others received the mail. */
    PARTIAL,
    /** The sender may not use any of the addressed aliases. */
    REJECTED,
    /** Authorized, but no recipient could be resolved. */
    UNRESOLVED,
    /** No recipient was reached. */
    FAILED,
    /**
     * Sending began and never finished (the task died mid-send). Some recipients may lack the
     * mail. Recorded once so redeliveries do not report it again (#1139); never retried.
     */
    STALLED
}
