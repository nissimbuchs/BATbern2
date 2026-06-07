package ch.batbern.events.service;

import ch.batbern.events.client.UserApiClient;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Session;
import ch.batbern.events.domain.SessionMaterial;
import ch.batbern.events.domain.SessionUser;
import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.events.dto.SpeakerPoolResponse;
import ch.batbern.events.dto.generated.users.UserResponse;
import ch.batbern.events.repository.SessionContentHistoryRepository;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.SessionMaterialsRepository;
import ch.batbern.events.repository.SessionRepository;
import ch.batbern.events.repository.SessionUserRepository;
import ch.batbern.events.repository.SpeakerPoolRepository;
import ch.batbern.events.security.SecurityContextHelper;
import ch.batbern.shared.types.SpeakerWorkflowState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit tests for SpeakerPoolService.
 * Regression tests for material enrichment in speaker pool responses.
 */
@ExtendWith(MockitoExtension.class)
class SpeakerPoolServiceTest {

    @Mock
    private SpeakerPoolRepository speakerPoolRepository;

    @Mock
    private EventRepository eventRepository;

    @Mock
    private SessionContentHistoryRepository sessionContentHistoryRepository;

    @Mock
    private SessionRepository sessionRepository;

    @Mock
    private SessionMaterialsRepository sessionMaterialsRepository;

    @Mock
    private SessionUserRepository sessionUserRepository;

    @Mock
    private UserApiClient userApiClient;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private SecurityContextHelper securityContextHelper;

    @Mock
    private PrimarySpeakerResolver primarySpeakerResolver;

    private SpeakerPoolService service;

    private Event testEvent;
    private UUID eventId;

    @BeforeEach
    void setUp() {
        service = new SpeakerPoolService(
                speakerPoolRepository, eventRepository, sessionContentHistoryRepository,
                sessionRepository, sessionMaterialsRepository, sessionUserRepository,
                userApiClient, eventPublisher, securityContextHelper,
                primarySpeakerResolver
        );

        eventId = UUID.randomUUID();
        testEvent = Event.builder()
                .id(eventId)
                .eventCode("BATbern99")
                .title("Test Event")
                .date(Instant.now().plusSeconds(86400 * 30))
                .build();
    }

    @Nested
    @DisplayName("getSpeakerPool - material enrichment")
    class GetSpeakerPoolMaterialTests {

        @Test
        @DisplayName("should include material info when speaker has session with materials")
        void shouldIncludeMaterialInfo_whenSessionHasMaterials() {
            UUID speakerId = UUID.randomUUID();
            UUID sessionId = UUID.randomUUID();

            SpeakerPool speaker = SpeakerPool.builder()
                    .id(speakerId)
                    .eventId(eventId)
                    .speakerName("Speaker One")
                    .status(SpeakerWorkflowState.CONTENT_SUBMITTED)
                    .sessionId(sessionId)
                    .build();

            SessionMaterial material = SessionMaterial.builder()
                    .fileName("slides.pptx")
                    .cloudFrontUrl("https://cdn.test.ch/materials/slides.pptx")
                    .createdAt(Instant.now())
                    .build();

            when(eventRepository.findByEventCode("BATbern99")).thenReturn(Optional.of(testEvent));
            when(speakerPoolRepository.findByEventId(eventId)).thenReturn(List.of(speaker));
            when(sessionContentHistoryRepository.findFirstBySessionIdOrderBySubmissionVersionDesc(sessionId))
                    .thenReturn(Optional.empty());
            when(sessionMaterialsRepository.findBySession_IdOrderByCreatedAtAsc(sessionId))
                    .thenReturn(List.of(material));

            List<SpeakerPoolResponse> result = service.getSpeakerPoolForEvent("BATbern99");

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getMaterialFileName()).isEqualTo("slides.pptx");
            assertThat(result.get(0).getMaterialCloudFrontUrl())
                    .isEqualTo("https://cdn.test.ch/materials/slides.pptx");
        }

