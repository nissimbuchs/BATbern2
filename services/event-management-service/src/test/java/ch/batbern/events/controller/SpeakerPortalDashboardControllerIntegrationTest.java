package ch.batbern.events.controller;

import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Session;
import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.events.dto.generated.EventType;
import ch.batbern.events.repository.SessionContentHistoryRepository;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.SessionMaterialsRepository;
import ch.batbern.events.repository.SessionRepository;
import ch.batbern.events.repository.SpeakerInvitationTokenRepository;
import ch.batbern.events.repository.SpeakerPoolRepository;
import ch.batbern.events.service.MagicLinkService;
import ch.batbern.shared.types.EventWorkflowState;
import ch.batbern.shared.types.SpeakerWorkflowState;
import ch.batbern.shared.types.TokenAction;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for SpeakerPortalDashboardController.
 * Story 6.4: Speaker Dashboard (View-Only) - AC1-AC5
 *
 * Tests run against real PostgreSQL via Testcontainers.
 * UserApiClient is mocked via TestUserApiClientConfig.
 */
@Transactional
@org.junit.jupiter.api.Disabled(
        "Story 11.E.3 (ADR-009 §Decision 3): magic-link flow replaced by Cognito Bearer + "
                + "@PreAuthorize(\"hasRole('SPEAKER')\"). GET /api/v1/speaker-portal/dashboard "
                + "now takes no token query parameter and reads the username from the Cognito "
                + "JWT. Cognito-side coverage lives in SpeakerPortalAuthIntegrationTest "
                + "(Task 10). Phase F (Story 11.F.1) deletes this file.")
class SpeakerPortalDashboardControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MagicLinkService magicLinkService;

    @Autowired
    private SpeakerInvitationTokenRepository tokenRepository;

    @Autowired
    private SpeakerPoolRepository speakerPoolRepository;

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private ch.batbern.events.repository.SessionUserRepository sessionUserRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private SessionContentHistoryRepository sessionContentHistoryRepository;

    @Autowired
    private SessionMaterialsRepository sessionMaterialsRepository;

    private String validToken;
    private UUID testEventId;
    private UUID testSessionId;
    private UUID testSpeakerPoolId;
    private String testUsername = "dashboard.speaker";

    @BeforeEach
    void setUp() {
        // Clean up in FK dependency order
        sessionContentHistoryRepository.deleteAll();
        sessionMaterialsRepository.deleteAll();
        tokenRepository.deleteAll();
        sessionUserRepository.deleteAll();
        speakerPoolRepository.deleteAll();
        sessionRepository.deleteAll();
        eventRepository.deleteAll();

        long uniqueNumber = System.currentTimeMillis() % 100000;

        // Create a future event (upcoming)
        Event futureEvent = Event.builder()
                .eventCode("bat-bern-2026-dash-" + uniqueNumber)
                .eventNumber((int) uniqueNumber)
                .title("BATbern Dashboard Test 2026")
                .date(Instant.now().plus(30, ChronoUnit.DAYS))
                .registrationDeadline(Instant.now().plus(25, ChronoUnit.DAYS))
                .venueName("Test Venue Bern")
                .venueAddress("Teststrasse 1, 3000 Bern")
                .venueCapacity(100)
                .organizerUsername("organizer.test")
                .eventType(EventType.FULL_DAY)
                .workflowState(EventWorkflowState.SPEAKER_IDENTIFICATION)
                .build();
        futureEvent = eventRepository.save(futureEvent);
        testEventId = futureEvent.getId();

        // Create a session for the future event
        Session session = Session.builder()
                .eventId(testEventId)
                .eventCode(futureEvent.getEventCode())
                .sessionSlug("cloud-architecture-dash-" + uniqueNumber)
                .title("Cloud Architecture Best Practices")
                .description("A deep dive into cloud architecture")
                .sessionType("presentation")
                .build();
        session = sessionRepository.save(session);
        testSessionId = session.getId();

        // Create speaker pool entry (ACCEPTED state, upcoming event)
        SpeakerPool speaker = SpeakerPool.builder()
                .eventId(testEventId)
                .sessionId(testSessionId)
                .speakerName("Dashboard Speaker")
                .company("Test Corp")
                .status(SpeakerWorkflowState.ACCEPTED)
                .responseDeadline(LocalDate.now().plusDays(7))
                .contentDeadline(LocalDate.now().plusDays(20))
                .build();
        speaker = speakerPoolRepository.save(speaker);
        testSpeakerPoolId = speaker.getId();

        // 2026-05-21 (BATbern75 follow-up) — dashboard's canonical data source is now
        // session_users (not speaker_pool.username). Mirror the production fixture
        // shape from Story 11.E.8 provisionSessionAndPrimarySpeaker: session.speakerPoolId
        // back-reference + a PRIMARY_SPEAKER session_users row.
        session.setSpeakerPoolId(testSpeakerPoolId);
        sessionRepository.save(session);
        ch.batbern.events.domain.SessionUser membership =
                ch.batbern.events.domain.SessionUser.builder()
                        .session(session)
                        .username(testUsername)
                        .speakerRole(ch.batbern.events.domain.SessionUser.SpeakerRole.PRIMARY_SPEAKER)
                        .isConfirmed(true)
                        .build();
        sessionUserRepository.save(membership);

        // Generate VIEW token for dashboard
        validToken = magicLinkService.generateToken(testSpeakerPoolId, TokenAction.VIEW);
    }

    @Nested
    @DisplayName("AC1: Dashboard Access via Magic Link")
    class DashboardAccessTests {

        @Test
        @DisplayName("should return dashboard for valid token")
        void should_returnDashboard_when_validToken() throws Exception {
            mockMvc.perform(get("/api/v1/speaker-portal/dashboard")
                            .param("token", validToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.speakerName", is("Dashboard Speaker")))
                    .andExpect(jsonPath("$.upcomingEvents", notNullValue()))
                    .andExpect(jsonPath("$.pastEvents", notNullValue()));
        }

        @Test
        @DisplayName("should return 400 when token is missing")
        void should_return400_when_tokenMissing() throws Exception {
            mockMvc.perform(get("/api/v1/speaker-portal/dashboard"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 when token is blank")
        void should_return400_when_tokenBlank() throws Exception {
            mockMvc.perform(get("/api/v1/speaker-portal/dashboard")
                            .param("token", ""))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 when token is invalid")
        void should_return400_when_tokenInvalid() throws Exception {
            mockMvc.perform(get("/api/v1/speaker-portal/dashboard")
                            .param("token", "invalid-token-value"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("AC2: Upcoming Events View")
    class UpcomingEventsTests {

        @Test
        @DisplayName("should show upcoming event with correct details")
        void should_showUpcomingEvent_withCorrectDetails() throws Exception {
            mockMvc.perform(get("/api/v1/speaker-portal/dashboard")
                            .param("token", validToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.upcomingEvents", hasSize(1)))
                    .andExpect(jsonPath("$.upcomingEvents[0].eventTitle", is("BATbern Dashboard Test 2026")))
                    .andExpect(jsonPath("$.upcomingEvents[0].eventLocation", is("Test Venue Bern")))
                    .andExpect(jsonPath("$.upcomingEvents[0].sessionTitle", is("Cloud Architecture Best Practices")))
                    .andExpect(jsonPath("$.upcomingEvents[0].workflowState", is("ACCEPTED")))
                    .andExpect(jsonPath("$.upcomingEvents[0].workflowStateLabel", is("Accepted")))
                    // 2026-05-20 (Q#D) — contentStatus / contentStatusLabel removed from
                    // the dashboard DTO end-to-end. The fields are no longer serialised
                    // (JsonInclude.NON_NULL on the record + record fields absent).
                    .andExpect(jsonPath("$.upcomingEvents[0].contentStatus").doesNotExist())
                    .andExpect(jsonPath("$.upcomingEvents[0].contentStatusLabel").doesNotExist());
        }

        @Test
        @DisplayName("should include INVITED speakers in upcoming events")
        void should_includeInvitedSpeakers() throws Exception {
            // Update speaker to INVITED status
            SpeakerPool speaker = speakerPoolRepository.findById(testSpeakerPoolId).orElseThrow();
            speaker.setStatus(SpeakerWorkflowState.INVITED);
            speakerPoolRepository.save(speaker);

            mockMvc.perform(get("/api/v1/speaker-portal/dashboard")
                            .param("token", validToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.upcomingEvents", hasSize(1)))
                    .andExpect(jsonPath("$.upcomingEvents[0].workflowState", is("INVITED")))
                    .andExpect(jsonPath("$.upcomingEvents[0].workflowStateLabel", is("Invitation Pending")))
                    .andExpect(jsonPath("$.upcomingEvents[0].respondUrl", is("/speaker-portal/respond")));
        }

        @Test
        @DisplayName("should include QUALITY_REVIEWED speakers in upcoming events")
        void should_includeQualityReviewedSpeakers() throws Exception {
            SpeakerPool speaker = speakerPoolRepository.findById(testSpeakerPoolId).orElseThrow();
            speaker.setStatus(SpeakerWorkflowState.QUALITY_REVIEWED);
            speakerPoolRepository.save(speaker);

            mockMvc.perform(get("/api/v1/speaker-portal/dashboard")
                            .param("token", validToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.upcomingEvents", hasSize(1)))
                    .andExpect(jsonPath("$.upcomingEvents[0].workflowState", is("QUALITY_REVIEWED")))
                    .andExpect(jsonPath("$.upcomingEvents[0].workflowStateLabel", is("Quality Reviewed")));
        }

        @Test
        @DisplayName("should show content deadlines")
        void should_showContentDeadlines() throws Exception {
            mockMvc.perform(get("/api/v1/speaker-portal/dashboard")
                            .param("token", validToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.upcomingEvents[0].responseDeadline", notNullValue()))
                    .andExpect(jsonPath("$.upcomingEvents[0].contentDeadline", notNullValue()));
        }

        @Test
        @DisplayName("should provide profile and content URLs")
        void should_provideQuickActionUrls() throws Exception {
            mockMvc.perform(get("/api/v1/speaker-portal/dashboard")
                            .param("token", validToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.upcomingEvents[0].profileUrl", is("/speaker-portal/profile")))
                    .andExpect(jsonPath("$.upcomingEvents[0].contentUrl", is("/speaker-portal/content")));
        }

        @Test
        @DisplayName("should show contentUrl for ACCEPTED speaker without session")
        void should_showContentUrl_forAcceptedSpeakerWithoutSession() throws Exception {
            // Remove session assignment - this was the original bug
            SpeakerPool speaker = speakerPoolRepository
                    .findById(testSpeakerPoolId).orElseThrow();
            speaker.setSessionId(null);
            speakerPoolRepository.save(speaker);

            mockMvc.perform(get("/api/v1/speaker-portal/dashboard")
                            .param("token", validToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.upcomingEvents[0].contentUrl",
                            is("/speaker-portal/content")));
        }

        @Test
        @DisplayName("should NOT show contentUrl for INVITED speaker")
        void should_notShowContentUrl_forInvitedSpeaker() throws Exception {
            SpeakerPool speaker = speakerPoolRepository
                    .findById(testSpeakerPoolId).orElseThrow();
            speaker.setStatus(SpeakerWorkflowState.INVITED);
            speakerPoolRepository.save(speaker);

            mockMvc.perform(get("/api/v1/speaker-portal/dashboard")
                            .param("token", validToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.upcomingEvents[0].contentUrl")
                            .doesNotExist())
                    .andExpect(jsonPath("$.upcomingEvents[0].respondUrl",
                            is("/speaker-portal/respond")));
        }

        @Test
        @DisplayName("should NOT show upcoming events for DECLINED speaker")
        void should_notShowUpcomingEvents_forDeclinedSpeaker() throws Exception {
            SpeakerPool speaker = speakerPoolRepository
                    .findById(testSpeakerPoolId).orElseThrow();
            speaker.setStatus(SpeakerWorkflowState.DECLINED);
            speakerPoolRepository.save(speaker);

            // DECLINED is not in UPCOMING_STATES, so no upcoming events
            mockMvc.perform(get("/api/v1/speaker-portal/dashboard")
                            .param("token", validToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.upcomingEvents", hasSize(0)));
        }

        @Test
        @DisplayName("should sort upcoming events by date ascending")
        void should_sortUpcomingEventsByDateAscending() throws Exception {
            long uniqueNumber2 = (System.currentTimeMillis() + 1) % 100000;

            // Create a second future event (sooner)
            Event soonerEvent = Event.builder()
                    .eventCode("bat-bern-2026-sooner-" + uniqueNumber2)
                    .eventNumber((int) uniqueNumber2)
                    .title("BATbern Sooner Event")
                    .date(Instant.now().plus(10, ChronoUnit.DAYS))
                    .registrationDeadline(Instant.now().plus(8, ChronoUnit.DAYS))
                    .venueName("Sooner Venue")
                    .venueAddress("Soonerstrasse 1, 3000 Bern")
                    .venueCapacity(50)
                    .organizerUsername("organizer.test")
                    .eventType(EventType.FULL_DAY)
                    .workflowState(EventWorkflowState.SPEAKER_IDENTIFICATION)
                    .build();
            soonerEvent = eventRepository.save(soonerEvent);

            SpeakerPool soonerSpeaker = SpeakerPool.builder()
                    .eventId(soonerEvent.getId())
                    .speakerName("Dashboard Speaker")
                    .status(SpeakerWorkflowState.QUALITY_REVIEWED)
                    .build();
            speakerPoolRepository.save(soonerSpeaker);

            mockMvc.perform(get("/api/v1/speaker-portal/dashboard")
                            .param("token", validToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.upcomingEvents", hasSize(2)))
                    // Sooner event should be first
                    .andExpect(jsonPath("$.upcomingEvents[0].eventTitle", is("BATbern Sooner Event")))
                    .andExpect(jsonPath("$.upcomingEvents[1].eventTitle", is("BATbern Dashboard Test 2026")));
        }
    }

    @Nested
    @DisplayName("AC3: Past Events View")
    class PastEventsTests {

        @Test
        @DisplayName("should show past events for confirmed speakers")
        void should_showPastEvents_forConfirmedSpeakers() throws Exception {
            long uniqueNumber2 = (System.currentTimeMillis() + 2) % 100000;

            // Create a past event
            Event pastEvent = Event.builder()
                    .eventCode("bat-bern-2025-past-" + uniqueNumber2)
                    .eventNumber((int) uniqueNumber2)
                    .title("BATbern Past Event 2025")
                    .date(Instant.now().minus(60, ChronoUnit.DAYS))
                    .registrationDeadline(Instant.now().minus(65, ChronoUnit.DAYS))
                    .venueName("Past Venue")
                    .venueAddress("Paststrasse 1, 3000 Bern")
                    .venueCapacity(100)
                    .organizerUsername("organizer.test")
                    .eventType(EventType.FULL_DAY)
                    .workflowState(EventWorkflowState.EVENT_COMPLETED)
                    .build();
            pastEvent = eventRepository.save(pastEvent);

            Session pastSession = Session.builder()
                    .eventId(pastEvent.getId())
                    .eventCode(pastEvent.getEventCode())
                    .sessionSlug("past-session-" + uniqueNumber2)
                    .title("Past Architecture Talk")
                    .sessionType("presentation")
                    .build();
            pastSession = sessionRepository.save(pastSession);

            SpeakerPool pastSpeaker = SpeakerPool.builder()
                    .eventId(pastEvent.getId())
                    .sessionId(pastSession.getId())
                    .speakerName("Dashboard Speaker")
                    .status(SpeakerWorkflowState.QUALITY_REVIEWED)
                    .build();
            speakerPoolRepository.save(pastSpeaker);

            mockMvc.perform(get("/api/v1/speaker-portal/dashboard")
                            .param("token", validToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.pastEvents", hasSize(1)))
                    .andExpect(jsonPath("$.pastEvents[0].eventTitle", is("BATbern Past Event 2025")))
                    .andExpect(jsonPath("$.pastEvents[0].sessionTitle", is("Past Architecture Talk")));
        }

        @Test
        @DisplayName("should not show past events for INVITED speakers")
        void should_notShowPastEvents_forInvitedSpeakers() throws Exception {
            long uniqueNumber2 = (System.currentTimeMillis() + 3) % 100000;

            // Create a past event with INVITED speaker (should not appear)
            Event pastEvent = Event.builder()
                    .eventCode("bat-bern-2025-invited-" + uniqueNumber2)
                    .eventNumber((int) uniqueNumber2)
                    .title("BATbern Past Invited Event")
                    .date(Instant.now().minus(30, ChronoUnit.DAYS))
                    .registrationDeadline(Instant.now().minus(35, ChronoUnit.DAYS))
                    .venueName("Past Venue 2")
                    .venueAddress("Paststrasse 2, 3000 Bern")
                    .venueCapacity(50)
                    .organizerUsername("organizer.test")
                    .eventType(EventType.FULL_DAY)
                    .workflowState(EventWorkflowState.EVENT_COMPLETED)
                    .build();
            pastEvent = eventRepository.save(pastEvent);

            SpeakerPool pastInvitedSpeaker = SpeakerPool.builder()
                    .eventId(pastEvent.getId())
                    .speakerName("Dashboard Speaker")
                    .status(SpeakerWorkflowState.INVITED)
                    .build();
            speakerPoolRepository.save(pastInvitedSpeaker);

            mockMvc.perform(get("/api/v1/speaker-portal/dashboard")
                            .param("token", validToken))
                    .andExpect(status().isOk())
                    // Past events should not include INVITED speakers
                    .andExpect(jsonPath("$.pastEvents", hasSize(0)));
        }
    }

    @Nested
    @DisplayName("AC5: Organizer Contact Information")
    class OrganizerContactTests {

        @Test
        @DisplayName("should include organizer name and email")
        void should_includeOrganizerContact() throws Exception {
            // TestUserApiClientConfig returns "Test User" for any username
            mockMvc.perform(get("/api/v1/speaker-portal/dashboard")
                            .param("token", validToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.upcomingEvents[0].organizerName", is("Test User")))
                    .andExpect(jsonPath("$.upcomingEvents[0].organizerEmail", is("organizer.test@test.com")));
        }
    }

    @Nested
    @DisplayName("AC8: Empty & Loading States")
    class EmptyStateTests {

        @Test
        @DisplayName("should return empty lists when speaker has no matching events")
        void should_returnEmptyLists_when_noMatchingEvents() throws Exception {
            // Delete all speaker pool entries and create one with DECLINED status
            speakerPoolRepository.deleteAll();

            SpeakerPool declinedSpeaker = SpeakerPool.builder()
                    .eventId(testEventId)
                    .speakerName("Dashboard Speaker")
                    .status(SpeakerWorkflowState.DECLINED)
                    .build();
            declinedSpeaker = speakerPoolRepository.save(declinedSpeaker);

            // Re-generate token for new speaker pool entry
            String newToken = magicLinkService.generateToken(declinedSpeaker.getId(), TokenAction.VIEW);

            mockMvc.perform(get("/api/v1/speaker-portal/dashboard")
                            .param("token", newToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.upcomingEvents", hasSize(0)))
                    .andExpect(jsonPath("$.pastEvents", hasSize(0)));
        }
    }

    @Nested
    @DisplayName("Profile Information")
    class ProfileInfoTests {

        @Test
        @DisplayName("should include profile completeness from user service")
        void should_includeProfileCompleteness() throws Exception {
            mockMvc.perform(get("/api/v1/speaker-portal/dashboard")
                            .param("token", validToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.profileCompleteness").isNumber())
                    .andExpect(jsonPath("$.profilePictureUrl", notNullValue()));
        }
    }
}
