package ch.batbern.companyuser.domain;

import ch.batbern.companyuser.dto.generated.UpdateUserSettingsRequest;
import ch.batbern.companyuser.dto.generated.UserSettings;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("UserLanguages: profile language rules (#1222)")
class UserLanguagesTest {

    @ParameterizedTest(name = "\"{0}\" -> {1}")
    @CsvSource(nullValues = "null", value = {
        "de, de",
        "fr-CH, fr",
        "' EN ', en",
        "gsw-BE, gsw-BE",
        "gsw, gsw-BE",
        "GSW_ch, gsw-BE",
        "xx, null",
        "123, null",
        "'', null",
    })
    void should_normalizeToASupportedCode(String raw, String expected) {
        assertThat(UserLanguages.normalize(raw)).isEqualTo(expected);
    }

    @Test
    void should_returnNull_when_nothingGiven() {
        assertThat(UserLanguages.normalize(null)).isNull();
    }

    @Test
    void should_listExactlyTheUsersApiLanguages() {
        assertThat(UserLanguages.SUPPORTED).containsExactlyInAnyOrderElementsOf(
                Arrays.stream(UserSettings.LanguageEnum.values()).map(UserSettings.LanguageEnum::getValue).toList());
        assertThat(UserLanguages.SUPPORTED).containsExactlyInAnyOrderElementsOf(
                Arrays.stream(UpdateUserSettingsRequest.LanguageEnum.values())
                        .map(UpdateUserSettingsRequest.LanguageEnum::getValue).toList());
    }

    @Test
    void should_fitTheColumn_when_anySupportedCode() {
        assertThat(UserLanguages.SUPPORTED).allSatisfy(code -> assertThat(code.length()).isLessThanOrEqualTo(10));
    }

    @ParameterizedTest(name = "\"{0}\" -> {1}")
    @CsvSource(nullValues = "null", value = {
        "de, de",
        "de-CH, de",
        "gsw-BE, de",
        "en, en",
        "fr, en",
        "rm, en",
        "null, fallback",
        "'  ', fallback",
    })
    void should_pickGermanForGermanAndSwissGerman_andEnglishOtherwise(String preference, String expected) {
        assertThat(UserLanguages.mailLanguage(preference, "fallback")).isEqualTo(expected);
    }
}
