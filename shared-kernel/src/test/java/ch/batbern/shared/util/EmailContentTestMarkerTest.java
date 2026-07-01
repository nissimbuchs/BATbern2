package ch.batbern.shared.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("EmailContentTestMarker")
class EmailContentTestMarkerTest {

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = {
        "BATPW-E2E 1782885868836",
        "Join us at BATPW-E2E 42",
        "batpw-e2e lowercase still matches",
        "Event code BRUNO-TEST-abc123",
        "prefix bruno-test- suffix"
    })
    @DisplayName("should_detectMarker_when_contentContainsAnyKnownToken")
    void should_detectMarker_when_contentContainsAnyKnownToken(String content) {
        assertThat(EmailContentTestMarker.containsMarker(content)).isTrue();
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = {
        "BATbern 79 — Software Architecture",
        "Willkommen zum Berner Architekten Treffen",
        "Newsletter: our next event",
        "e2e",                 // partial token, not the full marker
        "BATPW"                // prefix only, not the full hyphenated marker
    })
    @DisplayName("should_notDetectMarker_when_legitimateContent")
    void should_notDetectMarker_when_legitimateContent(String content) {
        assertThat(EmailContentTestMarker.containsMarker(content)).isFalse();
    }

    @Test
    @DisplayName("should_scanAllFragments_when_multipleProvided")
    void should_scanAllFragments_when_multipleProvided() {
        assertThat(EmailContentTestMarker.containsMarker("clean subject", null, "body with BATPW-E2E"))
                .isTrue();
    }

    @Test
    @DisplayName("should_returnFalse_when_allFragmentsNullOrClean")
    void should_returnFalse_when_allFragmentsNullOrClean() {
        assertThat(EmailContentTestMarker.containsMarker((String) null)).isFalse();
        assertThat(EmailContentTestMarker.containsMarker("clean", "also clean")).isFalse();
    }

    @Test
    @DisplayName("describe lists both canonical markers")
    void describe_listsBothMarkers() {
        assertThat(EmailContentTestMarker.describe())
                .contains(EmailContentTestMarker.PLAYWRIGHT_MARKER)
                .contains(EmailContentTestMarker.BRUNO_MARKER);
    }
}
