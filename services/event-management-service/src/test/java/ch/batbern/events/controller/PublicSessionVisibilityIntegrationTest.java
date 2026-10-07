package ch.batbern.events.controller;

import ch.batbern.events.client.UserApiClient;
import ch.batbern.events.config.TestAwsConfig;
import ch.batbern.events.config.TestSecurityConfig;
import ch.batbern.events.core.dto.generated.EventType;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Session;
import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.LogoRepository;
import ch.batbern.events.repository.SessionRepository;
import ch.batbern.events.repository.SpeakerPoolRepository;
import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.shared.types.EventWorkflowState;
import ch.batbern.shared.types.SpeakerWorkflowState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Public (non-organizer) reads of an upcoming event only expose sessions and speakers the
 * publishing phase allows (bug fix 2026-10-07: BATbern60 showed READY/INVITED speakers after the
 * SPEAKERS phase auto-published). Organizers keep seeing every session.
 *
 * <p>Fixture: one upcoming event with five sessions, all slotted except "Reviewed unslotted":
 * <ul>
 *   <li>"Invited talk": speaker INVITED</li>
 *   <li>"Accepted talk": speaker ACCEPTED</li>
 *   <li>"Reviewed talk": speaker QUALITY_REVIEWED</li>
 *   <li>"Reviewed unslotted": speaker QUALITY_REVIEWED, no slot</li>
 *   <li>"Lunch": structural, no speaker</li>
 * </ul>
 */
@Transactional
@Import({TestSecurityConfig.class, TestAwsConfig.class})
class PublicSessionVisibilityIntegrationTest extends AbstractIntegrationTest {

    private static final Instant EVENT_DATE = Instant.now().plus(30, ChronoUnit.DAYS)
            .truncatedTo(ChronoUnit.DAYS);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private SpeakerPoolRepository speakerPoolRepository;

    @Autowired
    private CacheManager cacheManager;

    @MockitoBean
    private UserApiClient userApiClient;

    @MockitoBean
    private LogoRepository logoRepository;

    private Event event;

    @BeforeEach
    void setUp() {
        cacheManager.getCacheNames().forEach(name ->
                Objects.requireNonNull(cacheManager.getCache(name)).clear());
        eventRepository.deleteAll();

        event = eventRepository.save(Event.builder()
                .eventCode("BATbern9060")
                .title("Visibility Test Event")
                .eventNumber(9060)
                .date(EVENT_DATE)
                .registrationDeadline(EVENT_DATE.minus(7, ChronoUnit.DAYS))
                .venueName("Kornhausforum")
                .venueAddress("Kornhausplatz 18, 3011 Bern")
                .venueCapacity(200)
                .organizerUsername("test.organizer")
                .currentAttendeeCount(0)
                .eventType(EventType.EVENING)
                .workflowState(EventWorkflowState.SLOT_ASSIGNMENT)
                .currentPublishedPhase("speakers")
                .build());

        speakerSession("Invited talk", SpeakerWorkflowState.INVITED, true);
        speakerSession("Accepted talk", SpeakerWorkflowState.ACCEPTED, true);
        speakerSession("Reviewed talk", SpeakerWorkflowState.QUALITY_REVIEWED, true);
        speakerSession("Reviewed unslotted", SpeakerWorkflowState.QUALITY_REVIEWED, false);
        session("Lunch", "lunch", true);
    }

    // ----------------------------------------------------------------------------------------
    // GET /events/current
    // ----------------------------------------------------------------------------------------

    @Test
    @DisplayName("should_exposeOnlyAcceptedOrLaterSpeakers_when_publicReadsCurrentEventInSpeakersPhase")
    void should_exposeOnlyAcceptedOrLaterSpeakers_when_publicReadsCurrentEventInSpeakersPhase() throws Exception {
        mockMvc.perform(get("/api/v1/events/current").param("include", "topics,venue,speakers,sessions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventCode").value("BATbern9060"))
                .andExpect(jsonPath("$.sessions[*].title", containsInAnyOrder(
                        "Accepted talk", "Reviewed talk", "Reviewed unslotted")));
    }

    @Test
    @DisplayName("should_exposeNoSessions_when_publicReadsCurrentEventInTopicPhase")
    void should_exposeNoSessions_when_publicReadsCurrentEventInTopicPhase() throws Exception {
        setPhase("topic");

        mockMvc.perform(get("/api/v1/events/current").param("include", "sessions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessions", hasSize(0)));
    }

    @Test
    @DisplayName("should_exposeOnlySlottedReviewedSessions_when_publicReadsCurrentEventInAgendaPhase")
    void should_exposeOnlySlottedReviewedSessions_when_publicReadsCurrentEventInAgendaPhase() throws Exception {
        setPhase("agenda");

        mockMvc.perform(get("/api/v1/events/current").param("include", "sessions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessions[*].title", containsInAnyOrder("Reviewed talk", "Lunch")));
    }

    // ----------------------------------------------------------------------------------------
    // GET /events/{code} (cached per audience)
    // ----------------------------------------------------------------------------------------

