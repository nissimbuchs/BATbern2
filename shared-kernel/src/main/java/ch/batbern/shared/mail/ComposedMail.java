package ch.batbern.shared.mail;

import java.util.List;
import java.util.Objects;

/**
 * A personal mail with headers the caller owns (Story 16.6: an organizer's answer to a
 * conversation). Sent by {@code EmailService.sendComposed} through {@code SendRawEmail}, because
 * {@code SendEmail} can carry neither {@code In-Reply-To}/{@code References} nor a per-mail display
 * name. The From address stays the platform's sender address.
 *
 * @param fromName   display name, e.g. {@code Anna Muster via BATbern}
 * @param to         the one recipient
 * @param subject    subject as sent
 * @param html       HTML part
 * @param text       plain-text part
 * @param replyTo    Reply-To address, e.g. {@code replies+{token}@batbern.ch}
 * @param messageId  our {@code Message-ID}, with angle brackets; kept as given
 * @param inReplyTo  {@code In-Reply-To}, or null
 * @param references {@code References}, oldest first; empty for none
 */
public record ComposedMail(
        String fromName,
        String to,
        String subject,
        String html,
        String text,
        String replyTo,
        String messageId,
        String inReplyTo,
        List<String> references) {

    public ComposedMail {
        Objects.requireNonNull(to, "to must not be null");
        Objects.requireNonNull(messageId, "messageId must not be null");
        references = references == null ? List.of() : List.copyOf(references);
    }
}
