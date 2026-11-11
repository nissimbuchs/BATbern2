package ch.batbern.events.controller;

import ch.batbern.events.config.CumsContractHarness;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

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
 *
 * <p>Every test commits; the events of this class (BATbern970 to 977) and their registrations are
 * removed before and after each test.
 */
class AttendeeDashboardIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private RegistrationRepository registrationRepository;

    /** CUMS behind the real UserApiClient (Story 17.0b2). */
    @Autowired
    private CumsContractHarness cums;

    @Autowired
    private JdbcTemplate jdbc;

    private static final String ATTENDEE = "jane.attendee";
    private static final String OTHER = "bob.other";

    @AfterEach
    void tearDown() {
        cums.reset();
        removeOwnEvents();
    }

    @BeforeEach
    void setUp() {
        cums.reset();
        removeOwnEvents();
    }

    private static final String OWN_EVENTS = "event_code IN ('BATbern970', 'BATbern971', 'BATbern972', "
            + "'BATbern973', 'BATbern974', 'BATbern975', 'BATbern976', 'BATbern977')";

    private void removeOwnEvents() {
        jdbc.update("DELETE FROM registrations WHERE event_id IN (SELECT id FROM events WHERE " + OWN_EVENTS + ")");
        jdbc.update("DELETE FROM events WHERE " + OWN_EVENTS);
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
    @DisplayName("AC7: anonymous caller is rejected with 401")
    void should_reject_anonymous() throws Exception {
        mockMvc.perform(get("/api/v1/attendee-portal/dashboard"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = ATTENDEE, roles = {"ATTENDEE"})
    @DisplayName("Greets with the CUMS profile first name, not the login username")
    void should_greetWithProfileFirstName() throws Exception {
        cums.user(ATTENDEE, "Jane", "Müller", "jane.attendee@example.com");
        Event e = saveEvent("BATbern974", Instant.now().plus(20, ChronoUnit.DAYS),
                EventWorkflowState.AGENDA_PUBLISHED);
        saveRegistration(ATTENDEE, e, "confirmed");

        mockMvc.perform(get("/api/v1/attendee-portal/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attendeeName", is("Jane")));
    }

    // ==================== #1183: all of a person's verified addresses ====================

    private void addresses(String verified, String unverified) {
        cums.user(ATTENDEE, """
                {"id": "%s", "email": "jane@private.example.com", "firstName": "Jane", "lastName": "Müller",
                 "roles": ["ATTENDEE"], "createdAt": "2025-01-15T10:00:00Z", "updatedAt": "2025-01-15T10:00:00Z",
                 "additionalEmails": [
                   {"id": "6b0e2a4c-1d3f-4e5a-8b7c-9d0e1f2a3b5a", "email": "%s",
                    "createdAt": "2025-01-15T10:00:00Z", "verifiedAt": "2025-01-16T10:00:00Z"},
                   {"id": "6b0e2a4c-1d3f-4e5a-8b7c-9d0e1f2a3b5b", "email": "%s",
                    "createdAt": "2025-01-15T10:00:00Z", "verifiedAt": null}]}"""
                .formatted(ATTENDEE, verified, unverified));
    }

    @Test
    @WithMockUser(username = ATTENDEE, roles = {"ATTENDEE"})
    @DisplayName("#1183: history on an imported profile under a verified address shows; unverified does not")
    void should_includeRegistrations_underVerifiedAddressesOnly() throws Exception {
        addresses("jane@work.example.com", "not-mine@example.org");
        Event attended = saveEvent("BATbern975", Instant.now().minus(400, ChronoUnit.DAYS), EventWorkflowState.ARCHIVED);
        Event foreign = saveEvent("BATbern976", Instant.now().minus(300, ChronoUnit.DAYS), EventWorkflowState.ARCHIVED);
        Registration imported = saveRegistration("jane.imported", attended, "attended");
        imported.setAttendeeEmail("JANE@work.example.com");
        registrationRepository.save(imported);
        Registration notMine = saveRegistration("someone.else", foreign, "attended");
        notMine.setAttendeeEmail("not-mine@example.org");
        registrationRepository.save(notMine);

        mockMvc.perform(get("/api/v1/attendee-portal/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pastEvents.length()", is(1)))
                .andExpect(jsonPath("$.pastEvents[0].eventCode", is("BATbern975")));
    }

    @Test
    @WithMockUser(username = ATTENDEE, roles = {"ATTENDEE"})
    @DisplayName("#1183: one card per event when both profiles registered for it")
    void should_showEventOnce_when_registeredOnTwoProfiles() throws Exception {
        addresses("jane@work.example.com", "x@example.org");
        Event e = saveEvent("BATbern977", Instant.now().plus(10, ChronoUnit.DAYS), EventWorkflowState.AGENDA_PUBLISHED);
        saveRegistration(ATTENDEE, e, "confirmed");
        Registration second = saveRegistration("jane.guest", e, "waitlist");
        second.setAttendeeEmail("jane@work.example.com");
        registrationRepository.save(second);

        mockMvc.perform(get("/api/v1/attendee-portal/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.upcomingEvents.length()", is(1)))
                .andExpect(jsonPath("$.upcomingEvents[0].registrationStatus", is("confirmed")));
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
        event.setEventType(ch.batbern.events.core.dto.generated.EventType.EVENING);
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
