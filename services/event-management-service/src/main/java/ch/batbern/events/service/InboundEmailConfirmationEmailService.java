package ch.batbern.events.service;

import ch.batbern.events.mailcenter.render.MailContent;
import ch.batbern.events.mailcenter.render.MailPipeline;
import ch.batbern.events.mailcenter.render.RenderedMail;
import ch.batbern.shared.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Sends confirmation emails after inbound email actions (Story 10.17 — AC7).
 *
 * <ul>
 *   <li>After unsubscribe → sends {@code unsubscribe-confirmation-{locale}.html}</li>
 *   <li>No cancel confirmation since Story 16.0 (#1162): a reply "cancel" sends the
 *       deregistration-link mail instead, and the cancellation is confirmed by the link flow.</li>
 * </ul>
 *
 * Template loading is best-effort: if template is missing, logs a warning and skips send.
 * Templates are seeded into DB by {@link EmailTemplateSeedService} on startup.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InboundEmailConfirmationEmailService {

    private final EmailService emailService;
    private final MailPipeline mailPipeline;

    /**
     * Send unsubscribe confirmation email to the given address.
     * Best-effort: failures are logged, not thrown.
     *
     * @param email  recipient address
     * @param locale BCP 47 locale code detected from reply keyword (e.g. "de", "fr", "en")
     */
    public void sendUnsubscribeConfirmation(String email, String locale) {
        try {
            String resolvedLocale = "de".equals(locale) ? "de" : "en";
            MailContent content = mailPipeline.template("unsubscribe-confirmation", resolvedLocale);
            if (content.bodyHtml().isBlank()) {
                log.warn("unsubscribe-confirmation template not found — skipping confirmation email to: {}***",
                        email.substring(0, Math.min(5, email.length())));
                return;
            }
            // Story 16.11: through the one pipeline, so the BATbern layout and {{baseUrl}} are filled in
            RenderedMail mail = mailPipeline.renderNotice(content, mailPipeline.variables().system().asMap());
            String subject = mail.subject() != null ? mail.subject()
                    : resolvedLocale.equals("de")
                            ? "Newsletter Abmeldung bestätigt"
                            : "Newsletter Unsubscribe Confirmed";
            emailService.sendHtmlEmail(email, subject, mail.html());
        } catch (Exception e) {
            log.warn("Failed to send unsubscribe confirmation to: {}*** — {}",
                    email.substring(0, Math.min(5, email.length())), e.getMessage());
        }
    }
}
