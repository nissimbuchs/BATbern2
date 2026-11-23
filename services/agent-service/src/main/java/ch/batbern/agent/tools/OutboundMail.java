package ch.batbern.agent.tools;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * The mail a tool call would send (story 7, AD-7): its recipients, as the tool resolved them.
 *
 * @param recipients every recipient (to, cc, bcc alike), at least one
 */
public record OutboundMail(List<MailRecipient> recipients) {

    public OutboundMail {
        recipients = List.copyOf(Objects.requireNonNull(recipients, "recipients"));
        if (recipients.isEmpty()) {
            throw new IllegalArgumentException("A mail has at least one recipient");
        }
    }

    /** A mail to {@code recipients}. */
    public static OutboundMail to(MailRecipient... recipients) {
        return new OutboundMail(List.of(recipients));
    }

    /**
     * The distinct addresses of the recipients who are not organizers, in order. An address the tool
     * declared both ways counts as not an organizer: the safer reading.
     */
    public Set<String> nonOrganizerAddresses() {
        Set<String> addresses = new LinkedHashSet<>();
        recipients.stream().filter(recipient -> !recipient.organizer())
                .forEach(recipient -> addresses.add(recipient.address()));
        return addresses;
    }

    /** How many distinct addresses the mail goes to. */
    public int distinctAddressCount() {
        return (int) recipients.stream().map(MailRecipient::address).distinct().count();
    }

    /** How many distinct addresses count as organizers: resolved so, and never declared otherwise. */
    public long organizerCount() {
        Set<String> nonOrganizers = nonOrganizerAddresses();
        return recipients.stream().filter(MailRecipient::organizer).map(MailRecipient::address)
                .filter(address -> !nonOrganizers.contains(address)).distinct().count();
    }
}
