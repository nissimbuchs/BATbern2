package ch.batbern.events.mailcenter.inbound;

import ch.batbern.events.domain.MailMessage;

import java.util.List;

/**
 * One inbound mail as the Mail Center needs it (Story 16.2a): the threading headers, who wrote it,
 * the text to show, attachment names, and the headers the classifier reads. Built by
 * {@link InboundMailParser}; never holds attachment content.
 *
 * @param messageId     its own Message-ID, without angle brackets, or null
 * @param inReplyTo     first id of In-Reply-To, without brackets, or null
 * @param references    References ids in header order (oldest first, newest last), without brackets
 * @param fromAddress   lowercased, or null when there is no parseable From
 * @param fromName      display name, decoded, or null
 * @param replyTo       first Reply-To address, lowercased, or null
 * @param to            To addresses, lowercased
 * @param cc            Cc addresses, lowercased
 * @param subject       decoded subject, never null
 * @param text          the plain text: the text/plain part, else text extracted from the HTML; never null
 * @param html          sanitized HTML, only when the mail has no text/plain part, else null
 * @param attachments   names, types and sizes
 * @param contentType   top-level Content-Type, for bounce detection
 * @param returnPath    Return-Path header
 * @param autoSubmitted Auto-Submitted header
 * @param precedence    Precedence header
 * @param xAutoreply    X-Autoreply header
 * @param xAutorespond  X-Autorespond header
 * @param forwarded     carries our own forwarding marker (a loop)
 */
public record InboundMail(
        String messageId,
        String inReplyTo,
        List<String> references,
        String fromAddress,
        String fromName,
        String replyTo,
        List<String> to,
        List<String> cc,
        String subject,
        String text,
        String html,
        List<MailMessage.Attachment> attachments,
        String contentType,
        String returnPath,
        String autoSubmitted,
        String precedence,
        String xAutoreply,
        String xAutorespond,
        boolean forwarded) {

    public InboundMail {
        references = references == null ? List.of() : List.copyOf(references);
        to = to == null ? List.of() : List.copyOf(to);
        cc = cc == null ? List.of() : List.copyOf(cc);
        attachments = attachments == null ? List.of() : List.copyOf(attachments);
        subject = subject == null ? "" : subject;
        text = text == null ? "" : text;
    }

    /** The same mail with these attachments (Story 16.6: with the keys of the kept files). */
    public InboundMail withAttachments(List<MailMessage.Attachment> kept) {
        return new InboundMail(messageId, inReplyTo, references, fromAddress, fromName, replyTo, to, cc, subject,
                text, html, kept, contentType, returnPath, autoSubmitted, precedence, xAutoreply, xAutorespond,
                forwarded);
    }
}
