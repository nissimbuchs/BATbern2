package ch.batbern.events.controller;

import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.shared.types.EventWorkflowState;
import ch.batbern.events.client.UserApiClient;
import ch.batbern.events.config.TestAwsConfig;
import ch.batbern.events.config.TestSecurityConfig;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Registration;
import ch.batbern.events.core.dto.generated.EventType;
import ch.batbern.events.dto.generated.users.GetOrCreateUserResponse;
import ch.batbern.events.dto.generated.users.UserResponse;
import ch.batbern.events.repository.AnalyticsRepository;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.RegistrationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for GET /api/v1/events/{eventCode}/my-registration
 * Story 10.10 — Registration Status Indicator for Logged-in Users (AC1, AC9)
 *
 * TDD — RED phase: written BEFORE implementation (T2, T3, T4).
 * Uses Testcontainers PostgreSQL for production parity.
 *
 * Test coverage:
 * - T5.2: 200 CONFIRMED status, registered=true
 * - T5.3: 200 REGISTERED status, registered=true
 * - T5.4: 200 WAITLIST status, registered=true
 * - T5.5: 200 CANCELLED status, registered=true
 * - T5.6: 200 registered=false when user has no registration (no 404)
 * - T5.7: 401 for unauthenticated (the production chain runs in tests, 17.0b3)
 * - T4.6.2: POST registrations allows re-registration when existing status is CANCELLED
 */
