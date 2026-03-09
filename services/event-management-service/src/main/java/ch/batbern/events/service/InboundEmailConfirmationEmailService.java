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
     * @param email  recipient address
     * @param locale BCP 47 locale code detected from reply keyword (e.g. "de", "fr", "en")
     */
    public void sendUnsubscribeConfirmation(String email, String locale) {
        try {
            String resolvedLocale = resolveLocale(locale, "unsubscribe-confirmation");
            String html = loadHtml("unsubscribe-confirmation", resolvedLocale,
                    "email-templates/unsubscribe-confirmation-" + resolvedLocale + ".html");
            if (html.isBlank()) {
                log.warn("unsubscribe-confirmation template not found — skipping confirmation email to: {}***",
                        email.substring(0, Math.min(5, email.length())));
                return;
            }
            String subject = resolvedLocale.equals("de")
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
     * @param locale    BCP 47 locale code detected from reply keyword
     */
    public void sendCancelConfirmation(String email, String eventCode, String locale) {
        try {
            String resolvedLocale = resolveLocale(locale, "cancel-confirmation");
            String html = loadHtml("cancel-confirmation", resolvedLocale,
                    "email-templates/cancel-confirmation-" + resolvedLocale + ".html");
            if (html.isBlank()) {
                log.warn("cancel-confirmation template not found — skipping confirmation email to: {}***",
                        email.substring(0, Math.min(5, email.length())));
                return;
            }
            html = emailService.replaceVariables(html, Map.of("eventCode", eventCode));
            String subject = resolvedLocale.equals("de")
                    ? "Abmeldung für " + eventCode + " bestätigt"
                    : "Deregistration for " + eventCode + " Confirmed";
            emailService.sendHtmlEmail(email, subject, html);
        } catch (Exception e) {
            log.warn("Failed to send cancel confirmation to: {}*** — {}",
                    email.substring(0, Math.min(5, email.length())), e.getMessage());
        }
    }

    /**
     * Resolves the locale to use for the given template key.
     * Falls back to "en" if the requested locale has no template on the classpath.
     */
    private String resolveLocale(String requestedLocale, String templateKey) {
        if ("en".equals(requestedLocale)) {
            return "en";
        }
        // Check if a classpath template exists for the requested locale; fall back to "en" if not
        ClassPathResource resource = new ClassPathResource(
                "email-templates/" + templateKey + "-" + requestedLocale + ".html");
        return resource.exists() ? requestedLocale : "en";
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
