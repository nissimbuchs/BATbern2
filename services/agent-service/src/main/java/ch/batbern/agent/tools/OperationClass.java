package ch.batbern.agent.tools;

import java.util.Locale;

/**
 * The risk class of a public operation and of the tool that calls it (AD-6), lowest first:
 * {@code read} &lt; {@code reversible} &lt; {@code reply} &lt; {@code binding} &lt; {@code audience}.
 * The declaration order is the order; {@link #compareTo} compares risk.
 */
public enum OperationClass {

    /** Changes nothing. */
    READ,
    /** Changes something an undo operation can take back. */
    REVERSIBLE,
    /** One mail to the run's requester, about their own case, in their conversation. */
    REPLY,
    /** Cannot be taken back, or mails one non-organizer other than the requester. */
    BINDING,
    /** Reaches more than one non-organizer. */
    AUDIENCE;

    /** The name the architecture uses: {@code read}, {@code reversible}, ... */
    public String wireName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
