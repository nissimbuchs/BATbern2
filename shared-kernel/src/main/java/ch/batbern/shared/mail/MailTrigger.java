package ch.batbern.shared.mail;

import java.util.Objects;
import java.util.UUID;

/**
 * What caused a send: one scheduler run, one organizer request, or one send audit row (Story 16.1a
 * AC2). Every {@link MailContext} built from the same trigger, event and template key lands in the
 * same send group, so a run that mails forty speakers is one recorded mail with forty recipients.
 *
 * @param runId           identifies the run or request; part of the send-group id
 * @param senderUsername  the organizer who asked for the send, or null for the system
 */
public record MailTrigger(String runId, String senderUsername) {

    public MailTrigger {
        Objects.requireNonNull(runId, "runId must not be null");
    }

    /** A new system run: a scheduler tick or an event listener. */
    public static MailTrigger system() {
        return new MailTrigger(UUID.randomUUID().toString(), null);
    }

    /** A new organizer request, e.g. one click on "send" or "remind". */
    public static MailTrigger organizer(String username) {
        return new MailTrigger(UUID.randomUUID().toString(), username);
    }

    /** A send that already has an audit row (newsletter, registrant notice): retries join its group. */
    public static MailTrigger forSend(UUID sendId, String senderUsername) {
        return new MailTrigger(sendId.toString(), senderUsername);
    }
}
