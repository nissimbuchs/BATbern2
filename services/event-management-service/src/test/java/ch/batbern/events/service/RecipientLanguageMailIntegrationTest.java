package ch.batbern.events.service;

import ch.batbern.events.config.CumsContractHarness;
import ch.batbern.events.config.TestAwsConfig;
import ch.batbern.events.config.TestSecurityConfig;
import ch.batbern.events.core.dto.generated.EventType;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Registration;
import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.shared.test.mail.SesRecorder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * #1119 (A3), #1222: a template mail goes out in the recipient's language from their CUMS
 * preference, German for German, English for everyone else. Driven through the deregistration-link
 * mail; what SES receives is asserted, not a mocked {@code EmailService}. CUMS cannot store
 * {@code gsw-BE} today (the spec's language enum, #1222 item 5); Swiss German is covered by
 * {@code MailPipelineLocaleIntegrationTest} and {@code QnaNotificationServiceIntegrationTest}.
 */
@Import({TestSecurityConfig.class, TestAwsConfig.class})
@DisplayName("Template mail in the recipient's language")
class RecipientLanguageMailIntegrationTest extends AbstractIntegrationTest {

    private static final String USERNAME = "lang.recipient";
    private static final String EMAIL = "success+lang-recipient@simulator.amazonses.com";

    @Autowired
    private DeregistrationEmailService deregistrationEmailService;
    @Autowired
    private CumsContractHarness cums;
    @Autowired
    private SesRecorder sesRecorder;

    @BeforeEach
    void setUp() {
        sesRecorder.reset();
    }

    @AfterEach
    void tearDown() {
        sesRecorder.awaitAsyncSends();
        cums.reset();
    }

    @ParameterizedTest(name = "preference ''{0}'' -> subject starts ''{1}''")
    @CsvSource({
        "fr, Cancel Your Registration",
        "en, Cancel Your Registration",
        "it, Cancel Your Registration",
        "de, Abmeldung von"
    })
    void should_mailInRecipientLanguage_when_preferenceSet(String language, String subjectStart) {
        cums.userWithLanguage(USERNAME, language);
        Instant date = Instant.now().plus(30, ChronoUnit.DAYS);
        Event event = Event.builder().eventCode("BATbern912").title("BATbern Test Event #912").eventNumber(912)
                .date(date).eventType(EventType.EVENING).venueName("Test Venue").venueAddress("Bern").build();
        Registration registration = Registration.builder()
                .registrationCode("BATbern912-reg-LANG")
                .attendeeUsername(USERNAME)
                .attendeeFirstName("Lea")
                .attendeeEmail(EMAIL)
                .build();

        deregistrationEmailService.sendDeregistrationLinkEmail(registration, event,
                "https://www.batbern.ch/deregister?token=x");
        sesRecorder.awaitAsyncSends();

        assertThat(sesRecorder.mailsTo(EMAIL)).singleElement()
                .satisfies(mail -> assertThat(mail.subject()).startsWith(subjectStart));
    }
}