        @Test
        @DisplayName("should return latest material when multiple uploads exist")
        void shouldReturnLatestMaterial_whenMultipleUploads() {
            UUID speakerId = UUID.randomUUID();
            UUID sessionId = UUID.randomUUID();

            SpeakerPool speaker = SpeakerPool.builder()
                    .id(speakerId)
                    .eventId(eventId)
                    .speakerName("Speaker One")
                    .status(SpeakerWorkflowState.CONTENT_SUBMITTED)
                    .sessionId(sessionId)
                    .build();

            SessionMaterial oldMaterial = SessionMaterial.builder()
                    .fileName("old-slides.pptx")
                    .cloudFrontUrl("https://cdn.test.ch/materials/old.pptx")
                    .createdAt(Instant.now().minusSeconds(3600))
                    .build();

            SessionMaterial newMaterial = SessionMaterial.builder()
                    .fileName("new-slides.pptx")
                    .cloudFrontUrl("https://cdn.test.ch/materials/new.pptx")
                    .createdAt(Instant.now())
                    .build();

            when(eventRepository.findByEventCode("BATbern99")).thenReturn(Optional.of(testEvent));
            when(speakerPoolRepository.findByEventId(eventId)).thenReturn(List.of(speaker));
            when(sessionContentHistoryRepository.findFirstBySessionIdOrderBySubmissionVersionDesc(sessionId))
                    .thenReturn(Optional.empty());
            // Ascending order: old first, new last
            when(sessionMaterialsRepository.findBySession_IdOrderByCreatedAtAsc(sessionId))
                    .thenReturn(List.of(oldMaterial, newMaterial));

            List<SpeakerPoolResponse> result = service.getSpeakerPoolForEvent("BATbern99");

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getMaterialFileName()).isEqualTo("new-slides.pptx");
            assertThat(result.get(0).getMaterialCloudFrontUrl())
                    .isEqualTo("https://cdn.test.ch/materials/new.pptx");
        }

        @Test
        @DisplayName("should have null material fields when no session")
        void shouldHaveNullMaterialFields_whenNoSession() {
            UUID speakerId = UUID.randomUUID();

            SpeakerPool speaker = SpeakerPool.builder()
                    .id(speakerId)
                    .eventId(eventId)
                    .speakerName("Speaker One")
                    .status(SpeakerWorkflowState.INVITED)
                    .sessionId(null)
                    .build();

            when(eventRepository.findByEventCode("BATbern99")).thenReturn(Optional.of(testEvent));
            when(speakerPoolRepository.findByEventId(eventId)).thenReturn(List.of(speaker));
            // No sessionId on the speaker → no history lookup will happen; nothing to stub.

            List<SpeakerPoolResponse> result = service.getSpeakerPoolForEvent("BATbern99");

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getMaterialFileName()).isNull();
            assertThat(result.get(0).getMaterialCloudFrontUrl()).isNull();
        }
    }

    @Nested
    @DisplayName("getSpeakerPool - session title fallback")
    class GetSpeakerPoolSessionFallbackTests {

        @Test
        @DisplayName("should use session title as fallback when no SessionContentVersion exists")
        void shouldUseSessionTitle_whenNoContentSubmission() {
            UUID speakerId = UUID.randomUUID();
            UUID sessionId = UUID.randomUUID();

            SpeakerPool speaker = SpeakerPool.builder()
                    .id(speakerId)
                    .eventId(eventId)
                    .speakerName("Confirmed Speaker")
                    .status(SpeakerWorkflowState.QUALITY_REVIEWED)
                    .sessionId(sessionId)
                    .build();

            Session session = Session.builder()
                    .id(sessionId)
                    .eventId(eventId)
                    .title("Digital Twins in Architecture")
                    .description("A talk about digital twins and their impact")
                    .sessionType("presentation")
                    .build();

            when(eventRepository.findByEventCode("BATbern99")).thenReturn(Optional.of(testEvent));
            when(speakerPoolRepository.findByEventId(eventId)).thenReturn(List.of(speaker));
            when(sessionContentHistoryRepository.findFirstBySessionIdOrderBySubmissionVersionDesc(sessionId))
                    .thenReturn(Optional.empty());
            when(sessionRepository.findAllById(List.of(sessionId))).thenReturn(List.of(session));
            when(sessionMaterialsRepository.findBySession_IdOrderByCreatedAtAsc(sessionId))
                    .thenReturn(List.of());

            List<SpeakerPoolResponse> result = service.getSpeakerPoolForEvent("BATbern99");

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getSubmittedTitle()).isEqualTo("Digital Twins in Architecture");
            assertThat(result.get(0).getSubmittedAbstract()).isEqualTo("A talk about digital twins and their impact");
        }

        @Test
        @DisplayName("Story 11.E.8 §2.9: submittedTitle mirrors sessions.title (not the latest history row)")
        void shouldMirrorSessionTitle_evenWhenHistoryRowDiffers() {
            UUID speakerId = UUID.randomUUID();
            UUID sessionId = UUID.randomUUID();

            SpeakerPool speaker = SpeakerPool.builder()
                    .id(speakerId)
                    .eventId(eventId)
                    .speakerName("Speaker with Submission")
                    .status(SpeakerWorkflowState.CONTENT_SUBMITTED)
                    .sessionId(sessionId)
                    .build();

            Session session = Session.builder()
                    .id(sessionId)
                    .eventId(eventId)
                    .title("Original Session Title")
                    .description("Original description")
                    .sessionType("presentation")
                    .build();

            ch.batbern.events.domain.SessionContentVersion submission =
                    ch.batbern.events.domain.SessionContentVersion.builder()
                            .id(UUID.randomUUID())
                            .session(session)
                            .title("Updated Title from Portal")
                            .contentAbstract("Updated abstract via speaker portal")
                            .abstractCharCount(38)
                            .submissionVersion(1)
                            .build();

            when(eventRepository.findByEventCode("BATbern99")).thenReturn(Optional.of(testEvent));
            when(speakerPoolRepository.findByEventId(eventId)).thenReturn(List.of(speaker));
            when(sessionRepository.findAllById(List.of(sessionId))).thenReturn(List.of(session));
            when(sessionContentHistoryRepository.findFirstBySessionIdOrderBySubmissionVersionDesc(sessionId))
                    .thenReturn(Optional.of(submission));
            when(sessionMaterialsRepository.findBySession_IdOrderByCreatedAtAsc(sessionId))
                    .thenReturn(List.of());

            List<SpeakerPoolResponse> result = service.getSpeakerPoolForEvent("BATbern99");

            assertThat(result).hasSize(1);
            // Story 11.E.8 §2.9: submittedTitle/submittedAbstract are now sourced from
            // sessions.title / sessions.description (the canonical "current"), NOT from
            // the latest session_content_history row. The history row drives the version
            // counter + the derived contentStatus, but the title shown to organizer +
            // public surfaces must match what an organizer's session-modal edit would
            // produce.
            assertThat(result.get(0).getSubmittedTitle()).isEqualTo("Original Session Title");
            assertThat(result.get(0).getSubmittedAbstract()).isEqualTo("Original description");
        }
    }

    @Nested
    @DisplayName("getSpeakerPool — session-derived identity overlay (Phase A)")
    class GetSpeakerPoolSessionIdentityOverlayTests {

        @Test
        @DisplayName("BATbern75 bug fix: response username/email/speakerName/company "
                + "come from session_users + UserApiClient when session exists, ignoring stale pool columns")
        void shouldOverlayIdentityFromSession_whenSessionExists() {
            UUID speakerId = UUID.randomUUID();
            UUID sessionId = UUID.randomUUID();

            // Pool row carries stale columns from a prior promotion; the organizer
            // has since reassigned the session's primary speaker on the Sessions tab.
            SpeakerPool speaker = SpeakerPool.builder()
                    .id(speakerId)
                    .eventId(eventId)
                    .speakerName("Stale Brainstorm Name")
                    .company("StaleCo")
                    .expertise("Some topic")
                    .status(SpeakerWorkflowState.CONTENT_SUBMITTED)
                    .sessionId(sessionId)
                    .build();

            Session session = Session.builder()
                    .id(sessionId)
                    .eventId(eventId)
                    .title("Session Title")
                    .description("Session Description")
                    .sessionType("presentation")
                    .build();

            SessionUser primary = SessionUser.builder()
                    .id(UUID.randomUUID())
                    .session(session)
                    .username("nissim.buchs.3")
                    .speakerRole(SessionUser.SpeakerRole.PRIMARY_SPEAKER)
                    .isConfirmed(true)
                    .build();

            UserResponse user = new UserResponse();
            user.setId("nissim.buchs.3");
            user.setEmail("nissim3@elca.example");
            user.setFirstName("Nissim3");
            user.setLastName("Buchs");
            user.setCompanyId("ELCA");

            when(eventRepository.findByEventCode("BATbern99")).thenReturn(Optional.of(testEvent));
            when(speakerPoolRepository.findByEventId(eventId)).thenReturn(List.of(speaker));
            when(sessionRepository.findAllById(List.of(sessionId))).thenReturn(List.of(session));
            when(sessionContentHistoryRepository.findFirstBySessionIdOrderBySubmissionVersionDesc(sessionId))
                    .thenReturn(Optional.empty());
            when(sessionUserRepository.findBySessionIdInAndSpeakerRole(
                    List.of(sessionId), SessionUser.SpeakerRole.PRIMARY_SPEAKER))
                    .thenReturn(List.of(primary));
            when(userApiClient.getUserByUsername("nissim.buchs.3")).thenReturn(user);
            when(sessionMaterialsRepository.findBySession_IdOrderByCreatedAtAsc(sessionId))
                    .thenReturn(List.of());

            List<SpeakerPoolResponse> result = service.getSpeakerPoolForEvent("BATbern99");

            assertThat(result).hasSize(1);
            SpeakerPoolResponse response = result.get(0);
            // The session-overlay wins over the stale pool columns: this is the entire
            // point of the BATbern75 fix.
            // The session-overlay wins over the stale pool columns: this is the entire
            // point of the BATbern75 fix.
            assertThat(response.getUsername()).isEqualTo("nissim.buchs.3");
            assertThat(response.getEmail()).isEqualTo("nissim3@elca.example");
            assertThat(response.getSpeakerName()).isEqualTo("Nissim3 Buchs");
            assertThat(response.getCompany()).isEqualTo("ELCA");
        }

        @Test
        @DisplayName("pre-session rows (no sessionId) keep pool columns untouched — brainstorm UX preserved")
        void shouldKeepPoolColumns_whenNoSession() {
            UUID speakerId = UUID.randomUUID();

            SpeakerPool speaker = SpeakerPool.builder()
                    .id(speakerId)
                    .eventId(eventId)
                    .speakerName("Brainstorm Candidate")
                    .company("BrainstormCo")
                    .expertise("Topic")
                    .status(SpeakerWorkflowState.IDENTIFIED)
                    .sessionId(null)
                    .build();

            when(eventRepository.findByEventCode("BATbern99")).thenReturn(Optional.of(testEvent));
            when(speakerPoolRepository.findByEventId(eventId)).thenReturn(List.of(speaker));

            List<SpeakerPoolResponse> result = service.getSpeakerPoolForEvent("BATbern99");

            assertThat(result).hasSize(1);
            SpeakerPoolResponse response = result.get(0);
            assertThat(response.getSpeakerName()).isEqualTo("Brainstorm Candidate");
            assertThat(response.getCompany()).isEqualTo("BrainstormCo");
            // Pre-session: no overlay because there's no session_user yet.
        }

        @Test
        @DisplayName("UserApiClient failure on a single row degrades gracefully — speakerName falls back to SessionUser cache, response still returned")
        void shouldDegrade_whenUserApiClientFails() {
            UUID speakerId = UUID.randomUUID();
            UUID sessionId = UUID.randomUUID();

            SpeakerPool speaker = SpeakerPool.builder()
                    .id(speakerId)
                    .eventId(eventId)
                    .speakerName("Pool Name")
                    .company("PoolCo")
                    .status(SpeakerWorkflowState.CONTENT_SUBMITTED)
                    .sessionId(sessionId)
                    .build();

            Session session = Session.builder()
                    .id(sessionId)
                    .eventId(eventId)
                    .title("Session")
                    .sessionType("presentation")
                    .build();

            // Primary session_user carries its cached firstName/lastName (from V38).
            SessionUser primary = SessionUser.builder()
                    .id(UUID.randomUUID())
                    .session(session)
                    .username("orphaned.user")
                    .speakerRole(SessionUser.SpeakerRole.PRIMARY_SPEAKER)
                    .speakerFirstName("Cached")
                    .speakerLastName("Name")
                    .build();

            when(eventRepository.findByEventCode("BATbern99")).thenReturn(Optional.of(testEvent));
            when(speakerPoolRepository.findByEventId(eventId)).thenReturn(List.of(speaker));
            when(sessionRepository.findAllById(List.of(sessionId))).thenReturn(List.of(session));
            when(sessionContentHistoryRepository.findFirstBySessionIdOrderBySubmissionVersionDesc(sessionId))
                    .thenReturn(Optional.empty());
            when(sessionUserRepository.findBySessionIdInAndSpeakerRole(
                    List.of(sessionId), SessionUser.SpeakerRole.PRIMARY_SPEAKER))
                    .thenReturn(List.of(primary));
            when(userApiClient.getUserByUsername("orphaned.user"))
                    .thenThrow(new ch.batbern.events.exception.UserServiceException(
                            "CUMS unavailable", new RuntimeException()));
            when(sessionMaterialsRepository.findBySession_IdOrderByCreatedAtAsc(sessionId))
                    .thenReturn(List.of());

            List<SpeakerPoolResponse> result = service.getSpeakerPoolForEvent("BATbern99");

            assertThat(result).hasSize(1);
            SpeakerPoolResponse response = result.get(0);
            // Username comes from session_users (no HTTP call needed); name falls back
            // to the SessionUser cache; email is unknown (CUMS down) → null is acceptable.
            assertThat(response.getSpeakerName()).isEqualTo("Cached Name");
        }
    }
}
