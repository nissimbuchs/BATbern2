package ch.batbern.events.controller;

import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.shared.types.EventWorkflowState;
import ch.batbern.events.client.UserApiClient;
import ch.batbern.events.config.TestAwsConfig;
import ch.batbern.events.config.TestSecurityConfig;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Registration;
import ch.batbern.events.dto.generated.EventType;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.RegistrationRepository;
import ch.batbern.events.service.DeregistrationEmailService;
import ch.batbern.events.service.WaitlistPromotionEmailService;
import ch.batbern.events.service.RegistrationEmailService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for DeregistrationController.
 * Story 10.12 (AC12): Written FIRST (RED phase) — TDD mandate.
 *
 * Tests all three public deregistration endpoints against real PostgreSQL via Testcontainers.
 * All endpoints must be accessible without authentication (token is the auth mechanism).
 *
 * Coverage:
 * - T5.2.1: GET /deregister/verify?token=valid → 200 with registration info
 * - T5.2.2: GET /deregister/verify?token=unknown → 404
 * - T5.2.3: POST /deregister with valid token → 200; registration status = cancelled
 * - T5.2.4: POST /deregister with valid token (2nd call) → 409 already_cancelled
 * - T5.2.5: POST /deregister with unknown token → 404
 * - T5.2.6: POST /deregister/by-email with any input → always 200
 * - T5.2.7: All endpoints accessible without auth (no JWT required)
 * - T5.2.8: Deregister when waitlisted → cancels, waitlist promotion triggered
 */
