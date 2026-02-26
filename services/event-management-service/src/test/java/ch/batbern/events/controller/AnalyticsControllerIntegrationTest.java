package ch.batbern.events.controller;

import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.events.config.TestAwsConfig;
import ch.batbern.events.config.TestSecurityConfig;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Registration;
import ch.batbern.events.domain.Session;
import ch.batbern.events.domain.SessionUser;
import ch.batbern.events.domain.Topic;
import ch.batbern.events.dto.generated.EventType;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.RegistrationRepository;
import ch.batbern.events.repository.SessionRepository;
import ch.batbern.events.repository.SessionUserRepository;
import ch.batbern.events.repository.TopicRepository;
import ch.batbern.shared.types.EventWorkflowState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for GET /api/v1/analytics/* endpoints.
 * Story 10.5: Analytics Dashboard (AC6)
 *
 * Uses Testcontainers PostgreSQL for production parity.
 */
@Transactional
@Import({TestSecurityConfig.class, TestAwsConfig.class})
class AnalyticsControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private RegistrationRepository registrationRepository;

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private SessionUserRepository sessionUserRepository;

    @Autowired
    private TopicRepository topicRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final String COMPANY_GOOGLE = "GoogleZH";
    private static final String COMPANY_OTHER = "OtherCo";
    private static final ZoneId ZURICH = ZoneId.of("Europe/Zurich");
    private static final Instant EVENT_DATE_2024 = LocalDate.of(2024, 6, 15)
            .atStartOfDay(ZURICH).toInstant();
    private static final Instant EVENT_DATE_2022 = LocalDate.of(2022, 3, 10)
            .atStartOfDay(ZURICH).toInstant();

    private Event event2024;
    private Event event2022;

    @BeforeEach
    void setUp() {
        sessionUserRepository.deleteAll();
        sessionRepository.deleteAll();
        registrationRepository.deleteAll();
        eventRepository.deleteAll();
        topicRepository.deleteAll();
        jdbcTemplate.update("DELETE FROM user_profiles WHERE username IN (?, ?)", "alice.smith", "bob.jones");

        // Create topics using valid DB constraint values:
        // topics_category_check: ('technical','management','soft_skills','industry_trends','tools_platforms')
        Topic topicArch = new Topic();
        topicArch.setTopicCode("arch-ddd");
        topicArch.setTitle("Domain-Driven Design");
        topicArch.setCategory("technical");
        topicRepository.save(topicArch);

        Topic topicSec = new Topic();
        topicSec.setTopicCode("sec-zero-trust");
        topicSec.setTitle("Zero Trust Security");
        topicSec.setCategory("management");
        topicRepository.save(topicSec);

        // Create events
        event2024 = eventRepository.save(Event.builder()
                .eventCode("BATbern57")
                .title("Architecture at Scale")
                .eventNumber(57)
                .date(EVENT_DATE_2024)
                .registrationDeadline(EVENT_DATE_2024.minusSeconds(86400))
                .venueName("Venue A")
                .venueAddress("Bern")
                .venueCapacity(200)
                .organizerUsername("organizer")
                .currentAttendeeCount(0)
                .eventType(EventType.EVENING)
                .workflowState(EventWorkflowState.EVENT_COMPLETED)
                .topicCode("arch-ddd")
                .build());

        event2022 = eventRepository.save(Event.builder()
                .eventCode("BATbern50")
                .title("Zero Trust Architecture")
                .eventNumber(50)
                .date(EVENT_DATE_2022)
                .registrationDeadline(EVENT_DATE_2022.minusSeconds(86400))
                .venueName("Venue B")
                .venueAddress("Bern")
                .venueCapacity(150)
                .organizerUsername("organizer")
                .currentAttendeeCount(0)
                .eventType(EventType.EVENING)
                .workflowState(EventWorkflowState.EVENT_COMPLETED)
                .topicCode("sec-zero-trust")
                .build());

        // Registrations for BATbern 57 (2024): 3 confirmed GoogleZH, 2 confirmed OtherCo, 1 cancelled
        saveConfirmedReg(event2024, "alice.smith", COMPANY_GOOGLE);
        saveConfirmedReg(event2024, "bob.jones", COMPANY_GOOGLE);
        saveConfirmedReg(event2024, "carol.brown", COMPANY_GOOGLE);
        saveConfirmedReg(event2024, "dave.miller", COMPANY_OTHER);
        saveConfirmedReg(event2024, "eve.wilson", COMPANY_OTHER);
        saveCancelledReg(event2024, "frank.taylor", COMPANY_GOOGLE); // excluded

        // Registrations for BATbern 50 (2022): 2 confirmed GoogleZH, 1 confirmed OtherCo
        // alice.smith at both events → returning at event2024 (if ordered correctly)
        saveConfirmedReg(event2022, "alice.smith", COMPANY_GOOGLE);
        saveConfirmedReg(event2022, "grace.lee", COMPANY_GOOGLE);
        saveConfirmedReg(event2022, "henry.clark", COMPANY_OTHER);

        // Sessions for BATbern 57
        Session session2024 = sessionRepository.save(Session.builder()
                .eventId(event2024.getId())
                .eventCode("BATbern57")
                .sessionSlug("batbern57-ddd-in-microservices")
                .title("DDD in Microservices")
                .build());

        // Session speakers: alice.smith (GoogleZH) and bob.jones (GoogleZH)
        // user_profiles stub table is created by V100 test migration so the
        // findSessionsPerCompany JOIN resolves company_id from user_profiles.
        saveConfirmedSpeaker(session2024, "alice.smith");
        saveConfirmedSpeaker(session2024, "bob.jones");

        jdbcTemplate.update(
                "INSERT INTO user_profiles (username, company_id, first_name, last_name) VALUES (?, ?, ?, ?)",
                "alice.smith", COMPANY_GOOGLE, "Alice", "Smith");
        jdbcTemplate.update(
                "INSERT INTO user_profiles (username, company_id, first_name, last_name) VALUES (?, ?, ?, ?)",
                "bob.jones", COMPANY_GOOGLE, "Bob", "Jones");
    }

    // ── Overview endpoint ────────────────────────────────────────────────────

    @Test
    @DisplayName("should return overview KPIs with correct totals")
    @WithMockUser(roles = {"ORGANIZER"})
    void should_returnOverviewKpis_when_eventsExist() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalEvents").value(2))
                .andExpect(jsonPath("$.totalAttendees").value(8)) // 5 + 3 confirmed
                .andExpect(jsonPath("$.companiesRepresented").value(2)) // GoogleZH, OtherCo
                .andExpect(jsonPath("$.timeline").isArray())
                .andExpect(jsonPath("$.timeline", hasSize(2)));
    }

    @Test
    @DisplayName("should return overview timeline with category from topic join")
    @WithMockUser(roles = {"PARTNER"})
    void should_returnTimelineWithCategory_when_topicAssigned() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timeline[?(@.eventCode == 'BATbern57')].category")
                        .value("technical"));
    }

    // ── Attendance endpoint ──────────────────────────────────────────────────

    @Test
    @DisplayName("should return per-event attendance with returning/new breakdown")
    @WithMockUser(roles = {"ORGANIZER"})
    void should_returnAttendanceWithReturningNew_when_eventsExist() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/attendance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.events").isArray())
                .andExpect(jsonPath("$.events", hasSize(2)));
    }

    @Test
    @DisplayName("should filter attendance by fromYear")
    @WithMockUser(roles = {"ORGANIZER"})
    void should_filterAttendanceByFromYear() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/attendance").param("fromYear", "2023"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.events", hasSize(1)))
                .andExpect(jsonPath("$.events[0].eventCode").value("BATbern57"))
                .andExpect(jsonPath("$.events[0].totalAttendees").value(5));
    }

    @Test
    @DisplayName("should return empty events list when no events in time range")
    @WithMockUser(roles = {"ORGANIZER"})
    void should_returnEmptyAttendance_when_noEventsInRange() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/attendance").param("fromYear", "2030"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.events", hasSize(0)));
    }

    @Test
    @DisplayName("attendance — alice.smith is new at 2022 event, returning at 2024 event")
    @WithMockUser(roles = {"ORGANIZER"})
    void should_classifyReturningAttendees_correctly() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/attendance"))
                .andExpect(status().isOk())
                // 2022 event: all 3 are new (first ever event)
                .andExpect(jsonPath("$.events[?(@.eventCode == 'BATbern50')].newAttendees")
                        .value(3))
                .andExpect(jsonPath("$.events[?(@.eventCode == 'BATbern50')].returningAttendees")
                        .value(0))
                // 2024 event: alice.smith is returning, 4 are new
                .andExpect(jsonPath("$.events[?(@.eventCode == 'BATbern57')].returningAttendees")
                        .value(1))
                .andExpect(jsonPath("$.events[?(@.eventCode == 'BATbern57')].newAttendees")
                        .value(4));
    }

    // ── Topics endpoint ──────────────────────────────────────────────────────

    @Test
    @DisplayName("should return events per category and topic scatter data")
    @WithMockUser(roles = {"ORGANIZER"})
    void should_returnTopicsAnalytics_when_eventsWithTopicsExist() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/topics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventsPerCategory").isArray())
                .andExpect(jsonPath("$.topicScatter").isArray());
    }

    @Test
    @DisplayName("should group events per category correctly")
    @WithMockUser(roles = {"PARTNER"})
    void should_groupEventsPerCategory_correctly() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/topics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventsPerCategory[?(@.category == 'technical')].eventCount")
                        .value(1))
                .andExpect(jsonPath("$.eventsPerCategory[?(@.category == 'management')].eventCount")
                        .value(1));
    }

    @Test
    @DisplayName("should filter topics by fromYear")
    @WithMockUser(roles = {"ORGANIZER"})
    void should_filterTopicsByFromYear() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/topics").param("fromYear", "2023"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventsPerCategory", hasSize(1)))
                .andExpect(jsonPath("$.eventsPerCategory[0].category").value("technical"));
    }

    // ── Companies endpoint ───────────────────────────────────────────────────

    @Test
    @DisplayName("should return company analytics datasets")
    @WithMockUser(roles = {"ORGANIZER"})
    void should_returnCompanyAnalytics_when_eventsExist() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/companies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attendanceOverTime").isArray())
                .andExpect(jsonPath("$.sessionsPerCompany").isArray())
                .andExpect(jsonPath("$.distribution").isArray());
    }

    @Test
    @DisplayName("should return company distribution with attendance counts")
    @WithMockUser(roles = {"PARTNER"})
    void should_returnDistribution_withCorrectCounts() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/companies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.distribution[?(@.companyName == 'GoogleZH')].attendeeCount",
                        hasItem(greaterThan(0))))
                .andExpect(jsonPath("$.distribution[?(@.companyName == 'OtherCo')].attendeeCount",
                        hasItem(greaterThan(0))));
    }

    @Test
    @DisplayName("should filter company attendance by fromYear")
    @WithMockUser(roles = {"ORGANIZER"})
    void should_filterCompanyAttendanceByFromYear() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/companies").param("fromYear", "2023"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attendanceOverTime").isArray());
    }

    // ── Companies distribution endpoint ─────────────────────────────────────

    @Test
    @DisplayName("should return per-event company distribution")
    @WithMockUser(roles = {"ORGANIZER"})
    void should_returnPerEventDistribution_for_knownEvent() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/companies/distribution")
                        .param("eventCode", "BATbern57"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventCode").value("BATbern57"))
                .andExpect(jsonPath("$.distribution").isArray())
                .andExpect(jsonPath("$.distribution[?(@.companyName == 'GoogleZH')].attendeeCount")
                        .value(3))
                .andExpect(jsonPath("$.distribution[?(@.companyName == 'OtherCo')].attendeeCount")
                        .value(2));
    }

    @Test
    @DisplayName("should return empty distribution for event with no registrations")
    @WithMockUser(roles = {"ORGANIZER"})
    void should_returnEmptyDistribution_for_unknownEvent() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/companies/distribution")
                        .param("eventCode", "BATbern99"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventCode").value("BATbern99"))
                .andExpect(jsonPath("$.distribution", hasSize(0)));
    }

    // ── Authorization tests ──────────────────────────────────────────────────

    @Test
    @DisplayName("should return 403 for unauthenticated request to overview")
    void should_return403_when_unauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/overview"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("should return 403 for ATTENDEE role on overview")
    @WithMockUser(roles = {"ATTENDEE"})
    void should_return403_when_attendeeRole() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/overview"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("should allow PARTNER role to access overview")
    @WithMockUser(roles = {"PARTNER"})
    void should_return200_for_partnerRole() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/overview"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("should allow ORGANIZER role to access all endpoints")
    @WithMockUser(roles = {"ORGANIZER"})
    void should_return200_for_organizerRole_allEndpoints() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/overview")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/analytics/attendance")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/analytics/topics")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/analytics/companies")).andExpect(status().isOk());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void saveConfirmedReg(Event event, String username, String companyId) {
        registrationRepository.save(Registration.builder()
                .registrationCode(event.getEventCode() + "-reg-" + username)
                .eventId(event.getId())
                .attendeeUsername(username)
                .attendeeCompanyId(companyId)
                .status("confirmed")
                .registrationDate(event.getDate().minusSeconds(86400))
                .build());
    }

    private void saveCancelledReg(Event event, String username, String companyId) {
        registrationRepository.save(Registration.builder()
                .registrationCode(event.getEventCode() + "-reg-cancelled-" + username)
                .eventId(event.getId())
                .attendeeUsername(username)
                .attendeeCompanyId(companyId)
                .status("cancelled")
                .registrationDate(event.getDate().minusSeconds(86400))
                .build());
    }

    private void saveConfirmedSpeaker(Session session, String username) {
        sessionUserRepository.save(SessionUser.builder()
                .session(session)
                .username(username)
                .speakerRole(SessionUser.SpeakerRole.PRIMARY_SPEAKER)
                .isConfirmed(true)
                .build());
    }
}
