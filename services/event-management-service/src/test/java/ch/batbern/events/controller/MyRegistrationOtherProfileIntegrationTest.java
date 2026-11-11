package ch.batbern.events.controller;

import ch.batbern.events.config.CumsContractHarness;
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
 * #1183: {@code GET /events/{eventCode}/my-registration} finds a registration made on another
 * profile of the same person, under one of the caller's VERIFIED additional addresses only.
 *
 * <p>Moved out of the {@code @Transactional} RegistrationStatusIntegrationTest (Story 4.10d, touch
 * rule): CUMS answers through {@link CumsContractHarness} (the real {@code UserApiClient}, the user
 * response checked against the spec, so every additional address carries its {@code id}). Every
 * test commits; the event and its registrations are removed before and after each test.
 */
class MyRegistrationOtherProfileIntegrationTest extends AbstractIntegrationTest {

    private static final String EVENT_CODE = "BATbern9961";
    private static final String USERNAME = "alice.test";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private EventRepository eventRepository;
    @Autowired
    private RegistrationRepository registrationRepository;
    @Autowired
    private CumsContractHarness cums;
    @Autowired
    private JdbcTemplate jdbc;

    private Event event;

    @BeforeEach
    void setUp() {
        cums.reset();
        removeEvent();
        Instant date = Instant.now().plus(40, ChronoUnit.DAYS);
        event = eventRepository.save(Event.builder()
                .eventCode(EVENT_CODE)
                .title("BATbern Test Event")
                .eventNumber(9961)
                .date(date)
                .registrationDeadline(date.minus(1, ChronoUnit.DAYS))
                .venueName("Test Venue")
                .venueAddress("Bern")
                .venueCapacity(200)
                .organizerUsername("organizer")
                .currentAttendeeCount(0)
                .eventType(EventType.EVENING)
                .workflowState(EventWorkflowState.AGENDA_PUBLISHED)
                .build());
    }

    @AfterEach
    void tearDown() {
        cums.reset();
        removeEvent();
    }

    private void removeEvent() {
        jdbc.update("DELETE FROM registrations WHERE event_id IN (SELECT id FROM events WHERE event_code = ?)",
                EVENT_CODE);
        jdbc.update("DELETE FROM events WHERE event_code = ?", EVENT_CODE);
    }

    /** The caller's CUMS profile: a primary, one verified and one unverified additional address. */
    private void userWithAdditionalEmails(String verified, String unverified) {
        cums.user(USERNAME, """
                {"email": "private@example.com",
                 "additionalEmails": [
                   {"id": "6b0e2a4c-1d3f-4e5a-8b7c-9d0e1f2a3b6a", "email": "%s",
                    "createdAt": "2025-01-15T10:00:00Z", "verifiedAt": "2025-01-16T10:00:00Z"},
                   {"id": "6b0e2a4c-1d3f-4e5a-8b7c-9d0e1f2a3b6b", "email": "%s",
                    "createdAt": "2025-01-15T10:00:00Z", "verifiedAt": null}]}"""
                .formatted(verified, unverified));
    }

    private Registration registration(String username, String email) {
        return registrationRepository.save(Registration.builder()
                .registrationCode(EVENT_CODE + "-reg-" + username.replace(".", "-"))
                .eventId(event.getId())
                .attendeeUsername(username)
                .attendeeEmail(email)
                .status("confirmed")
                .registrationDate(Instant.now())
                .build());
    }

    @Test
    @DisplayName("#1183: finds the registration made as a guest under a verified additional address")
    @WithMockUser(username = USERNAME)
    void should_findRegistration_underVerifiedAdditionalAddress() throws Exception {
        userWithAdditionalEmails("Work@Example.com", "other@example.com");
        Registration guest = registration("guest.profile", "work@example.com");

        mockMvc.perform(get("/api/v1/events/{eventCode}/my-registration", EVENT_CODE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registered", is(true)))
                .andExpect(jsonPath("$.status", is("CONFIRMED")))
                .andExpect(jsonPath("$.registrationCode", is(guest.getRegistrationCode())));
    }

    @Test
    @DisplayName("#1183: an unverified additional address does not reveal anyone's registration")
    @WithMockUser(username = USERNAME)
    void should_notFindRegistration_underUnverifiedAddress() throws Exception {
        userWithAdditionalEmails("work@example.com", "other@example.com");
        registration("someone.else", "other@example.com");

        mockMvc.perform(get("/api/v1/events/{eventCode}/my-registration", EVENT_CODE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registered", is(false)));
    }
}
