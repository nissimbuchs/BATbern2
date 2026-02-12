package ch.batbern.events.service;

import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Session;
import ch.batbern.events.domain.SessionMaterial;
import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.events.dto.SpeakerPoolResponse;
import ch.batbern.events.repository.ContentSubmissionRepository;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.SessionMaterialsRepository;
import ch.batbern.events.repository.SessionRepository;
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
    private ContentSubmissionRepository contentSubmissionRepository;

    @Mock
    private SessionRepository sessionRepository;

    @Mock
    private SessionMaterialsRepository sessionMaterialsRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private SecurityContextHelper securityContextHelper;

    private SpeakerPoolService service;

    private Event testEvent;
    private UUID eventId;

    @BeforeEach
    void setUp() {
        service = new SpeakerPoolService(
                speakerPoolRepository, eventRepository, contentSubmissionRepository,
                sessionRepository, sessionMaterialsRepository, eventPublisher, securityContextHelper
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
            when(contentSubmissionRepository.findFirstBySpeakerPoolIdOrderBySubmissionVersionDesc(speakerId))
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
            when(contentSubmissionRepository.findFirstBySpeakerPoolIdOrderBySubmissionVersionDesc(speakerId))
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
            when(contentSubmissionRepository.findFirstBySpeakerPoolIdOrderBySubmissionVersionDesc(speakerId))
                    .thenReturn(Optional.empty());

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
        @DisplayName("should use session title as fallback when no ContentSubmission exists")
        void shouldUseSessionTitle_whenNoContentSubmission() {
            UUID speakerId = UUID.randomUUID();
            UUID sessionId = UUID.randomUUID();

            SpeakerPool speaker = SpeakerPool.builder()
                    .id(speakerId)
                    .eventId(eventId)
                    .speakerName("Confirmed Speaker")
                    .status(SpeakerWorkflowState.CONFIRMED)
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
            when(contentSubmissionRepository.findFirstBySpeakerPoolIdOrderBySubmissionVersionDesc(speakerId))
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
        @DisplayName("should not use session fallback when ContentSubmission exists")
        void shouldPreferContentSubmission_overSessionFallback() {
            UUID speakerId = UUID.randomUUID();
            UUID sessionId = UUID.randomUUID();

            SpeakerPool speaker = SpeakerPool.builder()
                    .id(speakerId)
                    .eventId(eventId)
                    .speakerName("Speaker with Submission")
                    .status(SpeakerWorkflowState.CONTENT_SUBMITTED)
                    .sessionId(sessionId)
                    .build();

            ch.batbern.events.domain.ContentSubmission submission =
                    ch.batbern.events.domain.ContentSubmission.builder()
                            .id(UUID.randomUUID())
                            .speakerPool(speaker)
                            .title("Updated Title from Portal")
                            .contentAbstract("Updated abstract via speaker portal")
                            .abstractCharCount(38)
                            .submissionVersion(1)
                            .build();

            when(eventRepository.findByEventCode("BATbern99")).thenReturn(Optional.of(testEvent));
            when(speakerPoolRepository.findByEventId(eventId)).thenReturn(List.of(speaker));
            when(contentSubmissionRepository.findFirstBySpeakerPoolIdOrderBySubmissionVersionDesc(speakerId))
                    .thenReturn(Optional.of(submission));
            when(sessionMaterialsRepository.findBySession_IdOrderByCreatedAtAsc(sessionId))
                    .thenReturn(List.of());

            List<SpeakerPoolResponse> result = service.getSpeakerPoolForEvent("BATbern99");

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getSubmittedTitle()).isEqualTo("Updated Title from Portal");
            assertThat(result.get(0).getSubmittedAbstract()).isEqualTo("Updated abstract via speaker portal");
        }
    }
}
