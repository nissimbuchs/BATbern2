package ch.batbern.events.mailcenter.compose;

import ch.batbern.shared.mail.MailContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** #1222: a composer request's locale follows the one mail language rule. */
class ComposeRequestLocaleTest {

    @ParameterizedTest(name = "''{0}'' -> {1}")
    @CsvSource(value = {"en, en", "fr, en", "de, de", "gsw-BE, de", "NULL, de"}, nullValues = "NULL")
    void should_normalizeLocale(String locale, String expected) {
        ComposeRequest request = new ComposeRequest(MailContext.Audience.NEWSLETTER, null, null, locale, 0, false,
                null, List.of(), null);

        assertThat(request.locale()).isEqualTo(expected);
    }
}
