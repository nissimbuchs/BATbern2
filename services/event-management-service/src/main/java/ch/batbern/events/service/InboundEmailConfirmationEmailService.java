package ch.batbern.events.service;

import ch.batbern.shared.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Sends confirmation emails after inbound email actions (Story 10.17 — AC7).
 *
 * <ul>
 *   <li>After unsubscribe → sends {@code unsubscribe-confirmation-{locale}.html}</li>
 *   <li>After deregistration cancel → sends {@code cancel-confirmation-{locale}.html}</li>
 * </ul>
 *
 * Template loading is best-effort: if template is missing, logs a warning and skips send.
 * Templates are seeded into DB by {@link EmailTemplateSeedService} on startup.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InboundEmailConfirmationEmailService {

    private final EmailTemplateService emailTemplateService;
    private final EmailService emailService;

    /**
     * Send unsubscribe confirmation email to the given address.
     * Best-effort: failures are logged, not thrown.
     *
     * @param email recipient address
     */
    public void sendUnsubscribeConfirmation(String email) {
        try {
            String locale = "en"; // default; could be resolved from subscriber record in future
            String html = loadHtml("unsubscribe-confirmation", locale,
                    "email-templates/unsubscribe-confirmation-" + locale + ".html");
            if (html.isBlank()) {
                log.warn("unsubscribe-confirmation template not found — skipping confirmation email to: {}***",
                        email.substring(0, Math.min(5, email.length())));
                return;
            }
            String subject = locale.equals("de")
                    ? "Newsletter Abmeldung bestätigt"
                    : "Newsletter Unsubscribe Confirmed";
            emailService.sendHtmlEmail(email, subject, html);
        } catch (Exception e) {
            log.warn("Failed to send unsubscribe confirmation to: {}*** — {}",
                    email.substring(0, Math.min(5, email.length())), e.getMessage());
        }
    }

    /**
     * Send deregistration confirmation email to the given address.
     * Best-effort: failures are logged, not thrown.
     *
     * @param email     recipient address
     * @param eventCode event code (e.g. "BATbern42")
     */
    public void sendCancelConfirmation(String email, String eventCode) {
        try {
            String locale = "en"; // default
            String html = loadHtml("cancel-confirmation", locale,
                    "email-templates/cancel-confirmation-" + locale + ".html");
            if (html.isBlank()) {
                log.warn("cancel-confirmation template not found — skipping confirmation email to: {}***",
                        email.substring(0, Math.min(5, email.length())));
                return;
            }
            html = emailService.replaceVariables(html, Map.of("eventCode", eventCode));
            String subject = locale.equals("de")
                    ? "Abmeldung für " + eventCode + " bestätigt"
                    : "Deregistration for " + eventCode + " Confirmed";
            emailService.sendHtmlEmail(email, subject, html);
        } catch (Exception e) {
            log.warn("Failed to send cancel confirmation to: {}*** — {}",
                    email.substring(0, Math.min(5, email.length())), e.getMessage());
        }
    }

    /**
     * Send attendance acceptance confirmation email to the given address.
     * Best-effort: failures are logged, not thrown.
     *
     * @param email     recipient address
     * @param eventCode event code (e.g. "BATbern42")
     */
    public void sendAcceptConfirmation(String email, String eventCode) {
        try {
            String locale = "en"; // default
            String html = loadHtml("accept-confirmation", locale,
                    "email-templates/accept-confirmation-" + locale + ".html");
            if (html.isBlank()) {
                log.warn("accept-confirmation template not found — skipping confirmation email to: {}***",
                        email.substring(0, Math.min(5, email.length())));
                return;
            }
            html = emailService.replaceVariables(html, Map.of("eventCode", eventCode));
            String subject = locale.equals("de")
                    ? "Teilnahme an " + eventCode + " bestätigt"
                    : "Attendance at " + eventCode + " Confirmed";
            emailService.sendHtmlEmail(email, subject, html);
        } catch (Exception e) {
            log.warn("Failed to send accept confirmation to: {}*** — {}",
                    email.substring(0, Math.min(5, email.length())), e.getMessage());
        }
    }

    private String loadHtml(String templateKey, String locale, String classpathPath) {
        return emailTemplateService.findByKeyAndLocale(templateKey, locale)
                .map(t -> t.getHtmlBody())
                .orElseGet(() -> {
                    try {
                        ClassPathResource resource = new ClassPathResource(classpathPath);
                        return resource.getContentAsString(StandardCharsets.UTF_8);
                    } catch (IOException e) {
                        log.warn("Email template classpath resource not found: {}", classpathPath);
                        return "";
                    }
                });
    }
}
