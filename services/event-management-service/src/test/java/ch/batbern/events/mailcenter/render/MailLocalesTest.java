package ch.batbern.events.mailcenter.render;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/** The one mail language rule (#1222): German for {@code de*}, {@code gsw*} and none, else English. */
class MailLocalesTest {

    @ParameterizedTest(name = "''{0}'' -> {1}")
    @CsvSource(value = {"fr, en", "it, en", "ja, en", "rm, en", "en, en", "en-GB, en", "de, de", "DE, de",
        "de-CH, de", "de_CH, de", "gsw-BE, de", "gsw-be, de", "NULL, de", "'', de", "'  ', de"},
            nullValues = "NULL")
    void should_mapPreferenceToTemplateLanguage(String preference, String expected) {
        assertThat(MailLocales.languageOf(preference)).isEqualTo(expected);
        assertThat(MailLocales.fromPreference(preference).getLanguage()).isEqualTo(expected);
    }
}
