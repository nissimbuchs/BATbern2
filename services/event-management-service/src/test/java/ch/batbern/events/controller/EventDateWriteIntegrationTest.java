package ch.batbern.events.controller;

import ch.batbern.events.config.TestAwsConfig;
import ch.batbern.events.config.TestSecurityConfig;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.shared.test.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * The date fields of an event write ({@code date}, {@code registrationDeadline}, {@code publishedAt})
 * arrive unchanged: in the write's answer and in the read that follows. The capture test numbers
 * date-times by position, so it would not see a shifted value; this test pins the values.
 *
 * <p>Commits like production (no test transaction). ASCII-only, single-event fixture (17.0d exception):
 * only date handling is under test, no name is searched, sorted or rendered.
 */
@Import({TestSecurityConfig.class, TestAwsConfig.class})
@DisplayName("Event writes keep date, registrationDeadline and publishedAt")
class EventDateWriteIntegrationTest extends AbstractIntegrationTest {

    private static final String CODE = "BATbern9760";
    private static final RequestPostProcessor ORGANIZER = user("olga.organizer").roles("ORGANIZER");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private EventRepository eventRepository;
    @Autowired
    private CacheManager cacheManager;

    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach
    @AfterEach
    void cleanUp() {
        cacheManager.getCacheNames().forEach(name -> Objects.requireNonNull(cacheManager.getCache(name)).clear());
        eventRepository.deleteAll();
    }

    @Test
    @DisplayName("should_keepDates_when_eventCreatedUpdatedAndPatched")
    void should_keepDates_when_eventCreatedUpdatedAndPatched() throws Exception {
        Instant base = Instant.now().truncatedTo(ChronoUnit.SECONDS);

        // POST
        Instant date = base.plus(Duration.ofDays(60)).plusSeconds(17);
        Instant deadline = base.plus(Duration.ofDays(50)).plusSeconds(23);
        Instant published = base.minus(Duration.ofDays(2)).plusSeconds(31);
        JsonNode created = send(post("/api/v1/events"), """
                {"title":"Dates","eventNumber":9760,"date":"%s","registrationDeadline":"%s","publishedAt":"%s",
                 "venueName":"Kornhausforum","venueAddress":"Kornhausplatz 18, 3011 Bern","venueCapacity":150,
                 "organizerUsername":"olga.organizer","eventType":"EVENING"}""".formatted(date, deadline, published),
                201);
        assertDates(created, date, deadline, published);
        assertDates(read(), date, deadline, published);

        // PUT
        date = base.plus(Duration.ofDays(70)).plusSeconds(41);
        deadline = base.plus(Duration.ofDays(65)).plusSeconds(43);
        published = base.minus(Duration.ofDays(1)).plusSeconds(47);
        JsonNode updated = send(put("/api/v1/events/" + CODE), """
                {"title":"Dates","date":"%s","registrationDeadline":"%s","publishedAt":"%s",
                 "venueName":"Kornhausforum","venueAddress":"Kornhausplatz 18, 3011 Bern","venueCapacity":150,
                 "organizerUsername":"olga.organizer","currentAttendeeCount":0,"eventType":"EVENING"}"""
                .formatted(date, deadline, published), 200);
        assertDates(updated, date, deadline, published);
        assertDates(read(), date, deadline, published);

        // PATCH
        date = base.plus(Duration.ofDays(80)).plusSeconds(53);
        deadline = base.plus(Duration.ofDays(75)).plusSeconds(59);
        published = base.minus(Duration.ofHours(5)).plusSeconds(7);
        JsonNode patched = send(patch("/api/v1/events/" + CODE), """
                {"date":"%s","registrationDeadline":"%s","publishedAt":"%s"}"""
                .formatted(date, deadline, published), 200);
        assertDates(patched, date, deadline, published);
        assertDates(read(), date, deadline, published);
    }

    private JsonNode send(MockHttpServletRequestBuilder request, String body, int expectedStatus) throws Exception {
        var response = mockMvc.perform(request.with(ORGANIZER).contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn().getResponse();
        assertThat(response.getStatus()).as(response.getContentAsString()).isEqualTo(expectedStatus);
        return json.readTree(response.getContentAsString());
    }

    private JsonNode read() throws Exception {
        return json.readTree(mockMvc.perform(get("/api/v1/events/" + CODE).with(ORGANIZER))
                .andReturn().getResponse().getContentAsString());
    }

    private static void assertDates(JsonNode event, Instant date, Instant deadline, Instant published) {
        assertThat(OffsetDateTime.parse(event.get("date").asText()).toInstant()).isEqualTo(date);
        assertThat(OffsetDateTime.parse(event.get("registrationDeadline").asText()).toInstant()).isEqualTo(deadline);
        assertThat(OffsetDateTime.parse(event.get("publishedAt").asText()).toInstant()).isEqualTo(published);
    }
}
