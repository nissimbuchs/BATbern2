package ch.batbern.events.watch;

import ch.batbern.events.config.TestAwsConfig;
import ch.batbern.events.config.TestSecurityConfig;
import ch.batbern.events.config.TestUserApiClientConfig;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Session;
import ch.batbern.events.dto.generated.EventType;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.SessionRepository;
import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.shared.types.EventWorkflowState;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Story 15.1 — MockMvc integration tests for {@link LiveTimingController}.
 *
 * Covers AC2 (conditional GET → 304), AC7 (anonymous-readable GET, organizer-only POST),
 * AC1 (POST applies the action + bumps the version/ETag), and AC3 (organizer poll sets
 * presence). TestSecurityConfig permits all at the HTTP layer + enforces @PreAuthorize at
 * the method layer, so unauthenticated POST yields 403 (the gateway returns 401 in prod).
 */
@Transactional
@Import({TestSecurityConfig.class, TestAwsConfig.class, TestUserApiClientConfig.class})
class LiveTimingControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private ch.batbern.events.repository.LiveTimingPresenceRepository presenceRepository;

    @Autowired
    private EntityManager entityManager;

    @MockitoBean
    private SimpMessagingTemplate messagingTemplate;

    @BeforeEach
    void setUp() {
        sessionRepository.deleteAll();
        eventRepository.deleteAll();
        presenceRepository.deleteAll();
    }

    private Event saveLiveEvent(String eventCode, int eventNumber) {
        Event event = Event.builder()
                .eventCode(eventCode)
                .eventNumber(eventNumber)
                .title("Live Event")
                .date(Instant.now())
                .registrationDeadline(Instant.now().minus(1, ChronoUnit.DAYS))
                .venueName("Kultur Casino Bern")
                .venueAddress("Casinoplatz 1, 3011 Bern")
                .venueCapacity(150)
                .eventType(EventType.EVENING)
                .workflowState(EventWorkflowState.EVENT_LIVE)
                .organizerUsername("test.organizer")
                .build();
        return eventRepository.save(event);
    }

    private void saveActiveSession(UUID eventId, String eventCode, String slug) {
        Instant base = Instant.now().minus(5, ChronoUnit.MINUTES);
        Session session = Session.builder()
                .eventId(eventId)
                .eventCode(eventCode)
                .sessionSlug(slug)
                .title("Talk " + slug)
                .sessionType("presentation")
                .startTime(base)
                .endTime(base.plus(45, ChronoUnit.MINUTES))
                .actualStartTime(base)
                .build();
        sessionRepository.save(session);
    }

    // MARK: - AC7: GET anonymous-readable + ETag

    @Test
    @DisplayName("should_return200WithEtag_when_anonymousGet")
    void should_return200WithEtag_when_anonymousGet() throws Exception {
        Event event = saveLiveEvent("BATbern80", 9080);
        saveActiveSession(event.getId(), "BATbern80", "talk-a");
        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(get("/api/v1/events/BATbern80/live-timing"))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"evt-BATbern80-0\""))
                .andExpect(jsonPath("$.eventCode").value("BATbern80"))
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.organizerPresent").value(false))
                .andExpect(jsonPath("$.currentSessionSlug").value("talk-a"));
    }

    // MARK: - AC2: conditional GET → 304

    @Test
    @DisplayName("should_return304_when_ifNoneMatchMatchesVersion")
    void should_return304_when_ifNoneMatchMatchesVersion() throws Exception {
        Event event = saveLiveEvent("BATbern81", 9081);
        saveActiveSession(event.getId(), "BATbern81", "talk-a");
        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(get("/api/v1/events/BATbern81/live-timing")
                        .header("If-None-Match", "\"evt-BATbern81-0\""))
                .andExpect(status().isNotModified());
    }

    @Test
    @DisplayName("should_return200_when_ifNoneMatchStale")
    void should_return200_when_ifNoneMatchStale() throws Exception {
        Event event = saveLiveEvent("BATbern82", 9082);
        saveActiveSession(event.getId(), "BATbern82", "talk-a");
        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(get("/api/v1/events/BATbern82/live-timing")
                        .header("If-None-Match", "\"evt-BATbern82-99\""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(0));
    }

    // MARK: - AC7: POST authorization

    @Test
    @DisplayName("should_return403_when_postUnauthenticated")
    void should_return403_when_postUnauthenticated() throws Exception {
        saveLiveEvent("BATbern83", 9083);
        mockMvc.perform(post("/api/v1/events/BATbern83/live-timing/actions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"EXTEND_SESSION\",\"sessionSlug\":\"talk-a\",\"minutes\":5}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("should_return403_when_postAsAttendee")
    @WithMockUser(username = "attendee.user", roles = {"ATTENDEE"})
    void should_return403_when_postAsAttendee() throws Exception {
        saveLiveEvent("BATbern84", 9084);
        mockMvc.perform(post("/api/v1/events/BATbern84/live-timing/actions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"EXTEND_SESSION\",\"sessionSlug\":\"talk-a\",\"minutes\":5}"))
                .andExpect(status().isForbidden());
    }

    // MARK: - AC1: POST applies action + bumps version/ETag

    @Test
    @DisplayName("should_applyActionAndBumpVersion_when_postAsOrganizer")
    @WithMockUser(username = "marco.organizer", roles = {"ORGANIZER"})
    void should_applyActionAndBumpVersion_when_postAsOrganizer() throws Exception {
        Event event = saveLiveEvent("BATbern85", 9085);
        saveActiveSession(event.getId(), "BATbern85", "talk-a");
        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(post("/api/v1/events/BATbern85/live-timing/actions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"EXTEND_SESSION\",\"sessionSlug\":\"talk-a\",\"minutes\":10}"))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"evt-BATbern85-1\""))
                .andExpect(jsonPath("$.version").value(1));
    }

    // MARK: - Validation + not-found

    @Test
    @DisplayName("should_return400_when_extendActionMissingMinutes")
    @WithMockUser(username = "marco.organizer", roles = {"ORGANIZER"})
    void should_return400_when_extendActionMissingMinutes() throws Exception {
        Event event = saveLiveEvent("BATbern87", 9087);
        saveActiveSession(event.getId(), "BATbern87", "talk-a");
        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(post("/api/v1/events/BATbern87/live-timing/actions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"EXTEND_SESSION\",\"sessionSlug\":\"talk-a\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("should_return404_when_unknownEvent")
    void should_return404_when_unknownEvent() throws Exception {
        mockMvc.perform(get("/api/v1/events/BATbern-nope/live-timing"))
                .andExpect(status().isNotFound());
    }

  // MARK: - AC3: organizer poll sets presence

    @Test
    @DisplayName("should_reportOrganizerPresent_when_organizerPolls")
    @WithMockUser(username = "marco.organizer", roles = {"ORGANIZER"})
    void should_reportOrganizerPresent_when_organizerPolls() throws Exception {
        Event event = saveLiveEvent("BATbern86", 9086);
        saveActiveSession(event.getId(), "BATbern86", "talk-a");
        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(get("/api/v1/events/BATbern86/live-timing"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.organizerPresent").value(is(true)));
    }
}
