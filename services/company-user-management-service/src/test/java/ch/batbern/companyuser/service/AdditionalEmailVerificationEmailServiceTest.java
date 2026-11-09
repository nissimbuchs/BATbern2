package ch.batbern.companyuser.service;

import ch.batbern.shared.service.EmailService;
import ch.batbern.shared.test.mail.SesRecorder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link AdditionalEmailVerificationEmailService}'s locale →
 * template/subject selection. Any German variant ({@code de}, {@code de-CH}, …)
 * must use the German template + subject; everything else (including {@code fr},
 * {@code gsw-BE}, and {@code null}) falls back to English per the email-template
 * DE+EN-only localization rule.
 */
@DisplayName("AdditionalEmailVerificationEmailService — locale resolution")
class AdditionalEmailVerificationEmailServiceTest {

    private static final String EN_SUBJECT = "Confirm your BATbern email address";

    private static final String BOX = "success+box@simulator.amazonses.com";

    private SesRecorder sesRecorder;
    private AdditionalEmailVerificationEmailService service;

    @BeforeEach
    void setUp() {
        // The real EmailService against the recording SES client (17.0b3): assert what SES got.
        sesRecorder = new SesRecorder();
        EmailService emailService = new EmailService();
        ReflectionTestUtils.setField(emailService, "sesClient", sesRecorder.client());
        ReflectionTestUtils.setField(emailService, "fromEmail", "noreply@batbern.ch");
        ReflectionTestUtils.setField(emailService, "fromName", "BATbern");
        ReflectionTestUtils.setField(emailService, "replyToEmail", "replies@batbern.ch");
        service = new AdditionalEmailVerificationEmailService(emailService);
        ReflectionTestUtils.setField(service, "baseUrl", "https://www.batbern.ch");
    }

    @ParameterizedTest(name = "locale=\"{0}\" → subject=\"{1}\"")
    @CsvSource(delimiter = '|', value = {
        "de     | Bestätigen Sie Ihre BATbern E-Mail-Adresse",
        "de-CH  | Bestätigen Sie Ihre BATbern E-Mail-Adresse",
        "de-DE  | Bestätigen Sie Ihre BATbern E-Mail-Adresse",
        "DE     | Bestätigen Sie Ihre BATbern E-Mail-Adresse",
        "fr     | Confirm your BATbern email address",
        "gsw-BE | Confirm your BATbern email address",
        "en     | Confirm your BATbern email address",
    })
    @DisplayName("should_selectSubjectByLocalePrefix")
    void should_selectSubjectByLocalePrefix(String locale, String expectedSubject) {
        service.sendVerificationEmail(BOX, "tok123", locale);

        assertThat(sesRecorder.single().subject()).isEqualTo(expectedSubject);
    }

    @Test
    @DisplayName("should_fallBackToEnglish_when_localeIsNull")
    void should_fallBackToEnglish_when_localeIsNull() {
        service.sendVerificationEmail(BOX, "tok123", null);

        assertThat(sesRecorder.single().subject()).isEqualTo(EN_SUBJECT);
        assertThat(sesRecorder.single().html()).contains("/verify-email?token=tok123");
    }
}