    @Test
    @DisplayName("should_filterSessions_when_publicReadsEventByCode")
    void should_filterSessions_when_publicReadsEventByCode() throws Exception {
        mockMvc.perform(get("/api/v1/events/BATbern9060").param("include", "sessions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessions[*].title", containsInAnyOrder(
                        "Accepted talk", "Reviewed talk", "Reviewed unslotted")));
    }

    @Test
    @WithMockUser(username = "test.organizer", roles = "ORGANIZER")
    @DisplayName("should_returnAllSessions_when_organizerReadsEventByCode")
    void should_returnAllSessions_when_organizerReadsEventByCode() throws Exception {
        mockMvc.perform(get("/api/v1/events/BATbern9060").param("include", "sessions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessions", hasSize(5)));
    }

    @Test
    @DisplayName("should_notServeOrganizerCacheEntry_when_publicReadsSameEventAfterOrganizer")
    void should_notServeOrganizerCacheEntry_when_publicReadsSameEventAfterOrganizer() throws Exception {
        mockMvc.perform(get("/api/v1/events/BATbern9060").param("include", "sessions")
                        .with(org.springframework.security.test.web.servlet.request
                                .SecurityMockMvcRequestPostProcessors.user("test.organizer").roles("ORGANIZER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessions", hasSize(5)));

        mockMvc.perform(get("/api/v1/events/BATbern9060").param("include", "sessions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessions", hasSize(3)));
    }

    @Test
    @WithMockUser(username = "some.speaker", roles = "SPEAKER")
    @DisplayName("should_filterSessions_when_speakerReadsEventByCode")
    void should_filterSessions_when_speakerReadsEventByCode() throws Exception {
        mockMvc.perform(get("/api/v1/events/BATbern9060").param("include", "sessions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessions", hasSize(3)));
    }

    @Test
    @DisplayName("should_notFilter_when_publicReadsCompletedEvent")
    void should_notFilter_when_publicReadsCompletedEvent() throws Exception {
        event.setWorkflowState(EventWorkflowState.ARCHIVED);
        eventRepository.save(event);

        mockMvc.perform(get("/api/v1/events/BATbern9060").param("include", "sessions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessions", hasSize(5)));
    }

    // ----------------------------------------------------------------------------------------
    // GET /events (list, used by the upcoming-events section)
    // ----------------------------------------------------------------------------------------

    @Test
    @DisplayName("should_filterSessions_when_publicListsEventsWithSessions")
    void should_filterSessions_when_publicListsEventsWithSessions() throws Exception {
        mockMvc.perform(get("/api/v1/events").param("include", "topics,sessions,speakers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].eventCode").value("BATbern9060"))
                .andExpect(jsonPath("$.data[0].sessions[*].title", containsInAnyOrder(
                        "Accepted talk", "Reviewed talk", "Reviewed unslotted")));
    }

    // ----------------------------------------------------------------------------------------
    // GET /events/{code}/sessions and /sessions/{slug}
    // ----------------------------------------------------------------------------------------

    @Test
    @DisplayName("should_filterSessions_when_publicListsSessionsOfEvent")
    void should_filterSessions_when_publicListsSessionsOfEvent() throws Exception {
        mockMvc.perform(get("/api/v1/events/BATbern9060/sessions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].title", containsInAnyOrder(
                        "Accepted talk", "Reviewed talk", "Reviewed unslotted")))
                .andExpect(jsonPath("$.pagination.totalItems").value(3));
    }

    @Test
    @WithMockUser(username = "test.organizer", roles = "ORGANIZER")
    @DisplayName("should_listAllSessions_when_organizerListsSessionsOfEvent")
    void should_listAllSessions_when_organizerListsSessionsOfEvent() throws Exception {
        mockMvc.perform(get("/api/v1/events/BATbern9060/sessions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(5)));
    }

    @Test
    @DisplayName("should_return404_when_publicReadsSessionNotYetVisible")
    void should_return404_when_publicReadsSessionNotYetVisible() throws Exception {
        mockMvc.perform(get("/api/v1/events/BATbern9060/sessions/invited-talk"))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/events/BATbern9060/sessions/accepted-talk"))
                .andExpect(status().isOk());
    }

    // ----------------------------------------------------------------------------------------
    // Fixtures
    // ----------------------------------------------------------------------------------------

    private void setPhase(String phase) {
        event.setCurrentPublishedPhase(phase);
        eventRepository.save(event);
    }

    private void speakerSession(String title, SpeakerWorkflowState state, boolean slotted) {
        Session session = session(title, "presentation", slotted);
        speakerPoolRepository.save(SpeakerPool.builder()
                .eventId(event.getId())
                .speakerName(title + " speaker")
                .status(state)
                .sessionId(session.getId())
                .build());
    }

    private Session session(String title, String type, boolean slotted) {
        return sessionRepository.save(Session.builder()
                .eventId(event.getId())
                .eventCode(event.getEventCode())
                .sessionSlug(title.toLowerCase().replace(' ', '-'))
                .title(title)
                .sessionType(type)
                .startTime(slotted ? EVENT_DATE : null)
                .endTime(slotted ? EVENT_DATE.plus(30, ChronoUnit.MINUTES) : null)
                .build());
    }
}
