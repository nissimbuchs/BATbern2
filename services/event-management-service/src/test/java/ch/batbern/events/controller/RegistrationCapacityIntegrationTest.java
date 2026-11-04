package ch.batbern.events.controller;

import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.events.client.UserApiClient;
import ch.batbern.events.config.TestAwsConfig;
import ch.batbern.events.config.TestSecurityConfig;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Registration;
import ch.batbern.events.core.dto.generated.EventType;
import ch.batbern.events.dto.generated.users.GetOrCreateUserResponse;
import ch.batbern.events.dto.generated.users.UserResponse;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.RegistrationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for registration capacity enforcement and waitlist management.
 * Story 10.11: Venue Capacity Enforcement & Waitlist Management (AC2, AC3, AC10)
 *
 * TDD: Written FIRST (RED phase) before service implementation.
 * Extends AbstractIntegrationTest — uses real PostgreSQL via Testcontainers.
 */
@Transactional
@Import({TestSecurityConfig.class, TestAwsConfig.class})
@DisplayName("RegistrationCapacity Integration Tests")
public class RegistrationCapacityIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private RegistrationRepository registrationRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UserApiClient userApiClient;

    private static final String EVENT_CODE = "BATbern10k";
    private static final String EVENT_CODE_UNLIMITED = "BATbern10kU";
    private static final String PATCHABLE_CODE = "BATbern9101";

    private int userCounter = 0;

    @BeforeEach
    void setUp() {
        userCounter = 0;
        registrationRepository.deleteAll();
        eventRepository.deleteAll();

        // Event with capacity = 2
        Event cappedEvent = Event.builder()
                .eventCode(EVENT_CODE)
                .title("Capacity Test Event")
                .eventNumber(9001)
                .date(Instant.now().plus(60, ChronoUnit.DAYS))
                .registrationDeadline(Instant.now().plus(55, ChronoUnit.DAYS))
                .venueName("Test Venue")
                .venueAddress("Teststrasse 1, Bern")
                .venueCapacity(200)
                .registrationCapacity(2) // Story 10.11: limit = 2
                .organizerUsername("test.organizer")
                .currentAttendeeCount(0)
                .eventType(EventType.EVENING)
                .workflowState(ch.batbern.shared.types.EventWorkflowState.CREATED)
                .build();
        eventRepository.save(cappedEvent);

        // Event with NO capacity (unlimited)
        Event unlimitedEvent = Event.builder()
                .eventCode(EVENT_CODE_UNLIMITED)
                .title("Unlimited Event")
                .eventNumber(9002)
                .date(Instant.now().plus(90, ChronoUnit.DAYS))
                .registrationDeadline(Instant.now().plus(85, ChronoUnit.DAYS))
                .venueName("Big Hall")
                .venueAddress("Grossstrasse 99, Bern")
                .venueCapacity(500)
                // registrationCapacity = null → unlimited
                .organizerUsername("test.organizer")
                .currentAttendeeCount(0)
                .eventType(EventType.EVENING)
                .workflowState(ch.batbern.shared.types.EventWorkflowState.CREATED)
                .build();
        eventRepository.save(unlimitedEvent);

        // Mock UserApiClient to return different users per call
        lenient().when(userApiClient.getOrCreateUser(any())).thenAnswer(invocation -> {
            ch.batbern.events.dto.generated.users.GetOrCreateUserRequest req =
                    invocation.getArgument(0);
            String email = req.getEmail() != null ? req.getEmail() : "user" + (++userCounter) + "@test.com";
            String username = email.replace("@test.com", "").replace(".", "-");
            UserResponse user = new UserResponse()
                    .id(username)
                    .firstName("Test")
                    .lastName("User")
                    .email(email)
                    .companyId("TestCo");
            return new GetOrCreateUserResponse()
                    .username(username)
                    .created(true)
                    .user(user);
        });

        lenient().when(userApiClient.getUserByUsername(any())).thenAnswer(invocation -> {
            String username = invocation.getArgument(0);
            return new UserResponse()
                    .id(username)
                    .firstName("Test")
                    .lastName("User")
                    .email(username + "@test.com")
                    .companyId("TestCo");
        });
    }

    // ── AC2: Capacity enforcement ──────────────────────────────────────────────

    @Test
    @DisplayName("register when count < capacity → status=registered")
    void register_whenBelowCapacity_createsRegistered() throws Exception {
        postRegistration(EVENT_CODE, "user1@test.com");
        postRegistration(EVENT_CODE, "user2@test.com"); // fills capacity

        // username derived from email: "user1@test.com" → replace("@test.com","") → "user1"
        Registration reg = registrationRepository.findByEventIdAndAttendeeUsername(
                eventByCode(EVENT_CODE).getId(), "user1").orElseThrow();
        // Note: second will be "registered" (fills the 2nd slot), first is also "registered"
        assertThat(reg.getStatus()).isEqualTo("registered");
        assertThat(reg.getWaitlistPosition()).isNull();
    }

    @Test
    @DisplayName("register when count == capacity → status=waitlist, waitlistPosition=1")
    void register_whenAtCapacity_createsWaitlist() throws Exception {
        // Fill capacity
        postRegistration(EVENT_CODE, "user1@test.com");
        postRegistration(EVENT_CODE, "user2@test.com");

        // Third registration → waitlist
        postRegistration(EVENT_CODE, "user3@test.com");

        // Flush and re-query since @Transactional defers actual DB writes
        Event event = eventByCode(EVENT_CODE);
        List<Registration> waitlisted = registrationRepository.findWaitlistByEventIdOrdered(event.getId());
        assertThat(waitlisted).hasSize(1);
        assertThat(waitlisted.get(0).getStatus()).isEqualTo("waitlist");
        assertThat(waitlisted.get(0).getWaitlistPosition()).isEqualTo(1);
    }

    @Test
    @DisplayName("second waitlist registration → waitlistPosition=2")
    void register_secondWaitlist_getsPosition2() throws Exception {
        postRegistration(EVENT_CODE, "user1@test.com");
        postRegistration(EVENT_CODE, "user2@test.com");
        postRegistration(EVENT_CODE, "user3@test.com"); // waitlist position 1
        postRegistration(EVENT_CODE, "user4@test.com"); // waitlist position 2

        Event event = eventByCode(EVENT_CODE);
        List<Registration> waitlisted = registrationRepository.findWaitlistByEventIdOrdered(event.getId());
        assertThat(waitlisted).hasSize(2);
        assertThat(waitlisted.get(0).getWaitlistPosition()).isEqualTo(1);
        assertThat(waitlisted.get(1).getWaitlistPosition()).isEqualTo(2);
    }

    @Test
    @DisplayName("register when capacity is NULL (unlimited) → status=registered always")
    void register_whenCapacityNull_alwaysRegistered() throws Exception {
        // Register 3 users on unlimited event
        postRegistration(EVENT_CODE_UNLIMITED, "u1@test.com");
        postRegistration(EVENT_CODE_UNLIMITED, "u2@test.com");
        postRegistration(EVENT_CODE_UNLIMITED, "u3@test.com");

        Event event = eventByCode(EVENT_CODE_UNLIMITED);
        List<Registration> all = registrationRepository.findByEventId(event.getId());
        assertThat(all).hasSize(3);
        assertThat(all).allMatch(r -> "registered".equals(r.getStatus()));
        assertThat(all).allMatch(r -> r.getWaitlistPosition() == null);
    }

    @Test
    @DisplayName("register when attendee already has waitlist registration → no duplicate, waitlist-confirmation email resent")
    void register_duplicateWaitlist_returnsExistingWaitlistRegistration() throws Exception {
        // Fill capacity
        postRegistration(EVENT_CODE, "user1@test.com");
        postRegistration(EVENT_CODE, "user2@test.com");

        // 3rd user registers → waitlist
        postRegistration(EVENT_CODE, "user3@test.com");

        // Same user registers again → must NOT create duplicate
        postRegistration(EVENT_CODE, "user3@test.com");

        Event event = eventByCode(EVENT_CODE);
        List<Registration> allForEvent = registrationRepository.findByEventId(event.getId());
        // Total: 2 registered + 1 waitlist (no duplicate)
        assertThat(allForEvent).hasSize(3);

        List<Registration> waitlisted = registrationRepository.findWaitlistByEventIdOrdered(event.getId());
        assertThat(waitlisted).hasSize(1); // exactly 1 waitlist entry
    }

    // ── Feedback #1: registration deadline closes registration AND waitlist ──────

    @Test
    @DisplayName("feedback #1: public register after the deadline → 409 REGISTRATION_CLOSED, no row")
    void register_afterDeadline_returns409Closed() throws Exception {
        Event closed = savePastDeadlineEvent("BATbern10kC", 9003, 2);

        String body = objectMapper.writeValueAsString(Map.of(
                "firstName", "Late", "lastName", "Comer", "email", "late@test.com",
                "termsAccepted", true));
        mockMvc.perform(post("/api/v1/events/{eventCode}/registrations", "BATbern10kC")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.details.code", is("REGISTRATION_CLOSED")));

        assertThat(registrationRepository.findByEventId(closed.getId()))
                .as("no registration row created after the deadline")
                .isEmpty();
    }

    @Test
    @DisplayName("feedback #1: waitlist join after the deadline → 409 REGISTRATION_CLOSED (not waitlisted)")
    void register_afterDeadline_whenFull_closesWaitlistToo() throws Exception {
        // Capacity 1, already filled (rows inserted directly — they pre-date the closed deadline).
        Event closed = savePastDeadlineEvent("BATbern10kCF", 9004, 1);
        createRegisteredRegistration(closed, "early.bird");

        // A late arrival would normally be waitlisted (event full) — but the deadline closes it.
        String body = objectMapper.writeValueAsString(Map.of(
                "firstName", "Late", "lastName", "Waiter", "email", "latewl@test.com",
                "termsAccepted", true));
        mockMvc.perform(post("/api/v1/events/{eventCode}/registrations", "BATbern10kCF")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.details.code", is("REGISTRATION_CLOSED")));

        assertThat(registrationRepository.findWaitlistByEventIdOrdered(closed.getId()))
                .as("no waitlist row created after the deadline")
                .isEmpty();
    }

    // ── AC3 + T15: Promote endpoint ────────────────────────────────────────────

    @Test
    @DisplayName("POST /promote — organizer promotes valid waitlisted registration → 204")
    @WithMockUser(roles = "ORGANIZER")
    void promoteFromWaitlist_validWaitlistedRegistration_returns204() throws Exception {
        // Fill capacity and create a waitlisted registration directly in DB
        Event event = eventByCode(EVENT_CODE);
        Registration waitlisted = createWaitlistRegistration(event, "waiting.user", 1);

        mockMvc.perform(post("/api/v1/events/{eventCode}/registrations/{registrationCode}/promote",
                        EVENT_CODE, waitlisted.getRegistrationCode()))
                .andExpect(status().isNoContent());

        // Verify promoted
        Registration promoted = registrationRepository.findByRegistrationCode(
                waitlisted.getRegistrationCode()).orElseThrow();
        assertThat(promoted.getStatus()).isEqualTo("registered");
        assertThat(promoted.getWaitlistPosition()).isNull();
    }

    @Test
    @DisplayName("POST /promote — registrationCode not found → 404")
    @WithMockUser(roles = "ORGANIZER")
    void promoteFromWaitlist_notFound_returns404() throws Exception {
        mockMvc.perform(post("/api/v1/events/{eventCode}/registrations/{registrationCode}/promote",
                        EVENT_CODE, "NONEXISTENT-CODE"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /promote — registration exists but status=registered (not waitlist) → 409")
    @WithMockUser(roles = "ORGANIZER")
    void promoteFromWaitlist_alreadyRegistered_returns409() throws Exception {
        Event event = eventByCode(EVENT_CODE);
        Registration alreadyReg = createRegisteredRegistration(event, "already.registered");

        mockMvc.perform(post("/api/v1/events/{eventCode}/registrations/{registrationCode}/promote",
                        EVENT_CODE, alreadyReg.getRegistrationCode()))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("POST /promote — no ORGANIZER role → 403")
    @WithMockUser(roles = "PARTNER")
    void promoteFromWaitlist_noOrganizerRole_returns403() throws Exception {
        Event event = eventByCode(EVENT_CODE);
        Registration waitlisted = createWaitlistRegistration(event, "waiting.user2", 1);

        mockMvc.perform(post("/api/v1/events/{eventCode}/registrations/{registrationCode}/promote",
                        EVENT_CODE, waitlisted.getRegistrationCode()))
                .andExpect(status().isForbidden());
    }

    // ── Raising the capacity fills the free places from the waitlist ──────────

    @Test
    @DisplayName("PATCH capacity 2 → 4 with 3 waiting → positions 1 and 2 promoted, position 3 keeps waiting")
    @WithMockUser(roles = "ORGANIZER")
    void patchCapacity_raised_promotesWaitlistIntoFreePlaces() throws Exception {
        Event event = cappedEvent();
        createActive(event, "active.one", "registered");
        createActive(event, "active.two", "confirmed");
        Registration first = createWaitlistRegistration(event, "waiting.one", 1);
        Registration second = createWaitlistRegistration(event, "waiting.two", 2);
        Registration third = createWaitlistRegistration(event, "waiting.three", 3);

        patchCapacity(4);

        assertThat(statusOf(first)).isEqualTo("registered");
        assertThat(statusOf(second)).isEqualTo("registered");
        Registration stillWaiting = registrationRepository.findByRegistrationCode(
                third.getRegistrationCode()).orElseThrow();
        assertThat(stillWaiting.getStatus()).isEqualTo("waitlist");
        assertThat(stillWaiting.getWaitlistPosition()).isEqualTo(3);
    }

    @Test
    @DisplayName("PATCH with the unchanged capacity still fills places that are already free")
    @WithMockUser(roles = "ORGANIZER")
    void patchCapacity_sameValueWithFreePlace_promotes() throws Exception {
        Event event = cappedEvent();
        createActive(event, "active.one", "confirmed");
        Registration first = createWaitlistRegistration(event, "waiting.one", 1);
        Registration second = createWaitlistRegistration(event, "waiting.two", 2);

        patchCapacity(2);

        assertThat(statusOf(first)).isEqualTo("registered");
        assertThat(statusOf(second)).isEqualTo("waitlist");
    }

    @Test
    @DisplayName("PATCH of another field never touches the waitlist")
    @WithMockUser(roles = "ORGANIZER")
    void patchOtherField_doesNotPromote() throws Exception {
        Event event = cappedEvent();
        Registration first = createWaitlistRegistration(event, "waiting.one", 1);

        mockMvc.perform(patch("/api/v1/events/{eventCode}", PATCHABLE_CODE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"new text\"}"))
                .andExpect(status().isOk());

        assertThat(statusOf(first)).isEqualTo("waitlist");
    }

    @Test
    @DisplayName("PATCH capacity lowered below the active count promotes nobody")
    @WithMockUser(roles = "ORGANIZER")
    void patchCapacity_lowered_promotesNobody() throws Exception {
        Event event = cappedEvent();
        createActive(event, "active.one", "confirmed");
        createActive(event, "active.two", "confirmed");
        Registration first = createWaitlistRegistration(event, "waiting.one", 1);

        patchCapacity(1);

        assertThat(statusOf(first)).isEqualTo("waitlist");
    }

    @Test
    @DisplayName("cancellation while still over capacity promotes nobody")
    @WithMockUser(roles = "ORGANIZER")
    void cancellation_overCapacity_promotesNobody() throws Exception {
        Event event = cappedEvent();
        Registration leaving = createActive(event, "active.one", "confirmed");
        createActive(event, "active.two", "confirmed");
        createActive(event, "active.three", "confirmed"); // organizer override put one over
        Registration first = createWaitlistRegistration(event, "waiting.one", 1);

        mockMvc.perform(patch("/api/v1/events/{eventCode}/registrations/{registrationCode}",
                        PATCHABLE_CODE, leaving.getRegistrationCode())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"cancelled\"}"))
                .andExpect(status().isOk());

        assertThat(statusOf(first)).isEqualTo("waitlist");
    }

    @Test
    @DisplayName("cancellation that frees a place promotes position 1")
    @WithMockUser(roles = "ORGANIZER")
    void cancellation_freesPlace_promotesFirst() throws Exception {
        Event event = cappedEvent();
        Registration leaving = createActive(event, "active.one", "confirmed");
        createActive(event, "active.two", "confirmed");
        Registration first = createWaitlistRegistration(event, "waiting.one", 1);
        Registration second = createWaitlistRegistration(event, "waiting.two", 2);

        mockMvc.perform(patch("/api/v1/events/{eventCode}/registrations/{registrationCode}",
                        PATCHABLE_CODE, leaving.getRegistrationCode())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"cancelled\"}"))
                .andExpect(status().isOk());

        assertThat(statusOf(first)).isEqualTo("registered");
        assertThat(statusOf(second)).isEqualTo("waitlist");
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private void patchCapacity(int capacity) throws Exception {
        mockMvc.perform(patch("/api/v1/events/{eventCode}", PATCHABLE_CODE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"registrationCapacity\":" + capacity + "}"))
                .andExpect(status().isOk());
    }

    /** Capacity 2; a code the PATCH path pattern accepts (the BATbern10k fixture does not). */
    private Event cappedEvent() {
        return eventRepository.save(Event.builder()
                .eventCode(PATCHABLE_CODE)
                .title("Capacity Raise Event")
                .eventNumber(9101)
                .date(Instant.now().plus(60, ChronoUnit.DAYS))
                .registrationDeadline(Instant.now().plus(55, ChronoUnit.DAYS))
                .venueName("Test Venue")
                .venueAddress("Teststrasse 1, Bern")
                .venueCapacity(200)
                .registrationCapacity(2)
                .organizerUsername("test.organizer")
                .currentAttendeeCount(0)
                .eventType(EventType.EVENING)
                .workflowState(ch.batbern.shared.types.EventWorkflowState.CREATED)
                .build());
    }

    private String statusOf(Registration registration) {
        return registrationRepository.findByRegistrationCode(registration.getRegistrationCode())
                .orElseThrow().getStatus();
    }

    private Registration createActive(Event event, String username, String status) {
        return registrationRepository.save(Registration.builder()
                .registrationCode(event.getEventCode() + "-reg-" + username)
                .eventId(event.getId())
                .attendeeUsername(username)
                .status(status)
                .registrationDate(Instant.now())
                .build());
    }

    private void postRegistration(String eventCode, String email) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "firstName", "Test",
                "lastName", "User",
                "email", email,
                "termsAccepted", true
        ));
        mockMvc.perform(post("/api/v1/events/{eventCode}/registrations", eventCode)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().is2xxSuccessful());
    }

    private Event eventByCode(String code) {
        return eventRepository.findByEventCode(code).orElseThrow();
    }

    /** An event whose registration deadline is already in the past (feedback #1). */
    private Event savePastDeadlineEvent(String eventCode, int eventNumber, int capacity) {
        return eventRepository.save(Event.builder()
                .eventCode(eventCode)
                .title("Closed-Registration Event")
                .eventNumber(eventNumber)
                .date(Instant.parse("2026-01-15T18:00:00Z"))
                .registrationDeadline(Instant.parse("2026-01-10T23:59:00Z")) // in the past
                .venueName("Test Venue")
                .venueAddress("Teststrasse 1, Bern")
                .venueCapacity(200)
                .registrationCapacity(capacity)
                .organizerUsername("test.organizer")
                .currentAttendeeCount(0)
                .eventType(EventType.EVENING)
                .workflowState(ch.batbern.shared.types.EventWorkflowState.CREATED)
                .build());
    }

    private Registration createWaitlistRegistration(Event event, String username, int position) {
        Registration reg = Registration.builder()
                .registrationCode(event.getEventCode() + "-reg-WL" + position)
                .eventId(event.getId())
                .attendeeUsername(username)
                .status("waitlist")
                .waitlistPosition(position)
                .registrationDate(Instant.now())
                .build();
        return registrationRepository.save(reg);
    }

    private Registration createRegisteredRegistration(Event event, String username) {
        Registration reg = Registration.builder()
                .registrationCode(event.getEventCode() + "-reg-REG")
                .eventId(event.getId())
                .attendeeUsername(username)
                .status("registered")
                .registrationDate(Instant.now())
                .build();
        return registrationRepository.save(reg);
    }
}
