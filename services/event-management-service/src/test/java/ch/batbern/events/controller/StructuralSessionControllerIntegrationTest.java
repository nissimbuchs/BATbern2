package ch.batbern.events.controller;

import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.events.client.UserApiClient;
import ch.batbern.events.config.TestAwsConfig;
import ch.batbern.events.config.TestSecurityConfig;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Session;
import ch.batbern.events.dto.generated.EventType;
import ch.batbern.events.dto.generated.users.UserResponse;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.EventTypeRepository;
import ch.batbern.events.repository.SessionRepository;
import ch.batbern.shared.types.EventWorkflowState;
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

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for POST /api/v1/events/{eventCode}/sessions/structural
 *
 * Tests verify:
 * - 201 with structural sessions created for FULL_DAY event
 * - 409 on second call (overwrite=false default)
 * - 201 on second call with overwrite=true (replaces sessions)
 * - 403 for non-ORGANIZER role
 * - 404 for unknown event code
 *
 * Uses PostgreSQL via Testcontainers for production parity (never H2).
 */
@Transactional
@Import({TestSecurityConfig.class, TestAwsConfig.class})
class StructuralSessionControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private EventTypeRepository eventTypeRepository;

    @MockitoBean
    private UserApiClient userApiClient;

    private static final String EVENT_CODE = "BATbern999";

    @BeforeEach
    void setUp() {
        sessionRepository.deleteAll();
        eventRepository.deleteAll();
        createTestEvent();
        updateEventTypeConfig();
        mockUserApiClient();
    }

    private void mockUserApiClient() {
        when(userApiClient.getUserByUsername(anyString())).thenAnswer(invocation -> {
            String username = invocation.getArgument(0);
            return new UserResponse()
                    .id(username)
                    .firstName("John")
                    .lastName("Doe")
                    .email(username + "@example.com")
                    .companyId("test-company");
        });
    }

    private void createTestEvent() {
        Event event = new Event();
        event.setEventNumber(999);
        event.setEventCode(EVENT_CODE);
        event.setTitle("BATbern 999 Structural Test");
        event.setDescription("Integration test event");
        event.setEventType(EventType.FULL_DAY);
        event.setDate(Instant.parse("2025-09-15T07:00:00Z")); // 09:00 Swiss time
        event.setRegistrationDeadline(Instant.parse("2025-09-10T23:59:59Z"));
        event.setVenueName("Test Venue");
        event.setVenueAddress("Bern, Switzerland");
        event.setVenueCapacity(300);
        event.setOrganizerUsername("john.doe");
        event.setWorkflowState(EventWorkflowState.AGENDA_PUBLISHED);
        event.setCreatedAt(Instant.now());
        event.setUpdatedAt(Instant.now());
        eventRepository.save(event);
    }

    private void updateEventTypeConfig() {
        // Ensure FULL_DAY config has timing fields set (V58 migration adds defaults)
        eventTypeRepository.findByType(EventType.FULL_DAY).ifPresent(config -> {
            config.setModerationStartDuration(5);
            config.setModerationEndDuration(5);
            config.setBreakDuration(20);
            config.setLunchDuration(60);
            config.setTypicalStartTime(LocalTime.of(9, 0));
            config.setTypicalEndTime(LocalTime.of(17, 0));
            eventTypeRepository.save(config);
        });
    }

    @Test
    @DisplayName("Should return 201 with structural sessions when FULL_DAY event requested")
    @WithMockUser(username = "john.doe", roles = {"ORGANIZER"})
    void should_return201WithSessions_when_structuralSessionsGenerated() throws Exception {
        mockMvc.perform(post("/api/v1/events/{eventCode}/sessions/structural", EVENT_CODE)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].sessionType").value("moderation"))
                .andExpect(jsonPath("$[0].title").value("Moderation Start"));

        // Verify sessions were persisted
        var event = eventRepository.findByEventCode(EVENT_CODE).orElseThrow();
        List<Session> sessions = sessionRepository.findByEventId(event.getId());
        assertThat(sessions).isNotEmpty();
        assertThat(sessions.stream().map(Session::getSessionType))
                .contains("moderation", "lunch");
    }

    @Test
    @DisplayName("Should return 409 on second call when overwrite=false (default)")
    @WithMockUser(username = "john.doe", roles = {"ORGANIZER"})
    void should_return409_when_structuralSessionsAlreadyExist() throws Exception {
        // First call: succeeds
        mockMvc.perform(post("/api/v1/events/{eventCode}/sessions/structural", EVENT_CODE)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated());

        // Second call: 409 conflict
        mockMvc.perform(post("/api/v1/events/{eventCode}/sessions/structural", EVENT_CODE)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Should return 201 with overwrite=true even if sessions already exist")
    @WithMockUser(username = "john.doe", roles = {"ORGANIZER"})
    void should_return201_when_overwriteTrue() throws Exception {
        // First call
        mockMvc.perform(post("/api/v1/events/{eventCode}/sessions/structural", EVENT_CODE)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated());

        var event = eventRepository.findByEventCode(EVENT_CODE).orElseThrow();
        int sessionsAfterFirst = sessionRepository.findByEventId(event.getId()).size();

        // Second call with overwrite=true: succeeds and replaces
        mockMvc.perform(post("/api/v1/events/{eventCode}/sessions/structural?overwrite=true", EVENT_CODE)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated());

        // Should have same number of sessions (replaced, not added)
        int sessionsAfterSecond = sessionRepository.findByEventId(event.getId()).size();
        assertThat(sessionsAfterSecond).isEqualTo(sessionsAfterFirst);
    }

    @Test
    @DisplayName("Should return 403 when non-ORGANIZER calls the endpoint")
    @WithMockUser(username = "attendee.user", roles = {"ATTENDEE"})
    void should_return403_when_nonOrganizerCalls() throws Exception {
        mockMvc.perform(post("/api/v1/events/{eventCode}/sessions/structural", EVENT_CODE)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Should return 404 when event code does not exist")
    @WithMockUser(username = "john.doe", roles = {"ORGANIZER"})
    void should_return404_when_eventNotFound() throws Exception {
        mockMvc.perform(post("/api/v1/events/{eventCode}/sessions/structural", "BATbernXXX")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }
}
