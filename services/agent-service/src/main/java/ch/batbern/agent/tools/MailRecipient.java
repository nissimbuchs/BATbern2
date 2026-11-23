package ch.batbern.agent.tools;

import java.util.Locale;
import java.util.Objects;

/**
 * One recipient of a mail a tool would send (story 7, AD-6, AD-7). The gate counts the distinct
 * non-organizer recipients per run and per day; the address is the identity it counts by.
 *
 * @param address   the mail address, stripped and lowercased, so one person counts once
 * @param organizer whether the tool resolved this address from the organizer side (an organizer user
 *                  or an organizer alias read from the API); never from anything the model claims
 */
public record MailRecipient(String address, boolean organizer) {

    /** Longest address RFC 5321 allows. */
    public static final int MAX_ADDRESS_LENGTH = 254;

    public MailRecipient {
        Objects.requireNonNull(address, "address");
        address = address.strip().toLowerCase(Locale.ROOT);
        int at = address.indexOf('@');
        if (address.isEmpty() || address.length() > MAX_ADDRESS_LENGTH || at < 1 || at != address.lastIndexOf('@')
                || at == address.length() - 1 || address.chars().anyMatch(Character::isWhitespace)) {
            // The message never carries the value (#1202)
            throw new IllegalArgumentException("A recipient needs one mail address");
        }
    }

    /** A recipient who is not an organizer: the ones the gate counts. */
    public static MailRecipient external(String address) {
        return new MailRecipient(address, false);
    }

    /** A recipient the tool resolved as an organizer. */
    public static MailRecipient organizer(String address) {
        return new MailRecipient(address, true);
    }
}
