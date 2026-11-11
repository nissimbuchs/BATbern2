package ch.batbern.events.controller;

import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.events.config.TestAwsConfig;
import ch.batbern.events.config.TestSecurityConfig;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Registration;
import ch.batbern.events.core.dto.generated.EventType;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.RegistrationRepository;
import ch.batbern.shared.types.EventWorkflowState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for {@code GET /internal/events/attendance-summary} (ems-internal, Epic 17 6b;
 * the public {@code GET /api/v1/events/attendance-summary} before). Story 8.1: Partner Attendance
 * Dashboard - AC1, AC2, AC5. The role check (AC6) lives on PCS {@code PartnerAnalyticsController};
 * this operation answers with and without a forwarded JWT.
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

    private static final String PATH = "/internal/events/attendance-summary";
    private static final String COMPANY_GOOGLE = "GoogleZH";
    private static final String COMPANY_DEFAULT_WINDOW = "WindowCo";
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
    @DisplayName("should return attendance summary with correct counts for company, with the forwarded JWT")
    void should_returnAttendanceSummary_with_correctCounts() throws Exception {
        // fromYear=2024 gets only event2024
        mockMvc.perform(get(PATH)
                        .with(partnerJwt())
                        .param("companyName", COMPANY_GOOGLE)
                        .param("fromYear", "2024"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].eventCode").value("BATbern142"))
                .andExpect(jsonPath("$[0].eventTitle").value("BATbern 2024"))
                .andExpect(jsonPath("$[0].eventDate").value("2024-06-15T00:00:00Z"))
                .andExpect(jsonPath("$[0].totalAttendees").value(4))   // 3 GoogleZH + 1 OtherCo confirmed
                .andExpect(jsonPath("$[0].companyAttendees").value(3)); // Only 3 confirmed GoogleZH
    }

    // ── AC2: fromYear filtering ──────────────────────────────────────────────

    @Test
    @DisplayName("should include all events, newest first, when fromYear covers full history")
    void should_includeAllEvents_when_fromYearIsOld() throws Exception {
        mockMvc.perform(get(PATH)
                        .param("companyName", COMPANY_GOOGLE)
                        .param("fromYear", "2019"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].eventCode").value("BATbern142"))
                .andExpect(jsonPath("$[1].eventCode").value("BATbern100"))
                .andExpect(jsonPath("$[1].companyAttendees").value(1));
    }

    @Test
    @DisplayName("without fromYear: from 1 January of the current year minus 5 (UTC)")
    void should_useFiveYearWindow_when_fromYearMissing() throws Exception {
        int firstYear = LocalDate.now(ZoneOffset.UTC).getYear() - 5;
        Event inside = saveEvent("BATbern9101", 9101,
                LocalDate.of(firstYear, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant());
        Event outside = saveEvent("BATbern9100", 9100,
                LocalDate.of(firstYear - 1, 12, 31).atTime(23, 0).toInstant(ZoneOffset.UTC));
        saveConfirmedReg(inside, "henry.ford", COMPANY_DEFAULT_WINDOW);
        saveConfirmedReg(outside, "iris.west", COMPANY_DEFAULT_WINDOW);

        // Positive control: with an explicit earlier fromYear both events are there.
        mockMvc.perform(get(PATH)
                        .param("companyName", COMPANY_DEFAULT_WINDOW)
                        .param("fromYear", String.valueOf(firstYear - 1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].eventCode",
                        hasItems("BATbern9101", "BATbern9100")));

        mockMvc.perform(get(PATH).param("companyName", COMPANY_DEFAULT_WINDOW))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].eventCode", hasItem("BATbern9101")))
                .andExpect(jsonPath("$[*].eventCode", not(hasItem("BATbern9100"))));
    }

    // ── AC5: Only confirmed registrations counted ────────────────────────────

    @Test
    @DisplayName("should count only confirmed registrations, not cancelled")
    void should_countOnlyConfirmedRegistrations() throws Exception {
        mockMvc.perform(get(PATH)
                        .param("companyName", COMPANY_GOOGLE)
                        .param("fromYear", "2024"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].companyAttendees").value(3)); // cancelled one excluded
    }

    // ── Authorized by operation (ADR-016 decision 3) ─────────────────────────

    @Test
    @DisplayName("anonymous (scheduler, async, no user): the same answer as with a user JWT")
    void should_answerTheSame_withAndWithoutJwt() throws Exception {
        String anonymous = mockMvc.perform(get(PATH)
                        .param("companyName", COMPANY_GOOGLE)
                        .param("fromYear", "2019"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String signedIn = mockMvc.perform(get(PATH)
                        .with(partnerJwt())
                        .param("companyName", COMPANY_GOOGLE)
                        .param("fromYear", "2019"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(signedIn).isEqualTo(anonymous).contains("BATbern142");
    }

    @Test
    @DisplayName("an attendee's forwarded JWT is no reason to refuse: the role check is PCS's")
    void should_notCheckTheRole_ofTheForwardedUser() throws Exception {
        mockMvc.perform(get(PATH)
                        .with(jwt().jwt(j -> j.claim("custom:username", "some.attendee")
                                .claim("custom:role", "ATTENDEE")))
                        .param("companyName", COMPANY_GOOGLE)
                        .param("fromYear", "2024"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    @DisplayName("companyName missing: 400")
    void should_return400_when_companyNameMissing() throws Exception {
        mockMvc.perform(get(PATH))
                .andExpect(status().isBadRequest());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static RequestPostProcessor partnerJwt() {
        return jwt().jwt(j -> j.claim("custom:username", "partner.google").claim("custom:role", "PARTNER"));
    }

    private Event saveEvent(String code, int number, Instant date) {
        return eventRepository.save(Event.builder()
                .eventCode(code)
                .title("BATbern " + number)
                .eventNumber(number)
                .date(date)
                .registrationDeadline(date.minusSeconds(86400))
                .venueName("Venue C")
                .venueAddress("Bern")
                .venueCapacity(200)
                .organizerUsername("organizer")
                .currentAttendeeCount(0)
                .eventType(EventType.EVENING)
                .workflowState(EventWorkflowState.EVENT_COMPLETED)
                .build());
    }

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
