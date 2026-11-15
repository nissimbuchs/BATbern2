package ch.batbern.events.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The startup guard of {@link EventReadService}: each {@code include=} value has at most one owner. */
@DisplayName("EventReadService include ownership")
class EventReadServiceIncludeOwnershipTest {

    private static EventResponseContributor claiming(String... includes) {
        return () -> Set.of(includes);
    }

    @Test
    @DisplayName("should_mapEachInclude_when_contributorsAreDisjoint")
    void should_mapEachInclude_when_contributorsAreDisjoint() {
        EventResponseContributor topics = claiming("topics");
        EventResponseContributor sessions = claiming("sessions", "speakers");

        assertThat(EventReadService.byInclude(List.of(topics, sessions, claiming())))
                .containsOnlyKeys("topics", "sessions", "speakers")
                .containsEntry("topics", topics)
                .containsEntry("speakers", sessions);
    }

    @Test
    @DisplayName("should_failStart_when_twoContributorsClaimTheSameInclude")
    void should_failStart_when_twoContributorsClaimTheSameInclude() {
        assertThatThrownBy(() -> EventReadService.byInclude(List.of(claiming("topics"), claiming("metrics", "topics"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("include=topics");
    }

    @Test
    @DisplayName("should_failStart_when_contributorClaimsVenue")
    void should_failStart_when_contributorClaimsVenue() {
        assertThatThrownBy(() -> EventReadService.byInclude(List.of(claiming("venue"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("include=venue")
                .hasMessageContaining("event core");
    }
}
