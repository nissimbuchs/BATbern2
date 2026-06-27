package ch.batbern.events.watch;

import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.events.config.TestAwsConfig;
import ch.batbern.events.config.TestSecurityConfig;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Session;
import ch.batbern.events.domain.SessionUser;
import ch.batbern.events.core.dto.generated.EventType;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.SessionRepository;
import ch.batbern.shared.types.EventWorkflowState;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for WatchEventController.
 * W2.3: Event Join & Schedule Sync
 *
 * Test scenarios:
 * - AC#1: Authenticated organizer receives full schedule
 * - AC#4: Empty list when no active events
 * - 401 when JWT is invalid (no auth)
 * - 403 when user lacks ORGANIZER role
 * - All organizers see all active events (no per-organizer isolation)
 *
 * Uses Testcontainers PostgreSQL for production parity.
 */
@Transactional
@Import({TestSecurityConfig.class, TestAwsConfig.class, ch.batbern.events.config.TestUserApiClientConfig.class})
public class WatchEventControllerIntegrationTest extends AbstractIntegrationTest {

    private static final String ENDPOINT = "/api/v1/watch/organizers/me/active-events";
    private static final String ORGANIZER_USERNAME = "test.organizer";
    private static final String OTHER_ORGANIZER_USERNAME = "other.organizer";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private ch.batbern.events.repository.SessionUserRepository sessionUserRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private int eventNumberCounter = 9000;

    @BeforeEach
    void setUp() {
        sessionUserRepository.deleteAll();
        sessionRepository.deleteAll();
        eventRepository.deleteAll();
        eventNumberCounter = 9000;
    }

    // ============================================================================
    // AC#1: Authenticated organizer receives full schedule
    // ============================================================================

