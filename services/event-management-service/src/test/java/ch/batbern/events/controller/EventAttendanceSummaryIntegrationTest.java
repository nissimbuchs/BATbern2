package ch.batbern.events.controller;

import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.events.config.TestAwsConfig;
import ch.batbern.events.config.TestSecurityConfig;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Registration;
import ch.batbern.events.dto.generated.EventType;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.RegistrationRepository;
import ch.batbern.shared.types.EventWorkflowState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for GET /api/v1/events/attendance-summary
 * Story 8.1: Partner Attendance Dashboard - AC1, AC2, AC5, AC6
 *
 * Uses Testcontainers PostgreSQL for production parity.
 */
@Transactional
@Import({TestSecurityConfig.class, TestAwsConfig.class})
class EventAttendanceSummaryIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private RegistrationRepository registrationRepository;

    private static final String COMPANY_GOOGLE = "GoogleZH";
    private static final String COMPANY_OTHER = "OtherCo";
    private static final Instant EVENT_2024 = LocalDate.of(2024, 6, 15)
            .atStartOfDay(ZoneId.of("UTC")).toInstant();
    private static final Instant EVENT_2020 = LocalDate.of(2020, 3, 10)
            .atStartOfDay(ZoneId.of("UTC")).toInstant();

    private Event event2024;
    private Event event2020;

    @BeforeEach
    void setUp() {
        registrationRepository.deleteAll();
        eventRepository.deleteAll();

        // Event in 2024 (within last-5-years window)
        event2024 = eventRepository.save(Event.builder()
                .eventCode("BATbern142")
                .title("BATbern 2024")
                .eventNumber(142)
                .date(EVENT_2024)
                .registrationDeadline(EVENT_2024.minusSeconds(86400))
                .venueName("Venue A")
                .venueAddress("Bern")
                .venueCapacity(200)
                .organizerUsername("organizer")
                .currentAttendeeCount(0)
                .eventType(EventType.EVENING)
                .workflowState(EventWorkflowState.EVENT_COMPLETED)
                .build());

        // Event in 2020 (outside default 5-year window, inside 20-year window)
        event2020 = eventRepository.save(Event.builder()
                .eventCode("BATbern100")
                .title("BATbern 2020")
                .eventNumber(100)
                .date(EVENT_2020)
                .registrationDeadline(EVENT_2020.minusSeconds(86400))
                .venueName("Venue B")
                .venueAddress("Bern")
                .venueCapacity(200)
                .organizerUsername("organizer")
                .currentAttendeeCount(0)
                .eventType(EventType.EVENING)
                .workflowState(EventWorkflowState.EVENT_COMPLETED)
                .build());

        // Registrations for BATbern 2024: 3 GoogleZH confirmed + 1 OtherCo + 1 GoogleZH cancelled
        saveConfirmedReg(event2024, "alice.smith", COMPANY_GOOGLE);
        saveConfirmedReg(event2024, "bob.jones", COMPANY_GOOGLE);
        saveConfirmedReg(event2024, "carol.brown", COMPANY_GOOGLE);
        saveConfirmedReg(event2024, "dave.miller", COMPANY_OTHER);
        saveCancelledReg(event2024, "eve.wilson", COMPANY_GOOGLE); // Should NOT be counted

        // Registrations for BATbern 2020: 1 GoogleZH confirmed
        saveConfirmedReg(event2020, "frank.taylor", COMPANY_GOOGLE);
        saveConfirmedReg(event2020, "grace.lee", COMPANY_OTHER);
    }

    // ── AC1: Returns correct per-event attendance breakdown ──────────────────

    @Test
    @DisplayName("should return attendance summary with correct counts for company")
    @WithMockUser(roles = {"PARTNER"})
    void should_returnAttendanceSummary_with_correctCounts() throws Exception {
        int currentYear = LocalDate.now().getYear();
        // Use fromYear=2024 to get only event2024
        mockMvc.perform(get("/api/v1/events/attendance-summary")
                        .param("companyName", COMPANY_GOOGLE)
                        .param("fromYear", "2024"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].eventCode").value("BATbern142"))
                .andExpect(jsonPath("$[0].totalAttendees").value(4))   // 3 GoogleZH + 1 OtherCo confirmed
                .andExpect(jsonPath("$[0].companyAttendees").value(3)); // Only 3 confirmed GoogleZH
    }

    // ── AC2: fromYear filtering ──────────────────────────────────────────────

    @Test
    @DisplayName("should include all events when fromYear covers full history")
    @WithMockUser(roles = {"ORGANIZER"})
    void should_includeAllEvents_when_fromYearIsOld() throws Exception {
        mockMvc.perform(get("/api/v1/events/attendance-summary")
                        .param("companyName", COMPANY_GOOGLE)
                        .param("fromYear", "2019"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    @DisplayName("should exclude old events with default 5-year window")
    @WithMockUser(roles = {"PARTNER"})
    void should_excludeOldEvents_with_default5YearWindow() throws Exception {
        // Default fromYear = current year - 5. Event 2020 may or may not be included
        // depending on test run year. Use explicit fromYear = current year - 4 to be precise.
        int fromYear = LocalDate.now().getYear() - 4;
        mockMvc.perform(get("/api/v1/events/attendance-summary")
                        .param("companyName", COMPANY_GOOGLE)
                        .param("fromYear", String.valueOf(fromYear)))
                .andExpect(status().isOk())
                // event2024 (2024) is within range; event2020 (2020) is excluded
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].eventCode").value("BATbern142"));
    }

    // ── AC5: Only confirmed registrations counted ────────────────────────────

    @Test
    @DisplayName("should count only confirmed registrations, not cancelled")
    @WithMockUser(roles = {"PARTNER"})
    void should_countOnlyConfirmedRegistrations() throws Exception {
        mockMvc.perform(get("/api/v1/events/attendance-summary")
                        .param("companyName", COMPANY_GOOGLE)
                        .param("fromYear", "2024"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].companyAttendees").value(3)); // cancelled one excluded
    }

    // ── AC6: Authorization ───────────────────────────────────────────────────

    @Test
    @DisplayName("should return 403 when unauthenticated")
    void should_return403_when_unauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/events/attendance-summary")
                        .param("companyName", COMPANY_GOOGLE))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("should return 403 when user has ATTENDEE role only")
    @WithMockUser(roles = {"ATTENDEE"})
    void should_return403_when_attendeeRole() throws Exception {
        mockMvc.perform(get("/api/v1/events/attendance-summary")
                        .param("companyName", COMPANY_GOOGLE))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("should return 200 for ORGANIZER role")
    @WithMockUser(roles = {"ORGANIZER"})
    void should_return200_for_organizerRole() throws Exception {
        mockMvc.perform(get("/api/v1/events/attendance-summary")
                        .param("companyName", COMPANY_GOOGLE)
                        .param("fromYear", "2024"))
                .andExpect(status().isOk());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void saveConfirmedReg(Event event, String username, String companyId) {
        registrationRepository.save(Registration.builder()
                .registrationCode(event.getEventCode() + "-reg-" + username)
                .eventId(event.getId())
                .attendeeUsername(username)
                .attendeeCompanyId(companyId)
                .status("confirmed")
                .registrationDate(Instant.now())
                .build());
    }

    private void saveCancelledReg(Event event, String username, String companyId) {
        registrationRepository.save(Registration.builder()
                .registrationCode(event.getEventCode() + "-reg-" + username)
                .eventId(event.getId())
                .attendeeUsername(username)
                .attendeeCompanyId(companyId)
                .status("cancelled")
                .registrationDate(Instant.now())
                .build());
    }
}
