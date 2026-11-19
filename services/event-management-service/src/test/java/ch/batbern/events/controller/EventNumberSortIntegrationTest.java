package ch.batbern.events.controller;

import ch.batbern.events.config.TestAwsConfig;
import ch.batbern.events.config.TestSecurityConfig;
import ch.batbern.events.core.dto.generated.EventType;
import ch.batbern.events.domain.Event;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.shared.types.EventWorkflowState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The query the event form asks for the next event number (#1223 item 7): one row of
 * {@code GET /events}, sorted by {@code -eventNumber}, archived events included. Pins that the sort
 * is by number, not by date: the highest number here is an archived event with the oldest date.
 *
 * <p>Commits like production (no test transaction), on the shared container: it creates, reads
 * (filtered to its own numbers) and deletes only its own events. ASCII-only fixture (17.0d
 * exception): only numbers and dates are under test, no name is searched or rendered.
 */
@Import({TestSecurityConfig.class, TestAwsConfig.class})
@DisplayName("Events sort by event number for the next-number query")
class EventNumberSortIntegrationTest extends AbstractIntegrationTest {

    private static final RequestPostProcessor ORGANIZER = user("olga.organizer").roles("ORGANIZER");
    private static final List<Integer> NUMBERS = List.of(9761, 9762, 9763);
    private static final String OWN_ROWS = "{\"eventNumber\":{\"$in\":[9761,9762,9763]}}";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private EventRepository eventRepository;
    @Autowired
    private CacheManager cacheManager;

    @BeforeEach
    @AfterEach
    void cleanUp() {
        cacheManager.getCacheNames().forEach(name -> Objects.requireNonNull(cacheManager.getCache(name)).clear());
        NUMBERS.forEach(n -> eventRepository.findByEventCode("BATbern" + n).ifPresent(eventRepository::delete));
    }

    @Test
    @DisplayName("should_returnHighestNumber_when_sortedByEventNumberDescendingWithArchived")
    void should_returnHighestNumber_when_sortedByEventNumberDescendingWithArchived() throws Exception {
        save(9761, "2027-03-05T17:00:00Z", EventWorkflowState.CREATED);
        save(9763, "2010-05-20T17:00:00Z", EventWorkflowState.ARCHIVED);
        save(9762, "2026-11-06T17:00:00Z", EventWorkflowState.EVENT_COMPLETED);

        mockMvc.perform(get("/api/v1/events")
                        .param("filter", OWN_ROWS)
                        .param("sort", "-eventNumber")
                        .param("limit", "1")
                        .param("includeArchived", "true")
                        .with(ORGANIZER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].eventNumber").value(9763))
                .andExpect(jsonPath("$.pagination.totalItems").value(3));
    }

    private void save(int number, String date, EventWorkflowState state) {
        Instant when = Instant.parse(date);
        eventRepository.save(Event.builder()
                .eventCode("BATbern" + number)
                .eventNumber(number)
                .title("Event " + number)
                .date(when)
                .registrationDeadline(when.minusSeconds(86400L * 7))
                .venueName("Test Venue")
                .venueAddress("Test Address 123, Bern")
                .venueCapacity(100)
                .organizerUsername("olga.organizer")
                .currentAttendeeCount(0)
                .description("Event " + number)
                .eventType(EventType.EVENING)
                .workflowState(state)
                .build());
    }
}
