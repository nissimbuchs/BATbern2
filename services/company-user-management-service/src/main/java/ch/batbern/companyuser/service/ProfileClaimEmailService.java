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
 * #1183 D: the mail with the claim link, sent to the earlier profile's address. DE and EN only
 * (email-template rule); other languages get EN.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProfileClaimEmailService {

    private final EmailService emailService;

    @Value("${app.base-url:https://www.batbern.ch}")
    private String baseUrl;

    public void sendClaimEmail(String email, String token, String locale, String claimerName) {
        send(email, "profile-claim", token, locale, claimerName,
                "Frühere BATbern-Anmeldungen mit Ihrem Konto zusammenführen",
                "Combine your earlier BATbern registrations with your account");
    }

    /** #1244 step 3: the link to the address of a second account with its own login. */
    public void sendAccountMergeEmail(String email, String token, String locale, String claimerName) {
        send(email, "account-merge", token, locale, claimerName,
                "Zwei BATbern-Konten zusammenführen", "Combine two BATbern accounts");
    }

    private void send(String email, String key, String token, String locale, String claimerName, String subjectDe,
            String subjectEn) {
        String resolved = "de".equals(locale) ? "de" : "en";
        String html = load(key, resolved);
        if (html.isBlank()) {
            throw new IllegalStateException(key + " template missing for " + resolved);
        }
        Map<String, String> vars = new HashMap<>();
        vars.put("baseUrl", baseUrl != null ? baseUrl : "https://www.batbern.ch");
        vars.put("token", token);
        vars.put("claimerName", HtmlUtils.htmlEscape(claimerName == null ? "" : claimerName));
        html = emailService.replaceVariables(html, vars);
        emailService.sendHtmlEmail(email, "de".equals(resolved) ? subjectDe : subjectEn, html);
        log.info("PROFILE_CLAIM_SENT template={} email={} locale={}", key, LoggingUtils.maskEmail(email), resolved);
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
