package ch.batbern.events.mailcenter.compose;

import ch.batbern.shared.service.EmailService;

import java.util.List;

/**
 * A mail as one recipient gets it, before redaction.
 *
 * @param subject     subject as sent
 * @param html        body as sent
 * @param templateKey the template actually used (speakers in automatic mode differ per recipient)
 * @param locale      language actually used
 * @param replyTo     Reply-To, or null for the configured default
 * @param secrets     plain-text credentials in the text, masked before display and recording
 * @param attachments attachments (the newsletter's calendar entry)
 */
public record Rendering(String subject, String html, String templateKey, String locale, String replyTo,
        List<String> secrets, List<EmailService.EmailAttachment> attachments) {

    public Rendering {
        secrets = secrets == null ? List.of() : List.copyOf(secrets);
        attachments = attachments == null ? List.of() : List.copyOf(attachments);
    }
}