@Transactional
@Import({TestSecurityConfig.class, TestAwsConfig.class})
class RegistrationStatusIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private RegistrationRepository registrationRepository;

    @Autowired
    private AnalyticsRepository analyticsRepository;

    @MockitoBean
    private UserApiClient userApiClient;

    private static final String EVENT_CODE = "BATbern999";
    private static final String USERNAME = "alice.test";
    private static final String OTHER_USERNAME = "bob.other";
    private static final Instant EVENT_DATE = LocalDate.of(2027, 6, 15)
            .atStartOfDay(ZoneId.of("UTC")).toInstant();

    private Event testEvent;

    @BeforeEach
    void setUp() {
        registrationRepository.deleteAll();
        eventRepository.deleteAll();

        // Mock UserApiClient for re-registration tests (T4.6.2) — returns deterministic user
        GetOrCreateUserResponse mockUserResponse = new GetOrCreateUserResponse()
                .username("alice.test")
                .created(false)
                .user(new UserResponse()
                        .id("alice.test")
                        .firstName("Alice")
                        .lastName("Test")
                        .email("alice.test@example.com")
                        .companyId("TestCo"));
        lenient().when(userApiClient.getOrCreateUser(any())).thenReturn(mockUserResponse);

        testEvent = eventRepository.save(Event.builder()
                .eventCode(EVENT_CODE)
                .title("BATbern Test Event")
                .eventNumber(999)
                .date(EVENT_DATE)
                .registrationDeadline(EVENT_DATE.minusSeconds(86400))
                .venueName("Test Venue")
                .venueAddress("Bern")
                .venueCapacity(200)
                .organizerUsername("organizer")
                .currentAttendeeCount(0)
                .eventType(EventType.EVENING)
                .workflowState(EventWorkflowState.AGENDA_PUBLISHED)
                .build());
    }

    // ── T5.2: 200 CONFIRMED ────────────────────────────────────────────────────

    @Test
    @DisplayName("should return 200 with CONFIRMED status when user has confirmed registration")
    @WithMockUser(username = USERNAME)
    void should_return200_with_confirmedStatus() throws Exception {
        saveRegistration(testEvent, USERNAME, "confirmed");

        mockMvc.perform(get("/api/v1/events/{eventCode}/my-registration", EVENT_CODE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registered", is(true)))
                .andExpect(jsonPath("$.eventCode", is(EVENT_CODE)))
                .andExpect(jsonPath("$.status", is("CONFIRMED")))
                .andExpect(jsonPath("$.registrationCode", notNullValue()))
                .andExpect(jsonPath("$.registrationDate", notNullValue()));
    }

    // ── T5.3: 200 REGISTERED ──────────────────────────────────────────────────

    @Test
    @DisplayName("should return 200 with REGISTERED status when user is pending registration")
    @WithMockUser(username = USERNAME)
    void should_return200_with_registeredStatus() throws Exception {
        saveRegistration(testEvent, USERNAME, "registered");

        mockMvc.perform(get("/api/v1/events/{eventCode}/my-registration", EVENT_CODE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registered", is(true)))
                .andExpect(jsonPath("$.eventCode", is(EVENT_CODE)))
                .andExpect(jsonPath("$.status", is("REGISTERED")));
    }

    // ── T5.4: 200 WAITLIST ────────────────────────────────────────────────────

    @Test
    @DisplayName("should return 200 with WAITLIST status when user is on the waitlist")
    @WithMockUser(username = USERNAME)
    void should_return200_with_waitlistStatus() throws Exception {
        // V73+ uses 'waitlist' (lowercase); V74 migration removed the legacy 'waitlisted' value
        saveRegistration(testEvent, USERNAME, "waitlist");

        mockMvc.perform(get("/api/v1/events/{eventCode}/my-registration", EVENT_CODE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registered", is(true)))
                .andExpect(jsonPath("$.eventCode", is(EVENT_CODE)))
                .andExpect(jsonPath("$.status", is("WAITLIST")));
    }

    // ── T5.4b: 200 ATTENDED ───────────────────────────────────────────────────

    @Test
    @DisplayName("should return 200 with ATTENDED status when user attended the event (regression: was 400 before fix)")
    @WithMockUser(username = USERNAME)
    void should_return200_with_attendedStatus() throws Exception {
        saveRegistration(testEvent, USERNAME, "attended");

        mockMvc.perform(get("/api/v1/events/{eventCode}/my-registration", EVENT_CODE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registered", is(true)))
                .andExpect(jsonPath("$.eventCode", is(EVENT_CODE)))
                .andExpect(jsonPath("$.status", is("ATTENDED")));
    }

    @Test
    @DisplayName("should return 200 with NO_SHOW status when the registrant did not come")
    @WithMockUser(username = USERNAME)
    void should_return200_with_noShowStatus() throws Exception {
        saveRegistration(testEvent, USERNAME, "no_show");
        registrationRepository.flush();

        mockMvc.perform(get("/api/v1/events/{eventCode}/my-registration", EVENT_CODE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registered", is(true)))
                .andExpect(jsonPath("$.status", is("NO_SHOW")));
    }

    @Test
    @DisplayName("should not count a no-show as attendee when one who came is counted")
    void should_notCountNoShow_when_countingAttendees() {
        saveRegistration(testEvent, USERNAME, "attended");
        saveRegistration(testEvent, OTHER_USERNAME, "no_show");
        registrationRepository.flush();

        assertThat(analyticsRepository.countTotalAttendees()).isEqualTo(1);
    }

    // ── T5.5: 200 CANCELLED ───────────────────────────────────────────────────

    @Test
    @DisplayName("should return 200 with CANCELLED status when user has cancelled registration")
    @WithMockUser(username = USERNAME)
    void should_return200_with_cancelledStatus() throws Exception {
        saveRegistration(testEvent, USERNAME, "cancelled");

        mockMvc.perform(get("/api/v1/events/{eventCode}/my-registration", EVENT_CODE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registered", is(true)))
                .andExpect(jsonPath("$.eventCode", is(EVENT_CODE)))
                .andExpect(jsonPath("$.status", is("CANCELLED")));
    }

    // ── T5.6: 200 registered=false when no registration ──────────────────────

    @Test
    @DisplayName("should return 200 with registered=false when authenticated user has no registration for event")
    @WithMockUser(username = USERNAME)
    void should_return200_with_registeredFalse_when_noRegistrationForUser() throws Exception {
        // Save a registration for a DIFFERENT user — alice should still get registered=false
        saveRegistration(testEvent, OTHER_USERNAME, "confirmed");

        mockMvc.perform(get("/api/v1/events/{eventCode}/my-registration", EVENT_CODE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registered", is(false)));
    }

    @Test
    @DisplayName("should return 200 with registered=false when event code does not exist")
    @WithMockUser(username = USERNAME)
    void should_return200_with_registeredFalse_when_eventNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/events/{eventCode}/my-registration", "BATbern000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registered", is(false)));
    }

    // ── T5.7: unauthenticated ─────────────────────────────────────────────────

    @Test
    @DisplayName("should return 401 for unauthenticated request")
    void should_return401_when_unauthenticated() throws Exception {
        // The production chain answers 401 before the controller (17.0b3).
        mockMvc.perform(get("/api/v1/events/{eventCode}/my-registration", EVENT_CODE))
                .andExpect(status().isUnauthorized());
    }

    // ── T4.6.2: POST /registrations succeeds (201) when existing status is CANCELLED ──

    @Test
    @DisplayName("should return 201 Created when POST /registrations and existing registration is CANCELLED (T4.6.2)")
    void should_return201_when_reRegisteringAfterCancellation() throws Exception {
        // Arrange: existing CANCELLED registration for alice.test
        saveRegistration(testEvent, USERNAME, "cancelled");
        assertThat(registrationRepository.count()).isEqualTo(1);

        String requestJson = """
                {
                    "firstName": "Alice",
                    "lastName": "Test",
                    "email": "alice.test@example.com",
                    "termsAccepted": true
                }
                """;

        // Act: POST /registrations — backend (T4.6) deletes cancelled record and creates new one
        mockMvc.perform(post("/api/v1/events/{eventCode}/registrations", EVENT_CODE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Registration submitted successfully")))
                .andExpect(jsonPath("$.email").value("alice.test@example.com"));

        // Assert: exactly one registration in DB (cancelled deleted, new created)
        assertThat(registrationRepository.count()).isEqualTo(1);
        Registration newReg = registrationRepository.findAll().get(0);
        assertThat(newReg.getStatus()).isEqualTo("registered"); // new registration starts as 'registered'
        assertThat(newReg.getAttendeeUsername()).isEqualTo(USERNAME);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    // #1183 (a registration on another profile of the same person):
    // MyRegistrationOtherProfileIntegrationTest, committing, CUMS through CumsContractHarness (4.10d).

    // ── #1238 E2: the hero's "Abmelden" (DELETE my-registration) closes on the event day ──

    private void eventStartingOn(LocalDate day) {
        ZoneId bern = ZoneId.of("Europe/Zurich");
        Instant start = day.atTime(18, 0).atZone(bern).toInstant();
        testEvent.setDate(start);
        testEvent.setRegistrationDeadline(start.minusSeconds(3 * 86400));
        eventRepository.save(testEvent);
    }

    @Test
    @DisplayName("#1238 E2: unregistering on the event day → 409 DEREGISTRATION_CLOSED, registration kept")
    @WithMockUser(username = USERNAME)
    void should_refuseUnregister_when_eventDay() throws Exception {
        eventStartingOn(LocalDate.now(ZoneId.of("Europe/Zurich")));
        Registration mine = saveRegistration(testEvent, USERNAME, "confirmed");

        mockMvc.perform(delete("/api/v1/events/{eventCode}/my-registration", EVENT_CODE))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.details.code", is("DEREGISTRATION_CLOSED")));

        assertThat(registrationRepository.findById(mine.getId()).orElseThrow().getStatus()).isEqualTo("confirmed");
    }

    @Test
    @DisplayName("#1238 E2: unregistering the day before the event works")
    @WithMockUser(username = USERNAME)
    void should_unregister_when_dayBeforeEvent() throws Exception {
        eventStartingOn(LocalDate.now(ZoneId.of("Europe/Zurich")).plusDays(1));
        Registration mine = saveRegistration(testEvent, USERNAME, "confirmed");

        mockMvc.perform(delete("/api/v1/events/{eventCode}/my-registration", EVENT_CODE))
                .andExpect(status().isNoContent());

        assertThat(registrationRepository.findById(mine.getId()).orElseThrow().getStatus()).isEqualTo("cancelled");
    }

    private Registration saveRegistration(Event event, String username, String status) {
        return registrationRepository.save(Registration.builder()
                .registrationCode(event.getEventCode() + "-reg-" + username.replace(".", "-"))
                .eventId(event.getId())
                .attendeeUsername(username)
                .status(status)
                .registrationDate(Instant.now())
                .build());
    }
}