    @Test
    @DisplayName("shouldReturnActiveEvents_whenOrganizerAuthenticated")
    @WithMockUser(username = ORGANIZER_USERNAME, roles = {"ORGANIZER"})
    void shouldReturnActiveEvents_whenOrganizerAuthenticated() throws Exception {
        // Arrange — create event for today with AGENDA_PUBLISHED state
        Event event = createEvent(ORGANIZER_USERNAME, Instant.now(), EventWorkflowState.AGENDA_PUBLISHED);
        Session session = createSession(event, "intro-keynote", "Introduction Keynote",
                "keynote",
                Instant.now().plus(1, ChronoUnit.HOURS),
                Instant.now().plus(2, ChronoUnit.HOURS));
        createSessionUser(session, "john.doe", SessionUser.SpeakerRole.PRIMARY_SPEAKER);

        // Flush and clear Hibernate first-level cache so the controller sees fresh DB data
        entityManager.flush();
        entityManager.clear();

        // Act & Assert
        mockMvc.perform(get(ENDPOINT).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeEvents").isArray())
                .andExpect(jsonPath("$.activeEvents", hasSize(1)))
                .andExpect(jsonPath("$.activeEvents[0].eventCode").value(event.getEventCode()))
                .andExpect(jsonPath("$.activeEvents[0].title").value(event.getTitle()))
                .andExpect(jsonPath("$.activeEvents[0].eventStatus").value("SCHEDULED"))
                .andExpect(jsonPath("$.activeEvents[0].sessions").isArray())
                .andExpect(jsonPath("$.activeEvents[0].sessions", hasSize(1)))
                .andExpect(jsonPath("$.activeEvents[0].sessions[0].sessionSlug").value("intro-keynote"))
                .andExpect(jsonPath("$.activeEvents[0].sessions[0].speakers").isArray())
                .andExpect(jsonPath("$.activeEvents[0].sessions[0].speakers", hasSize(1)))
                .andExpect(jsonPath("$.activeEvents[0].sessions[0].speakers[0].username").value("john.doe"));
    }

    // ============================================================================
    // AC#4: Empty list when no active events
    // ============================================================================

    @Test
    @DisplayName("shouldReturnEmptyList_whenNoActiveEvents")
    @WithMockUser(username = ORGANIZER_USERNAME, roles = {"ORGANIZER"})
    void shouldReturnEmptyList_whenNoActiveEvents() throws Exception {
        // Arrange — no events in database

        // Act & Assert
        mockMvc.perform(get(ENDPOINT).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeEvents").isArray())
                .andExpect(jsonPath("$.activeEvents", hasSize(0)));
    }

    // ============================================================================
    // 403: No authentication provided (TestSecurityConfig uses permitAll + @PreAuthorize)
    //
    // NOTE (M1 fix): Story Task 8.4 specifies "shouldReturn401_whenJWTInvalid".
    // At the service layer, anonymous access yields 403 (AccessDeniedException from @PreAuthorize).
    // The production 401 is enforced at API Gateway before this service is reached.
    // Testing the real JWT 401 path requires a gateway-level integration test (out of scope here).
    // ============================================================================

    @Test
    @DisplayName("shouldReturn403_whenUnauthenticated (service layer; API Gateway returns 401 for invalid JWT)")
    void shouldReturn403_whenUnauthenticated() throws Exception {
        // TestSecurityConfig uses permitAll() at HTTP level + @PreAuthorize at method level.
        // Anonymous users hit @PreAuthorize("hasRole('ORGANIZER')") → AccessDeniedException → 403.
        // In production, the API Gateway rejects unauthenticated requests with 401 before the service.
        mockMvc.perform(get(ENDPOINT).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    // ============================================================================
    // 403: User has no ORGANIZER role
    // ============================================================================

    @Test
    @DisplayName("shouldReturn403_whenUserNotOrganizer")
    @WithMockUser(username = "attendee.user", roles = {"ATTENDEE"})
    void shouldReturn403_whenUserNotOrganizer() throws Exception {
        // Act & Assert
        mockMvc.perform(get(ENDPOINT).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    // ============================================================================
    // All organizers see all active events (no per-organizer isolation)
    // ============================================================================

    @Test
    @DisplayName("shouldReturnAllActiveEvents_regardlessOfOrganizer")
    @WithMockUser(username = ORGANIZER_USERNAME, roles = {"ORGANIZER"})
    void shouldReturnAllActiveEvents_regardlessOfOrganizer() throws Exception {
        // Arrange — two events with different organizer usernames
        createEvent(ORGANIZER_USERNAME, Instant.now(), EventWorkflowState.AGENDA_PUBLISHED);
        createEvent(OTHER_ORGANIZER_USERNAME, Instant.now(), EventWorkflowState.AGENDA_PUBLISHED);

        // Act & Assert — any organizer sees ALL active events
        mockMvc.perform(get(ENDPOINT).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeEvents").isArray())
                .andExpect(jsonPath("$.activeEvents", hasSize(2)));
    }

    // ============================================================================
    // Story 15.5: speaker company logo enrichment
    // ============================================================================

    @Test
    @DisplayName("shouldEnrichSpeakerCompanyLogo_whenCompanyHasLogo (Story 15.5)")
    @WithMockUser(username = ORGANIZER_USERNAME, roles = {"ORGANIZER"})
    void shouldEnrichSpeakerCompanyLogo_whenCompanyHasLogo() throws Exception {
        Event event = createEvent(ORGANIZER_USERNAME, Instant.now(), EventWorkflowState.AGENDA_PUBLISHED);
        Session session = createSession(event, "logo-talk", "Logo Talk", "presentation",
                Instant.now().plus(1, ChronoUnit.HOURS), Instant.now().plus(2, ChronoUnit.HOURS));
        createSessionUser(session, "logo.speaker", SessionUser.SpeakerRole.PRIMARY_SPEAKER);
        seedCompanyAndProfile("logo.speaker", "acme", "ACME Corp",
                "https://cdn.batbern.ch/logos/acme.png");

        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(get(ENDPOINT).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeEvents[0].sessions[0].speakers[0].username").value("logo.speaker"))
                .andExpect(jsonPath("$.activeEvents[0].sessions[0].speakers[0].companyLogoUrl")
                        .value(is("https://cdn.batbern.ch/logos/acme.png")))
                .andExpect(jsonPath("$.activeEvents[0].sessions[0].speakers[0].company")
                        .value(is("ACME Corp")));
    }

    @Test
    @DisplayName("shouldLeaveCompanyLogoNull_whenSpeakerHasNoCompanyLogo (Story 15.5 AC3)")
    @WithMockUser(username = ORGANIZER_USERNAME, roles = {"ORGANIZER"})
    void shouldLeaveCompanyLogoNull_whenSpeakerHasNoCompanyLogo() throws Exception {
        Event event = createEvent(ORGANIZER_USERNAME, Instant.now(), EventWorkflowState.AGENDA_PUBLISHED);
        Session session = createSession(event, "plain-talk", "Plain Talk", "presentation",
                Instant.now().plus(1, ChronoUnit.HOURS), Instant.now().plus(2, ChronoUnit.HOURS));
        createSessionUser(session, "plain.speaker", SessionUser.SpeakerRole.PRIMARY_SPEAKER);
        // No company / profile seeded → graceful null.

        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(get(ENDPOINT).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeEvents[0].sessions[0].speakers[0].username").value("plain.speaker"))
                .andExpect(jsonPath("$.activeEvents[0].sessions[0].speakers[0].companyLogoUrl")
                        .value(nullValue()));
    }

    /** Seeds a company (with logo) + a user_profiles row linking the speaker to it (CUMS side). */
    private void seedCompanyAndProfile(String username, String companyKey, String displayName,
                                       String logoUrl) {
        jdbcTemplate.update("INSERT INTO companies (name, display_name, logo_url) VALUES (?, ?, ?)",
                companyKey, displayName, logoUrl);
        jdbcTemplate.update(
                "INSERT INTO user_profiles (username, company_id, first_name, last_name) "
                        + "VALUES (?, ?, ?, ?)",
                username, companyKey, "Logo", "Speaker");
    }

    // ============================================================================
    // Helpers
    // ============================================================================

    private Event createEvent(String organizerUsername, Instant date, EventWorkflowState state) {
        int number = eventNumberCounter++;
        Event event = Event.builder()
                .eventCode("BATwatch" + number)
                .title("BATbern Watch Test " + number)
                .eventNumber(number)
                .date(date)
                .registrationDeadline(date.minus(7, ChronoUnit.DAYS))
                .venueName("Kultur Casino Bern")
                .venueAddress("Casinoplatz 1, 3011 Bern")
                .venueCapacity(150)
                .organizerUsername(organizerUsername)
                .currentAttendeeCount(0)
                .description("Test event for Watch sync")
                .eventType(EventType.EVENING)
                .workflowState(state)
                .build();
        return eventRepository.save(event);
    }

    private Session createSession(Event event, String slug, String title, String sessionType,
                                  Instant startTime, Instant endTime) {
        Session session = Session.builder()
                .eventId(event.getId())
                .eventCode(event.getEventCode())
                .sessionSlug(slug)
                .title(title)
                .description("Test session description")
                .sessionType(sessionType)
                .startTime(startTime)
                .endTime(endTime)
                .room("Main Hall")
                .capacity(150)
                .language("de")
                .build();
        return sessionRepository.save(session);
    }

    private void createSessionUser(Session session, String username, SessionUser.SpeakerRole role) {
        SessionUser sessionUser = SessionUser.builder()
                .session(session)
                .username(username)
                .speakerRole(role)
                .isConfirmed(true)
                .speakerFirstName("John")
                .speakerLastName("Doe")
                .build();
        sessionUserRepository.save(sessionUser);
    }
}