@Transactional
@Import({TestSecurityConfig.class, TestAwsConfig.class})
@DisplayName("DeregistrationController Integration Tests")
class DeregistrationControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private RegistrationRepository registrationRepository;

    @MockitoBean
    private UserApiClient userApiClient;

    @MockitoBean
    private DeregistrationEmailService deregistrationEmailService;

    @MockitoBean
    private WaitlistPromotionEmailService waitlistPromotionEmailService;

    @MockitoBean
    private RegistrationEmailService registrationEmailService;

    private static final String EVENT_CODE = "BATbern910";
    private static final UUID VALID_TOKEN = UUID.fromString("550e8400-e29b-41d4-a716-446655440001");
    private static final UUID CANCELLED_TOKEN = UUID.fromString("550e8400-e29b-41d4-a716-446655440002");
    private static final UUID UNKNOWN_TOKEN = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
    private static final Instant EVENT_DATE = LocalDate.of(2026, 9, 15)
            .atStartOfDay(ZoneId.of("UTC")).toInstant();

    private Event testEvent;

    @BeforeEach
    void setUp() {
        registrationRepository.deleteAll();
        eventRepository.deleteAll();

        testEvent = eventRepository.save(Event.builder()
                .eventCode(EVENT_CODE)
                .title("BATbern Test Event #910")
                .eventNumber(910)
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

        // Active registration with valid token
        registrationRepository.save(Registration.builder()
                .registrationCode(EVENT_CODE + "-reg-ACTIVE1")
                .eventId(testEvent.getId())
                .deregistrationToken(VALID_TOKEN)
                .attendeeUsername("alice.test")
                .attendeeFirstName("Alice")
                .attendeeLastName("Test")
                .attendeeEmail("alice@example.com")
                .status("registered")
                .registrationDate(Instant.now())
                .build());

        // Already-cancelled registration
        registrationRepository.save(Registration.builder()
                .registrationCode(EVENT_CODE + "-reg-CANCEL1")
                .eventId(testEvent.getId())
                .deregistrationToken(CANCELLED_TOKEN)
                .attendeeUsername("bob.test")
                .attendeeFirstName("Bob")
                .attendeeLastName("Test")
                .attendeeEmail("bob@example.com")
                .status("cancelled")
                .registrationDate(Instant.now())
                .build());
    }

    // ── T5.2.1: GET /deregister/verify → 200 ─────────────────────────────────

    @Test
    @DisplayName("GET /deregister/verify with valid token → 200 with registration details")
    void verifyToken_validToken_returns200WithDetails() throws Exception {
        mockMvc.perform(get("/api/v1/registrations/deregister/verify")
                        .param("token", VALID_TOKEN.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registrationCode", is(EVENT_CODE + "-reg-ACTIVE1")))
                .andExpect(jsonPath("$.eventCode", is(EVENT_CODE)))
                .andExpect(jsonPath("$.eventTitle", notNullValue()))
                .andExpect(jsonPath("$.attendeeFirstName", is("Alice")));
    }

    // ── T5.2.2: GET /deregister/verify → 404 ────────────────────────────────

    @Test
    @DisplayName("GET /deregister/verify with unknown token → 404")
    void verifyToken_unknownToken_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/registrations/deregister/verify")
                        .param("token", UNKNOWN_TOKEN.toString()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /deregister/verify with already-cancelled token → 404 (AC3: same as unknown token)")
    void verifyToken_alreadyCancelledToken_returns404() throws Exception {
        // AC3: verify must return 404 for BOTH "not found" AND "already cancelled"
        // (POST /deregister returns 409 for cancelled — different spec)
        mockMvc.perform(get("/api/v1/registrations/deregister/verify")
                        .param("token", CANCELLED_TOKEN.toString()))
                .andExpect(status().isNotFound());
    }

    // ── T5.2.3: POST /deregister → 200; status = cancelled ───────────────────

    @Test
    @DisplayName("POST /deregister with valid token → 200; registration cancelled in DB")
    void deregisterByToken_validToken_returns200AndCancelsRegistration() throws Exception {
        Map<String, String> body = Map.of("token", VALID_TOKEN.toString());

        mockMvc.perform(post("/api/v1/registrations/deregister")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", notNullValue()));

        // Verify status changed in DB
        Registration updated = registrationRepository.findByDeregistrationToken(VALID_TOKEN).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo("cancelled");
    }

    // ── T5.2.4: POST /deregister (2nd call) → 409 ───────────────────────────

    @Test
    @DisplayName("POST /deregister with already-cancelled token → 409 Conflict")
    void deregisterByToken_alreadyCancelled_returns409() throws Exception {
        Map<String, String> body = Map.of("token", CANCELLED_TOKEN.toString());

        mockMvc.perform(post("/api/v1/registrations/deregister")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isConflict());
    }

    // ── T5.2.5: POST /deregister unknown token → 404 ────────────────────────

    @Test
    @DisplayName("POST /deregister with unknown token → 404 Not Found")
    void deregisterByToken_unknownToken_returns404() throws Exception {
        Map<String, String> body = Map.of("token", UNKNOWN_TOKEN.toString());

        mockMvc.perform(post("/api/v1/registrations/deregister")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isNotFound());
    }

    // ── T5.2.6: POST /deregister/by-email → always 200 ──────────────────────

    @Test
    @DisplayName("POST /deregister/by-email with any input → always 200 (anti-enumeration)")
    void deregisterByEmail_anyInput_alwaysReturns200() throws Exception {
        Map<String, String> body = Map.of("email", "ghost@example.com", "eventCode", EVENT_CODE);

        mockMvc.perform(post("/api/v1/registrations/deregister/by-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /deregister/by-email with real registered email → 200 (anti-enumeration)")
    void deregisterByEmail_realEmail_alwaysReturns200() throws Exception {
        Map<String, String> body = Map.of("email", "alice@example.com", "eventCode", EVENT_CODE);

        mockMvc.perform(post("/api/v1/registrations/deregister/by-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());
    }

    // ── T5.2.7: No auth required ─────────────────────────────────────────────

    @Test
    @DisplayName("All deregistration endpoints accessible without authentication token")
    void allEndpoints_noAuth_accessible() throws Exception {
        // GET /verify — no Authorization header
        mockMvc.perform(get("/api/v1/registrations/deregister/verify")
                        .param("token", VALID_TOKEN.toString()))
                .andExpect(status().isOk()); // NOT 401

        // POST /deregister/by-email — no Authorization header
        mockMvc.perform(post("/api/v1/registrations/deregister/by-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"test@example.com\",\"eventCode\":\"" + EVENT_CODE + "\"}"))
                .andExpect(status().isOk()); // NOT 401
    }

    // ── T5.2.8: Waitlist promotion on deregistration ─────────────────────────

    @Test
    @DisplayName("Deregister active registration when waitlist exists → waitlisted reg promoted")
    void deregisterByToken_whenWaitlistExists_waitlistRegistrationPromoted() throws Exception {
        // Add a waitlisted registration
        UUID waitlistToken = UUID.randomUUID();
        registrationRepository.save(Registration.builder()
                .registrationCode(EVENT_CODE + "-reg-WAIT01")
                .eventId(testEvent.getId())
                .deregistrationToken(waitlistToken)
                .attendeeUsername("carol.test")
                .attendeeFirstName("Carol")
                .attendeeLastName("Test")
                .attendeeEmail("carol@example.com")
                .status("waitlist")
                .waitlistPosition(1)
                .registrationDate(Instant.now())
                .build());

        // Set event capacity to 1 so the active registration fills it
        testEvent.setRegistrationCapacity(1);
        eventRepository.save(testEvent);

        Map<String, String> body = Map.of("token", VALID_TOKEN.toString());

        mockMvc.perform(post("/api/v1/registrations/deregister")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());

        // Active registration should be cancelled
        Registration cancelled = registrationRepository.findByDeregistrationToken(VALID_TOKEN).orElseThrow();
        assertThat(cancelled.getStatus()).isEqualTo("cancelled");

        // Waitlisted registration should be promoted to "registered"
        Registration promoted = registrationRepository.findByDeregistrationToken(waitlistToken).orElseThrow();
        assertThat(promoted.getStatus()).isEqualTo("registered");
        assertThat(promoted.getWaitlistPosition()).isNull();
    }
}
