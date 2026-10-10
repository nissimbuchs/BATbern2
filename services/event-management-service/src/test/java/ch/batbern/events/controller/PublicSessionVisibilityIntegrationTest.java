package ch.batbern.events.controller;

import ch.batbern.events.client.UserApiClient;
import ch.batbern.events.config.TestAwsConfig;
import ch.batbern.events.config.TestSecurityConfig;
import ch.batbern.events.core.dto.generated.EventType;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Session;
import ch.batbern.events.domain.SessionUser;
import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.events.dto.generated.users.UserResponse;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.LogoRepository;
import ch.batbern.events.repository.SessionRepository;
import ch.batbern.events.repository.SessionUserRepository;
import ch.batbern.events.repository.SpeakerPoolRepository;
import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.shared.types.EventWorkflowState;
import ch.batbern.shared.types.SpeakerWorkflowState;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * What the public website sees of an event, and who may see more.
 *
 * <p>Public read model ({@code GET /public/events/*}, 2026-10-07): sessions and speakers are
 * shaped by the publishing phase only, whoever asks. A logged-in organizer sees on the website
 * exactly what an anonymous visitor sees (bug: READY speakers on www.batbern.ch for organizers).
 *
 * <p>Organizer endpoints ({@code /events/{code}}, {@code /events}): sessions and speakers only for
 * organizers. {@code /events/current} is kept unchanged for the Apple Watch app (#1070).
 *
 * <p>Fixture: one upcoming event in the SPEAKERS phase with five sessions, all slotted except
 * "Reviewed unslotted":
 * <ul>
 *   <li>"Invited talk": invited.talk, INVITED</li>
 *   <li>"Accepted talk": accepted.talk, ACCEPTED</li>
 *   <li>"Reviewed talk": reviewed.talk, QUALITY_REVIEWED</li>
 *   <li>"Reviewed unslotted": reviewed.unslotted, QUALITY_REVIEWED, no slot</li>
 *   <li>"Lunch": structural, no speaker</li>
 * </ul>
 */
@Transactional
@Import({TestSecurityConfig.class, TestAwsConfig.class})
class PublicSessionVisibilityIntegrationTest extends AbstractIntegrationTest {

    private static final Instant EVENT_DATE = Instant.now().plus(30, ChronoUnit.DAYS)
            .truncatedTo(ChronoUnit.DAYS);
    private static final String CODE = "BATbern9060";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private SessionUserRepository sessionUserRepository;

    @Autowired
    private SpeakerPoolRepository speakerPoolRepository;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private EntityManager entityManager;

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
        when(userApiClient.getUserByUsername(anyString())).thenAnswer(inv -> {
            String username = inv.getArgument(0);
            return new UserResponse().id(username).firstName("First " + username).lastName("Last");
        });

        event = saveEvent(CODE, 9060, EVENT_DATE, EventWorkflowState.SLOT_ASSIGNMENT, "speakers");
        speakerSession(event, "Invited talk", "invited.talk", SpeakerWorkflowState.INVITED, true);
        speakerSession(event, "Accepted talk", "accepted.talk", SpeakerWorkflowState.ACCEPTED, true);
        speakerSession(event, "Reviewed talk", "reviewed.talk", SpeakerWorkflowState.QUALITY_REVIEWED, true);
        speakerSession(event, "Reviewed unslotted", "reviewed.unslotted",
                SpeakerWorkflowState.QUALITY_REVIEWED, false);
        session(event, "Lunch", "lunch", true);
        // Detach everything: the batch query (findByEventIdInWithSpeakers) must load the
        // session_users from the database, not the cached Session instances whose collection the
        // fixture never filled.
        entityManager.flush();
        entityManager.clear();
    }

    // ============================================================================================
    // Public read model: shaped by phase, identical for every caller
    // ============================================================================================

    @Nested
    @DisplayName("GET /public/events/current")
    class PublicCurrent {

        @Test
        @DisplayName("should_showAcceptedSpeakersWithoutSessionsOrTitles_when_speakersPhase")
        void should_showAcceptedSpeakersWithoutSessionsOrTitles_when_speakersPhase() throws Exception {
            mockMvc.perform(get("/api/v1/public/events/current"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.eventCode").value(CODE))
                    .andExpect(jsonPath("$.sessions", hasSize(0)))
                    .andExpect(jsonPath("$.speakers[*].username", containsInAnyOrder(
                            "accepted.talk", "reviewed.talk", "reviewed.unslotted")))
                    .andExpect(jsonPath("$.speakers[0].talkTitle").doesNotExist())
                    .andExpect(jsonPath("$.speakers[0].sessionSlug").doesNotExist());
        }

        @Test
        @WithMockUser(username = "test.organizer", roles = "ORGANIZER")
        @DisplayName("should_showOrganizerTheSamePublicView_when_loggedInOrganizerVisitsWebsite")
        void should_showOrganizerTheSamePublicView_when_loggedInOrganizerVisitsWebsite() throws Exception {
            mockMvc.perform(get("/api/v1/public/events/current"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.sessions", hasSize(0)))
                    .andExpect(jsonPath("$.speakers[*].username", containsInAnyOrder(
                            "accepted.talk", "reviewed.talk", "reviewed.unslotted")));
        }

        @Test
        @DisplayName("should_showSlottedReviewedSessionsWithTitles_when_agendaPhase")
        void should_showSlottedReviewedSessionsWithTitles_when_agendaPhase() throws Exception {
            setPhase("agenda");

            mockMvc.perform(get("/api/v1/public/events/current"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.sessions[*].title", containsInAnyOrder("Reviewed talk", "Lunch")))
                    .andExpect(jsonPath("$.speakers[*].username", containsInAnyOrder("reviewed.talk")))
                    .andExpect(jsonPath("$.speakers[0].talkTitle").value("Reviewed talk"))
                    .andExpect(jsonPath("$.speakers[0].sessionSlug").value("reviewed-talk"));
        }

        @Test
        @DisplayName("should_showNoSessionsOrSpeakers_when_topicPhase")
        void should_showNoSessionsOrSpeakers_when_topicPhase() throws Exception {
            setPhase("topic");

            mockMvc.perform(get("/api/v1/public/events/current"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.sessions", hasSize(0)))
                    .andExpect(jsonPath("$.speakers", hasSize(0)));
        }
    }

    @Nested
    @DisplayName("GET /public/events/{code}")
    class PublicByCode {

        @Test
        @WithMockUser(username = "test.organizer", roles = "ORGANIZER")
        @DisplayName("should_return404_when_eventNotPublished")
        void should_return404_when_eventNotPublished() throws Exception {
            setPhase(null);

            mockMvc.perform(get("/api/v1/public/events/" + CODE)).andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("should_shapeByPhase_when_publishedEventRequested")
        void should_shapeByPhase_when_publishedEventRequested() throws Exception {
            mockMvc.perform(get("/api/v1/public/events/" + CODE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.sessions", hasSize(0)))
                    .andExpect(jsonPath("$.speakers", hasSize(3)));
        }

        @Test
        @DisplayName("should_showEverything_when_eventArchived")
        void should_showEverything_when_eventArchived() throws Exception {
            event.setWorkflowState(EventWorkflowState.ARCHIVED);
            eventRepository.save(event);

            mockMvc.perform(get("/api/v1/public/events/" + CODE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.sessions", hasSize(5)))
                    .andExpect(jsonPath("$.speakers", hasSize(4)))
                    .andExpect(jsonPath("$.speakers[?(@.username == 'invited.talk')].talkTitle")
                            .value("Invited talk"));
        }
    }

    @Nested
    @DisplayName("GET /public/events")
    class PublicList {

        @Test
        @DisplayName("should_listOnlyPublishedUpcomingEvents_shapedByPhase_when_scopeUpcoming")
        void should_listOnlyPublishedUpcomingEvents_shapedByPhase_when_scopeUpcoming() throws Exception {
            saveEvent("BATbern9061", 9061, EVENT_DATE.plus(90, ChronoUnit.DAYS),
                    EventWorkflowState.CREATED, null);
            saveEvent("BATbern9062", 9062, Instant.now().minus(200, ChronoUnit.DAYS),
                    EventWorkflowState.ARCHIVED, "agenda");

            mockMvc.perform(get("/api/v1/public/events").param("scope", "upcoming"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[*].eventCode", containsInAnyOrder(CODE)))
                    .andExpect(jsonPath("$.data[0].sessions", hasSize(0)))
                    .andExpect(jsonPath("$.data[0].speakers", hasSize(3)));
        }

        @Test
        @DisplayName("should_listOnlyArchivedEvents_when_scopeArchive")
        void should_listOnlyArchivedEvents_when_scopeArchive() throws Exception {
            saveEvent("BATbern9062", 9062, Instant.now().minus(200, ChronoUnit.DAYS),
                    EventWorkflowState.ARCHIVED, "agenda");

            mockMvc.perform(get("/api/v1/public/events").param("scope", "archive"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[*].eventCode", containsInAnyOrder("BATbern9062")));
        }

        @Test
        @DisplayName("should_narrowArchiveByTitle_when_searchGiven")
        void should_narrowArchiveByTitle_when_searchGiven() throws Exception {
            Event a = saveEvent("BATbern9062", 9062, Instant.now().minus(200, ChronoUnit.DAYS),
                    EventWorkflowState.ARCHIVED, "agenda");
            a.setTitle("Quantum Architecture");
            eventRepository.save(a);
            saveEvent("BATbern9063", 9063, Instant.now().minus(300, ChronoUnit.DAYS),
                    EventWorkflowState.ARCHIVED, "agenda");

            mockMvc.perform(get("/api/v1/public/events").param("scope", "archive").param("search", "Quantum"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[*].eventCode", containsInAnyOrder("BATbern9062")));
        }

        @Test
        @DisplayName("should_return400_when_scopeUnknown")
        void should_return400_when_scopeUnknown() throws Exception {
            mockMvc.perform(get("/api/v1/public/events").param("scope", "drafts"))
                    .andExpect(status().isBadRequest());
        }
    }

    // ============================================================================================
    // Organizer endpoints: sessions/speakers only for organizers
    // ============================================================================================

    @Nested
    @DisplayName("GET /events/{code} and /events (organizer UI)")
    class OrganizerEndpoints {

        @Test
        @DisplayName("should_returnNoSessions_when_anonymousReadsEventByCode")
        void should_returnNoSessions_when_anonymousReadsEventByCode() throws Exception {
            mockMvc.perform(get("/api/v1/events/" + CODE).param("include", "sessions"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.eventCode").value(CODE))
                    .andExpect(jsonPath("$.sessions").doesNotExist());
        }

        @Test
        @WithMockUser(username = "some.speaker", roles = "SPEAKER")
        @DisplayName("should_returnNoSessions_when_speakerReadsEventByCode")
        void should_returnNoSessions_when_speakerReadsEventByCode() throws Exception {
            mockMvc.perform(get("/api/v1/events/" + CODE).param("include", "sessions"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.sessions").doesNotExist());
        }

        @Test
        @WithMockUser(username = "test.organizer", roles = "ORGANIZER")
        @DisplayName("should_returnAllSessions_when_organizerReadsEventByCode")
        void should_returnAllSessions_when_organizerReadsEventByCode() throws Exception {
            mockMvc.perform(get("/api/v1/events/" + CODE).param("include", "sessions"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.sessions", hasSize(5)));
        }

        @Test
        @DisplayName("should_notServeOrganizerCacheEntry_when_anonymousReadsSameEventAfterOrganizer")
        void should_notServeOrganizerCacheEntry_when_anonymousReadsSameEventAfterOrganizer() throws Exception {
            mockMvc.perform(get("/api/v1/events/" + CODE).param("include", "sessions")
                            .with(user("test.organizer").roles("ORGANIZER")))
                    .andExpect(jsonPath("$.sessions", hasSize(5)));

            mockMvc.perform(get("/api/v1/events/" + CODE).param("include", "sessions"))
                    .andExpect(jsonPath("$.sessions").doesNotExist());
        }

        @Test
        @DisplayName("should_returnNoSessions_when_anonymousListsEvents")
        void should_returnNoSessions_when_anonymousListsEvents() throws Exception {
            mockMvc.perform(get("/api/v1/events").param("include", "topics,sessions,speakers"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].eventCode").value(CODE))
                    .andExpect(jsonPath("$.data[0].sessions").doesNotExist());
        }

        @Test
        @WithMockUser(username = "test.organizer", roles = "ORGANIZER")
        @DisplayName("should_returnAllSessions_when_organizerListsEvents")
        void should_returnAllSessions_when_organizerListsEvents() throws Exception {
            mockMvc.perform(get("/api/v1/events").param("include", "sessions"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].sessions", hasSize(5)));
        }
    }

    @Nested
    @DisplayName("GET /events/current (Apple Watch only, #1070)")
    class WatchCurrent {

        @Test
        @WithMockUser(username = "test.organizer", roles = "ORGANIZER")
        @DisplayName("should_returnAllSessions_when_watchOrganizerZoneReads")
        void should_returnAllSessions_when_watchOrganizerZoneReads() throws Exception {
            mockMvc.perform(get("/api/v1/events/current").param("include", "topics,venue,sessions"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.sessions", hasSize(5)));
        }

        @Test
        @DisplayName("should_returnPublicView_when_watchPublicZoneReads")
        void should_returnPublicView_when_watchPublicZoneReads() throws Exception {
            mockMvc.perform(get("/api/v1/events/current").param("include", "topics,venue,sessions"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.sessions[*].title", containsInAnyOrder(
                            "Accepted talk", "Reviewed talk", "Reviewed unslotted")));
        }
    }

    @Nested
    @DisplayName("GET /events/{code}/sessions and /sessions/{slug}")
    class SessionEndpoints {

        @Test
        @DisplayName("should_filterSessions_when_publicListsSessionsOfEvent")
        void should_filterSessions_when_publicListsSessionsOfEvent() throws Exception {
            mockMvc.perform(get("/api/v1/events/" + CODE + "/sessions"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[*].title", containsInAnyOrder(
                            "Accepted talk", "Reviewed talk", "Reviewed unslotted")))
                    .andExpect(jsonPath("$.pagination.totalItems").value(3));
        }

        @Test
        @WithMockUser(username = "test.organizer", roles = "ORGANIZER")
        @DisplayName("should_listAllSessions_when_organizerListsSessionsOfEvent")
        void should_listAllSessions_when_organizerListsSessionsOfEvent() throws Exception {
            mockMvc.perform(get("/api/v1/events/" + CODE + "/sessions"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data", hasSize(5)));
        }

        @Test
        @DisplayName("should_return404_when_publicReadsSessionNotYetVisible")
        void should_return404_when_publicReadsSessionNotYetVisible() throws Exception {
            mockMvc.perform(get("/api/v1/events/" + CODE + "/sessions/invited-talk"))
                    .andExpect(status().isNotFound());

            mockMvc.perform(get("/api/v1/events/" + CODE + "/sessions/accepted-talk"))
                    .andExpect(status().isOk());
        }
    }

    // ============================================================================================
    // Fixtures
    // ============================================================================================

    private void setPhase(String phase) {
        event.setCurrentPublishedPhase(phase);
        eventRepository.save(event);
    }

    private Event saveEvent(String code, int number, Instant date, EventWorkflowState state, String phase) {
        return eventRepository.save(Event.builder()
                .eventCode(code)
                .title("Visibility Test Event " + number)
                .eventNumber(number)
                .date(date)
                .registrationDeadline(date.minus(7, ChronoUnit.DAYS))
                .venueName("Kornhausforum")
                .venueAddress("Kornhausplatz 18, 3011 Bern")
                .venueCapacity(200)
                .organizerUsername("test.organizer")
                .currentAttendeeCount(0)
                .eventType(EventType.EVENING)
                .workflowState(state)
                .currentPublishedPhase(phase)
                .build());
    }

    private void speakerSession(Event e, String title, String username, SpeakerWorkflowState state,
                                boolean slotted) {
        Session session = session(e, title, "presentation", slotted);
        sessionUserRepository.save(SessionUser.builder()
                .session(session)
                .username(username)
                .speakerRole(SessionUser.SpeakerRole.PRIMARY_SPEAKER)
                .isConfirmed(state.ordinal() >= SpeakerWorkflowState.ACCEPTED.ordinal())
                .build());
        speakerPoolRepository.save(SpeakerPool.builder()
                .eventId(e.getId())
                .speakerName(title + " speaker")
                .status(state)
                .sessionId(session.getId())
                .build());
    }

    private Session session(Event e, String title, String type, boolean slotted) {
        return sessionRepository.save(Session.builder()
                .eventId(e.getId())
                .eventCode(e.getEventCode())
                .sessionSlug(title.toLowerCase().replace(' ', '-'))
                .title(title)
                .sessionType(type)
                .startTime(slotted ? e.getDate() : null)
                .endTime(slotted ? e.getDate().plus(30, ChronoUnit.MINUTES) : null)
                .build());
    }
}
