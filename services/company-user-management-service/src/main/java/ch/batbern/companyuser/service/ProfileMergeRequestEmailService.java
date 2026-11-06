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
 * #1237: tells the requester the organizers' decision, at their current primary address. DE and EN
 * only (email-template rule); other languages get EN.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProfileMergeRequestEmailService {

    private final EmailService emailService;

    @Value("${app.base-url:https://www.batbern.ch}")
    private String baseUrl;

    public void sendApproved(String email, String firstName, String locale) {
        send(email, "profile-merge-approved", locale, firstName, null,
                "Ihr früheres BATbern-Profil ist zusammengeführt", "Your earlier BATbern profile is combined");
    }

    public void sendDeclined(String email, String firstName, String locale, String reason) {
        send(email, "profile-merge-declined", locale, firstName, reason,
                "Ihre Anfrage zum früheren BATbern-Profil", "Your request about your earlier BATbern profile");
    }

    private void send(String email, String key, String locale, String firstName, String reason, String subjectDe,
            String subjectEn) {
        String resolved = "de".equals(locale) ? "de" : "en";
        String html = load(key, resolved);
        if (html.isBlank()) {
            throw new IllegalStateException(key + " template missing for " + resolved);
        }
        Map<String, String> vars = new HashMap<>();
        vars.put("baseUrl", baseUrl != null ? baseUrl : "https://www.batbern.ch");
        vars.put("firstName", HtmlUtils.htmlEscape(firstName == null ? "" : firstName));
        vars.put("reason", HtmlUtils.htmlEscape(reason == null ? "" : reason));
        emailService.sendHtmlEmail(email, "de".equals(resolved) ? subjectDe : subjectEn,
                emailService.replaceVariables(html, vars));
        log.info("PROFILE_MERGE_REQUEST_MAIL template={} email={} locale={}", key, LoggingUtils.maskEmail(email),
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
