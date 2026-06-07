package ch.batbern.companyuser.service;

import ch.batbern.shared.service.EmailService;
import ch.batbern.shared.utils.LoggingUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Additional-email verification (v2): renders and sends the signed
 * verification link to a newly-added (or resent) additional email address.
 *
 * <p>Templates live on the classpath at
 * {@code email-templates/additional-email-verification-{locale}.html}. Per the
 * project's email-template localization rule, only {@code de} + {@code en}
 * templates exist; any other requested locale falls back to {@code en}.
 *
 * <p>The verification link is {@code {{baseUrl}}/verify-email?token={{token}}}.
 * Rendering uses {@link EmailService#replaceVariables} ({@code {{var}}} syntax).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdditionalEmailVerificationEmailService {

    private final EmailService emailService;

    @Value("${app.base-url:https://www.batbern.ch}")
    private String baseUrl;

    /**
     * Render and send the verification email. This method itself does NOT swallow
     * exceptions on the send path — callers that must tolerate send failures (the
     * add flow) wrap the call in their own try/catch. The resend endpoint lets
     * exceptions propagate so the caller can react.
     *
     * @param email   recipient additional-email address
     * @param token   signed verification token
     * @param locale  user language preference (de/en); anything else falls back to en
     */
    public void sendVerificationEmail(String email, String token, String locale) {
        String resolvedLocale = resolveLocale(locale);
        String html = loadHtml(resolvedLocale);
        if (html.isBlank()) {
            log.warn("additional-email-verification template not found — skipping email to: {}",
                    LoggingUtils.maskEmail(email));
            return;
        }
        Map<String, String> vars = new java.util.HashMap<>();
        vars.put("baseUrl", baseUrl != null ? baseUrl : "https://www.batbern.ch");
        vars.put("token", token != null ? token : "");
        html = emailService.replaceVariables(html, vars);
        String subject = "de".equals(resolvedLocale)
                ? "Bestätigen Sie Ihre BATbern E-Mail-Adresse"
                : "Confirm your BATbern email address";
        emailService.sendHtmlEmail(email, subject, html);
        log.info("ADDITIONAL_EMAIL_VERIFICATION_SENT email={} locale={}",
                LoggingUtils.maskEmail(email), resolvedLocale);
    }

    /**
     * Email templates exist only in de + en. Any German variant (de, de-DE,
     * de-CH, …) resolves to de; any other locale (including null and gsw-BE)
     * resolves to en.
     */
    private String resolveLocale(String requestedLocale) {
        return requestedLocale != null
                && requestedLocale.toLowerCase(java.util.Locale.ROOT).startsWith("de")
                ? "de" : "en";
    }

    private String loadHtml(String locale) {
        String classpathPath = "email-templates/additional-email-verification-" + locale + ".html";
        try {
            ClassPathResource resource = new ClassPathResource(classpathPath);
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warn("Email template classpath resource not found: {}", classpathPath);
            return "";
        }
    }
}
