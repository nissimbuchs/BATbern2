package ch.batbern.shared.mail;

import java.util.List;

/**
 * One send as handed to the {@link OutboundMailRecorder}: the addresses it went to, the text with
 * credentials already redacted, and what SES answered. Self-contained (no service entities), so a
 * recorder may also post it to another service (Story 16.9).
 *
 * @param to            primary recipient
 * @param cc            copies, as delivered (normalised, reserved domains dropped)
 * @param subject       subject, redacted
 * @param htmlBody      rendered HTML, redacted
 * @param sesMessageId  SES Message-ID; {@code local-{uuid}} for a mail captured on local dev; null
 *                      when SES refused the mail or no mail system is configured (tests)
 * @param failureReason SES error code when SES refused the mail, otherwise null
 */
public record OutboundMail(
        String to,
        List<String> cc,
        String subject,
        String htmlBody,
        String sesMessageId,
        String failureReason) {

    public OutboundMail {
        cc = cc == null ? List.of() : List.copyOf(cc);
    }

    public boolean failed() {
        return failureReason != null;
    }
}
