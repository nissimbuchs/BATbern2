package ch.batbern.companyuser.service;

import ch.batbern.shared.service.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

    private EmailService emailService;
    private AdditionalEmailVerificationEmailService service;

    @BeforeEach
    void setUp() {
        emailService = mock(EmailService.class);
        // Real variable substitution so the rendered body carries the token link.
        when(emailService.replaceVariables(anyString(), any())).thenCallRealMethod();
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
        service.sendVerificationEmail("box@example.com", "tok123", locale);

        ArgumentCaptor<String> subject = ArgumentCaptor.forClass(String.class);
        verify(emailService, times(1)).sendHtmlEmail(anyString(), subject.capture(), anyString());
        assertThat(subject.getValue()).isEqualTo(expectedSubject);
    }

    @Test
    @DisplayName("should_fallBackToEnglish_when_localeIsNull")
    void should_fallBackToEnglish_when_localeIsNull() {
        service.sendVerificationEmail("box@example.com", "tok123", null);

        ArgumentCaptor<String> subject = ArgumentCaptor.forClass(String.class);
        verify(emailService, times(1)).sendHtmlEmail(anyString(), subject.capture(), anyString());
        assertThat(subject.getValue()).isEqualTo(EN_SUBJECT);
    }
}
