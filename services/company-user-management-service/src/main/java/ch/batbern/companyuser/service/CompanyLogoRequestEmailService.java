package ch.batbern.companyuser.service;

import ch.batbern.shared.service.EmailService;
import ch.batbern.shared.utils.LoggingUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * #1246: tells the requester the organizers' decision on a company logo request, at their primary
 * address. DE and EN only (email-template rule); other languages get EN. The decline reason is
 * optional; without one the mail has no reason block.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CompanyLogoRequestEmailService {

    private final EmailService emailService;

    @Value("${app.base-url:https://www.batbern.ch}")
    private String baseUrl;

    public void sendApproved(String email, String firstName, String locale, String companyName) {
        send(email, "company-logo-approved", locale, vars(firstName, companyName, null),
                "Ihr Logo für " + companyName + " ist übernommen",
                "Your logo for " + companyName + " is now on BATbern");
    }

    public void sendDeclined(String email, String firstName, String locale, String companyName, String reason) {
        send(email, "company-logo-declined", locale, vars(firstName, companyName, reason),
                "Ihr Logo-Antrag für " + companyName, "Your logo request for " + companyName);
    }

    private Map<String, String> vars(String firstName, String companyName, String reason) {
        Map<String, String> vars = new HashMap<>();
        vars.put("baseUrl", baseUrl != null ? baseUrl : "https://www.batbern.ch");
        vars.put("firstName", HtmlUtils.htmlEscape(firstName == null ? "" : firstName));
        vars.put("companyName", HtmlUtils.htmlEscape(companyName == null ? "" : companyName));
        vars.put("reason", HtmlUtils.htmlEscape(reason == null ? "" : reason.trim()));
        return vars;
    }

    private void send(String email, String key, String locale, Map<String, String> vars, String subjectDe,
            String subjectEn) {
        String resolved = "de".equals(locale) ? "de" : "en";
        String html = load(key, resolved);
        if (html.isBlank()) {
            throw new IllegalStateException(key + " template missing for " + resolved);
        }
        emailService.sendHtmlEmail(email, "de".equals(resolved) ? subjectDe : subjectEn,
                emailService.replaceVariables(html, vars));
        log.info("COMPANY_LOGO_REQUEST_MAIL template={} email={} locale={}", key, LoggingUtils.maskEmail(email),
                resolved);
    }

    private static String load(String key, String locale) {
        try {
            return new ClassPathResource("email-templates/" + key + "-" + locale + ".html")
                    .getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }
}
