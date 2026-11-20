package ch.batbern.events.service;

import ch.batbern.events.domain.NewsletterSubscriber;
import ch.batbern.events.mailcenter.render.MailContent;
import ch.batbern.events.mailcenter.render.MailPipeline;
import ch.batbern.events.mailcenter.render.RenderedMail;
import ch.batbern.events.mailcenter.variables.MailVariable;
import ch.batbern.shared.mail.MailContext;
import ch.batbern.shared.service.EmailService;
import ch.batbern.shared.utils.LoggingUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * The confirmation mail of the newsletter double opt-in (#1288): a link to the public page
 * {@code /newsletter/confirm?token=…}, which activates the subscription. German, English for a
 * subscriber whose language is {@code en}. Recorded in the Mail Center outbound log as a system mail;
 * the token link is masked there ({@code MailRedaction}) and the token is passed as a secret too.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NewsletterConfirmationEmailService {

    static final String TEMPLATE_KEY = "newsletter-confirmation";

    private final EmailService emailService;
    private final MailPipeline mailPipeline;

    @Value("${app.base-url:https://batbern.ch}")
    private String appBaseUrl;

    /**
     * Sends the confirmation link of a pending subscription to its address. Best effort: a failure is
     * logged, the subscription stays pending, and subscribing again sends the same link. Asynchronous,
     * template and render included, so a subscribe that mails answers as fast as one that does not
     * (no address enumeration by timing).
     */
    @Async
    public void send(NewsletterSubscriber subscriber) {
        String recipient = subscriber.getEmail();
        String token = subscriber.getConfirmationToken();
        if (token == null) {
            log.warn("No confirmation token for newsletter subscriber {}; no confirmation mail",
                    LoggingUtils.maskEmail(recipient));
            return;
        }
        String locale = "en".equals(subscriber.getLanguage()) ? "en" : "de";
        try {
            MailContent content = mailPipeline.template(TEMPLATE_KEY, locale);
            if (content.bodyHtml().isBlank()) {
                log.warn("No {} template for locale '{}'; no confirmation mail to {}", TEMPLATE_KEY, locale,
                        LoggingUtils.maskEmail(recipient));
                return;
            }
            Map<String, String> variables = mailPipeline.variables().system()
                    .put(MailVariable.CONFIRMATION_URL, confirmationLink(token))
                    .asMap();
            RenderedMail mail = mailPipeline.renderNotice(content, variables);
            String subject = mail.subject() != null ? mail.subject()
                    : "en".equals(locale) ? "Please confirm your BATbern newsletter subscription"
                            : "Bitte bestätigen Sie Ihr BATbern-Newsletter-Abonnement";
            MailContext context = MailContext.individual(null, MailContext.Audience.SYSTEM, TEMPLATE_KEY)
                    .withSecrets(token);
            emailService.sendHtmlEmail(context, recipient, List.of(), subject, mail.html(), null);
            log.info("Newsletter confirmation mail sent to {} ({})", LoggingUtils.maskEmail(recipient), locale);
        } catch (Exception e) {
            log.error("Failed to send the newsletter confirmation mail to {}: {}",
                    LoggingUtils.maskEmail(recipient), e.getMessage(), e);
        }
    }

    /** The public confirmation page with the token (#1288). */
    String confirmationLink(String token) {
        return appBaseUrl + "/newsletter/confirm?token=" + token;
    }
}
