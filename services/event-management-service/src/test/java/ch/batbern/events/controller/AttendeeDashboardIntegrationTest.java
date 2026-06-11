package ch.batbern.events.controller;

import ch.batbern.events.client.UserApiClient;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Registration;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.RegistrationRepository;
import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.shared.types.EventWorkflowState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for the attendee dashboard — {@code GET /api/v1/attendee-portal/dashboard}
 * (Story 7.6). PostgreSQL via Testcontainers.
 *
 * <p>Covers AC1/AC7: upcoming/past split, cancelled excluded, attribution by the authenticated
 * username, anonymous rejection, empty state.
 */
@Transactional
class AttendeeDashboardIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private RegistrationRepository registrationRepository;

    @MockitoBean
    private UserApiClient userApiClient;

    private static final String ATTENDEE = "jane.attendee";
    private static final String OTHER = "bob.other";

    @BeforeEach
    void setUp() {
        registrationRepository.deleteAll();
        eventRepository.deleteAll();
    }

    @Test
    @WithMockUser(username = ATTENDEE, roles = {"ATTENDEE"})
    @DisplayName("AC1: splits the attendee's events into upcoming + past, excludes cancelled")
    void should_splitUpcomingPast_and_excludeCancelled() throws Exception {
        Event past = saveEvent("BATbern970", Instant.now().minus(30, ChronoUnit.DAYS),
                EventWorkflowState.ARCHIVED);
        Event upcoming = saveEvent("BATbern971", Instant.now().plus(30, ChronoUnit.DAYS),
                EventWorkflowState.AGENDA_PUBLISHED);
        Event cancelledEvt = saveEvent("BATbern972", Instant.now().minus(10, ChronoUnit.DAYS),
                EventWorkflowState.ARCHIVED);

        saveRegistration(ATTENDEE, past, "attended");
        saveRegistration(ATTENDEE, upcoming, "confirmed");
        saveRegistration(ATTENDEE, cancelledEvt, "cancelled");

        mockMvc.perform(get("/api/v1/attendee-portal/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.upcomingEvents.length()", is(1)))
                .andExpect(jsonPath("$.upcomingEvents[0].eventCode", is("BATbern971")))
                .andExpect(jsonPath("$.upcomingEvents[0].registrationStatus", is("confirmed")))
                .andExpect(jsonPath("$.pastEvents.length()", is(1)))
                .andExpect(jsonPath("$.pastEvents[0].eventCode", is("BATbern970")))
                .andExpect(jsonPath("$.pastEvents[0].eventLocation", is("Test Venue")));
    }

    @Test
    @WithMockUser(username = ATTENDEE, roles = {"ATTENDEE"})
    @DisplayName("AC7: only the authenticated user's registrations are returned")
    void should_returnOnlyOwnRegistrations() throws Exception {
        Event event = saveEvent("BATbern973", Instant.now().minus(5, ChronoUnit.DAYS),
                EventWorkflowState.ARCHIVED);
        saveRegistration(OTHER, event, "attended");

        mockMvc.perform(get("/api/v1/attendee-portal/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.upcomingEvents.length()", is(0)))
                .andExpect(jsonPath("$.pastEvents.length()", is(0)));
    }

    @Test
    @WithMockUser(username = ATTENDEE, roles = {"ATTENDEE"})
    @DisplayName("Empty state: no registrations → empty lists, 200")
    void should_returnEmpty_when_noRegistrations() throws Exception {
        mockMvc.perform(get("/api/v1/attendee-portal/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.upcomingEvents.length()", is(0)))
                .andExpect(jsonPath("$.pastEvents.length()", is(0)));
    }

    @Test
    @DisplayName("AC7: anonymous caller is rejected (401 at gateway / 403 in isolation)")
    void should_reject_anonymous() throws Exception {
        mockMvc.perform(get("/api/v1/attendee-portal/dashboard"))
                .andExpect(status().isForbidden());
    }

    // ==================== Helpers ====================

    private Event saveEvent(String eventCode, Instant date, EventWorkflowState state) {
        Event event = new Event();
        event.setEventCode(eventCode);
        event.setEventNumber(Integer.parseInt(eventCode.replaceAll("\\D", "")));
        event.setTitle("Attendee Dashboard Test " + eventCode);
        event.setDate(date);
        event.setRegistrationDeadline(date.minus(7, ChronoUnit.DAYS));
        event.setVenueName("Test Venue");
        event.setVenueAddress("Test Address");
        event.setVenueCapacity(150);
        event.setOrganizerUsername("org.user");
        event.setEventType(ch.batbern.events.dto.generated.EventType.EVENING);
        event.setWorkflowState(state);
        event.setCreatedAt(Instant.now());
        event.setUpdatedAt(Instant.now());
        event.setCreatedBy("org.user");
        event.setUpdatedBy("org.user");
        return eventRepository.save(event);
    }

    private Registration saveRegistration(String username, Event event, String status) {
        return registrationRepository.save(Registration.builder()
                .registrationCode(event.getEventCode() + "-reg-" + username.hashCode())
                .eventId(event.getId())
                .attendeeUsername(username)
                .attendeeFirstName("Jane")
                .attendeeLastName("Attendee")
                .status(status)
                .registrationDate(Instant.now().minus(40, ChronoUnit.DAYS))
                .build());
    }
}
