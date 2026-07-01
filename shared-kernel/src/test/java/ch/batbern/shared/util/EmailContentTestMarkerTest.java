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
        "Task template BATPW-E2E-TPL-99",                  // subsumed by BATPW-E2E
        "Event code BRUNO-TEST-abc123",
        "prefix bruno-test- suffix",
        "session bruno-test-session-42",                   // subsumed by BRUNO-TEST-
        "topic bruno-test-topic-42",                       // subsumed by BRUNO-TEST-
        "invite to bruno-test-portal-42@e2e.batbern.invalid", // subsumed by BRUNO-TEST-
        "Sponsored by BRUNOTESTCO1782885868836",           // company marker
        "Dear bruno.test.7,",                              // username marker
        "greeting for BRUNO.TEST uppercased"
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
        "BATPW",               // prefix only, not the full hyphenated marker
        "brtest",              // deliberately NOT content-matched (short/risky) — recipient-domain guard covers it
        "Dear Bruno,",         // bare firstName is too generic to match
        "the test passed"      // bare 'test' word must never match
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
