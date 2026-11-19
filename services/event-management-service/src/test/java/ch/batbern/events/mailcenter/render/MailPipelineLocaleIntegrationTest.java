package ch.batbern.events.mailcenter.render;

import ch.batbern.events.config.TestAwsConfig;
import ch.batbern.events.config.TestSecurityConfig;
import ch.batbern.events.core.dto.generated.EventType;
import ch.batbern.events.domain.Event;
import ch.batbern.shared.test.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The mail language rule in {@link MailPipeline} and its event variables against the real template
 * store (#1222): German for {@code de*}, {@code gsw*} and no preference, English for every other
 * language. Before the fix every locale but {@code en} got the German template and German dates.
 */
@Import({TestSecurityConfig.class, TestAwsConfig.class})
@DisplayName("MailPipeline picks German or English by the one locale rule")
class MailPipelineLocaleIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MailPipeline mailPipeline;

    @ParameterizedTest(name = "''{0}'' -> {1}")
    @CsvSource(value = {"fr, en", "it, en", "ja, en", "en, en", "EN, en", "de, de", "de-CH, de", "gsw-BE, de",
        "NULL, de", "'', de", "'  ', de"}, nullValues = "NULL")
    void should_pickTemplateLanguage_when_localeGiven(String locale, String expected) {
        MailContent template = mailPipeline.template("deregistration-link", locale);
        MailContent stored = mailPipeline.storedTemplate("deregistration-link", locale);

        assertThat(template.locale()).isEqualTo(expected);
        assertThat(template.bodyHtml()).isNotBlank();
        assertThat(stored.locale()).isEqualTo(expected);
        assertThat(stored.bodyHtml()).isNotBlank();
    }

    @ParameterizedTest(name = "''{0}'' -> German={1}")
    @CsvSource(value = {"fr, false", "ja, false", "en, false", "de, true", "gsw-BE, true", "NULL, true"},
        nullValues = "NULL")
    void should_fillEventVariablesInTemplateLanguage_when_localeGiven(String locale, boolean german) {
        Event event = Event.builder().eventCode("BATbern913").title("Locale Event").eventNumber(913)
                .date(Instant.parse("2027-03-05T17:00:00Z")).eventType(EventType.EVENING).build();

        Map<String, String> vars = mailPipeline.variables().event(event, locale).asMap();

        assertThat(vars.get("eventDate")).isEqualTo(german ? "Freitag, 5. März 2027" : "Friday, 5 March 2027");
        assertThat(vars.get("conferenceLanguage")).isEqualTo(german ? "Deutsch / Englisch" : "German / English");
    }
}
