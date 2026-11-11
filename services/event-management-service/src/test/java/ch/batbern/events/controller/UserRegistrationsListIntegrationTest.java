package ch.batbern.events.controller;

import ch.batbern.events.config.CumsContractHarness;
import ch.batbern.events.config.TestAwsConfig;
import ch.batbern.events.config.TestSecurityConfig;
import ch.batbern.events.core.dto.generated.EventType;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Registration;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.RegistrationRepository;
import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.shared.types.EventWorkflowState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * One attendee's registrations across events, {@code GET /registrations?filter=…} (Story 4.10b; was
 * {@code GET /events/registrations} with the username in the {@code X-Attendee-Username} header).
 * Organizer only, paginated (ADR-013 §6), newest event first, the filter required.
 *
 * <p>Data is committed and removed in {@code @AfterEach} (registrations cascade from the event), no
 * class-level {@code @Transactional} (Test fidelity rules). CUMS answers through the contract
 * harness (17.0b2).
 */
@Import({TestSecurityConfig.class, TestAwsConfig.class})
@DisplayName("Attendee registrations across events (GET /registrations)")
class UserRegistrationsListIntegrationTest extends AbstractIntegrationTest {

    private static final String HONG_AN = "hongan.nguyen";
    private static final String OTHER = "matthias.keller";
    private static final List<String> CODES = List.of("BATbern99801", "BATbern99802", "BATbern99803");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private RegistrationRepository registrationRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CumsContractHarness cums;

    @BeforeEach
    void setUp() {
        deleteTestEvents();
        cums.reset();
        cums.user(HONG_AN, CumsContractHarness.userJson(HONG_AN, "Hông Ân", "Nguyễn",
                "Hongan.Nguyen@example.com", "mobiliar"));
        cums.user(OTHER, CumsContractHarness.userJson(OTHER, "Matthias", "Keller",
                "matthias.keller@example.com", "mobiliar"));

        Instant now = Instant.now();
        Event oldest = saveEvent(CODES.get(0), 99801, now.minus(400, ChronoUnit.DAYS));
        Event middle = saveEvent(CODES.get(1), 99802, now.minus(30, ChronoUnit.DAYS));
        Event newest = saveEvent(CODES.get(2), 99803, now.plus(30, ChronoUnit.DAYS));
        saveRegistration(oldest, HONG_AN, "attended", "A1A1A1");
        saveRegistration(newest, HONG_AN, "registered", "B2B2B2");
        saveRegistration(middle, HONG_AN, "cancelled", "C3C3C3");
        saveRegistration(middle, OTHER, "attended", "D4D4D4");
    }

    @AfterEach
    void tearDown() {
        deleteTestEvents();
        cums.reset();
    }

    private void deleteTestEvents() {
        CODES.forEach(code -> jdbcTemplate.update("DELETE FROM events WHERE event_code = ?", code));
    }

    private static RequestPostProcessor organizer() {
        return user("anna.organizer").roles("ORGANIZER");
    }

    private static String filterFor(String username) {
        return "{\"attendeeUsername\":\"" + username + "\"}";
    }

    @Test
    @DisplayName("should_listOnlyThatAttendeesRegistrationsNewestEventFirst_when_organizerFilters")
    void should_listOnlyThatAttendeesRegistrationsNewestEventFirst_when_organizerFilters() throws Exception {
        mockMvc.perform(get("/api/v1/registrations").param("filter", filterFor(HONG_AN)).with(organizer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].eventCode").value(contains(CODES.get(2), CODES.get(1), CODES.get(0))))
                .andExpect(jsonPath("$.data[*].attendeeUsername").value(everyItem(is(HONG_AN))))
                .andExpect(jsonPath("$.data[0].eventTitle").value("Verlauf " + CODES.get(2)))
                .andExpect(jsonPath("$.data[0].attendeeFirstName").value("Hông Ân"))
                .andExpect(jsonPath("$.pagination.totalItems").value(3))
                .andExpect(jsonPath("$.pagination.page").value(1))
                .andExpect(jsonPath("$.pagination.hasNext").value(false));

        // Positive control for the scoping: the other attendee has a registration at the middle event.
        mockMvc.perform(get("/api/v1/registrations").param("filter", filterFor(OTHER)).with(organizer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].eventCode").value(contains(CODES.get(1))));
    }

    @Test
    @DisplayName("should_page_when_limitIsSmallerThanTheHistory")
    void should_page_when_limitIsSmallerThanTheHistory() throws Exception {
        mockMvc.perform(get("/api/v1/registrations").param("filter", filterFor(HONG_AN))
                        .param("page", "2").param("limit", "2").with(organizer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].eventCode").value(contains(CODES.get(0))))
                .andExpect(jsonPath("$.pagination.totalItems").value(3))
                .andExpect(jsonPath("$.pagination.totalPages").value(2))
                .andExpect(jsonPath("$.pagination.hasPrev").value(true))
                .andExpect(jsonPath("$.pagination.hasNext").value(false));
    }

    @Test
    @DisplayName("should_return400_when_filterIsMissingOrNamesAnotherField")
    void should_return400_when_filterIsMissingOrNamesAnotherField() throws Exception {
        mockMvc.perform(get("/api/v1/registrations").with(organizer()))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/registrations").param("filter", "{\"status\":\"attended\"}").with(organizer()))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/registrations")
                        .param("filter", "{\"attendeeUsername\":{\"$contains\":\"hong\"}}").with(organizer()))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/registrations").param("filter", "not json").with(organizer()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("should_return403_when_attendeeListsRegistrations")
    void should_return403_when_attendeeListsRegistrations() throws Exception {
        mockMvc.perform(get("/api/v1/registrations").param("filter", filterFor(HONG_AN))
                        .with(user(HONG_AN).roles("ATTENDEE")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("should_notListRegistrations_when_oldPathIsCalled")
    void should_notListRegistrations_when_oldPathIsCalled() throws Exception {
        // Story 4.10b, no alias: "registrations" is now read as an event code (GET
        // /events/{eventCode}), which fails its pattern or finds no event.
        mockMvc.perform(get("/api/v1/events/registrations").header("X-Attendee-Username", HONG_AN)
                        .with(organizer()))
                .andExpect(status().is4xxClientError())
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    private Event saveEvent(String code, int number, Instant date) {
        return eventRepository.save(Event.builder()
                .eventCode(code)
                .title("Verlauf " + code)
                .eventNumber(number)
                .date(date)
                .registrationDeadline(date.minus(7, ChronoUnit.DAYS))
                .venueName("Kursaal Bern")
                .venueAddress("Kornhausstrasse 3, 3013 Bern")
                .venueCapacity(250)
                .organizerUsername("anna.organizer")
                .currentAttendeeCount(0)
                .eventType(EventType.EVENING)
                .workflowState(date.isBefore(Instant.now())
                        ? EventWorkflowState.ARCHIVED
                        : EventWorkflowState.CREATED)
                .build());
    }

    private void saveRegistration(Event event, String username, String status, String suffix) {
        registrationRepository.save(Registration.builder()
                .registrationCode(event.getEventCode() + "-reg-" + suffix)
                .eventId(event.getId())
                .deregistrationToken(UUID.randomUUID())
                .attendeeUsername(username)
                .attendeeFirstName("Hông Ân")
                .attendeeLastName("Nguyễn")
                .attendeeEmail(username + "@example.com")
                .status(status)
                .registrationDate(Instant.now().minus(Duration.ofDays(10)))
                .build());
    }
}
