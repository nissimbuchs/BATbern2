package ch.batbern.events.service;

import ch.batbern.events.AbstractIntegrationTest;
import ch.batbern.events.domain.ContentSubmission;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Session;
import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.events.dto.ContentDraftRequest;
import ch.batbern.events.dto.ContentDraftResponse;
import ch.batbern.events.dto.ContentSubmitRequest;
import ch.batbern.events.dto.ContentSubmitResponse;
import ch.batbern.events.dto.SpeakerContentInfo;
import ch.batbern.events.dto.TokenValidationResult;
import ch.batbern.events.dto.generated.EventType;
import ch.batbern.events.domain.SpeakerStatusHistory;
import ch.batbern.events.repository.ContentSubmissionRepository;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.SessionRepository;
import ch.batbern.events.repository.SpeakerPoolRepository;
import ch.batbern.events.repository.SpeakerStatusHistoryRepository;
import ch.batbern.shared.types.EventWorkflowState;
import ch.batbern.shared.types.SpeakerWorkflowState;
import ch.batbern.shared.types.TokenAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import org.springframework.transaction.annotation.Transactional;

/**
 * Integration tests for ContentSubmissionService (Story 6.3).
 *
 * Tests speaker self-service content submission via magic link token.
 * All tests run against PostgreSQL via Testcontainers.
 */
@Transactional
class ContentSubmissionServiceTest extends AbstractIntegrationTest {

    @Autowired
    private ContentSubmissionService contentSubmissionService;

    @Autowired
    private SpeakerPoolRepository speakerPoolRepository;

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private ContentSubmissionRepository contentSubmissionRepository;

    @Autowired
    private SpeakerStatusHistoryRepository statusHistoryRepository;

    @MockBean
    private MagicLinkService magicLinkService;

    @MockBean
    private ApplicationEventPublisher eventPublisher;

    private Event testEvent;
    private Session testSession;
    private SpeakerPool speakerWithSession;
    private SpeakerPool speakerWithoutSession;
    private final String validToken = "valid-test-token";

    @BeforeEach
    void setUp() {
        // Clean up previous test data
        contentSubmissionRepository.deleteAll();

        // Create test event with all required fields (use random number to avoid conflicts)
        int randomEventNumber = (int) (System.currentTimeMillis() % 100000);
        testEvent = Event.builder()
                .eventCode("BAT-2026-" + randomEventNumber)
                .title("Test Event")
                .eventNumber(randomEventNumber)
                .eventType(EventType.FULL_DAY)
                .date(Instant.now().plusSeconds(90 * 24 * 60 * 60)) // 90 days from now
                .venueName("Test Venue")
                .venueAddress("Test Address, Bern")
                .venueCapacity(100)
                .registrationDeadline(Instant.now().plusSeconds(60 * 24 * 60 * 60)) // 60 days from now
                .organizerUsername("test-organizer")
                .workflowState(EventWorkflowState.TOPIC_SELECTION)
                .build();
        testEvent = eventRepository.save(testEvent);

        // Create test session
        testSession = Session.builder()
                .eventId(testEvent.getId())
                .eventCode(testEvent.getEventCode())
                .title("Test Session")
                .sessionSlug("test-session-" + UUID.randomUUID().toString().substring(0, 8))
                .sessionType("presentation")
                .build();
        testSession = sessionRepository.save(testSession);

        // Create speaker with session assigned (can submit content)
        speakerWithSession = SpeakerPool.builder()
                .eventId(testEvent.getId())
                .speakerName("Test Speaker")
                .email("speaker@test.com")
                .company("Test Company")
                .status(SpeakerWorkflowState.ACCEPTED)
                .sessionId(testSession.getId())
                .contentStatus("PENDING")
                .acceptedAt(Instant.now())
                .build();
        speakerWithSession = speakerPoolRepository.save(speakerWithSession);

        // Create speaker without session (cannot submit content)
        speakerWithoutSession = SpeakerPool.builder()
                .eventId(testEvent.getId())
                .speakerName("No Session Speaker")
                .email("nosession@test.com")
                .status(SpeakerWorkflowState.ACCEPTED)
                .contentStatus("PENDING")
                .acceptedAt(Instant.now())
                .build();
        speakerWithoutSession = speakerPoolRepository.save(speakerWithoutSession);
    }

    @Nested
    @DisplayName("AC1: Session Assignment Check")
    class SessionAssignmentTests {

        @Test
        @DisplayName("Test 1.1: should_showBlockedMessage_when_noSessionAssigned")
        void should_returnNoSession_when_speakerHasNoSessionAssigned() {
            // Given: Token validation returns speaker without session
            when(magicLinkService.validateToken(validToken))
                    .thenReturn(TokenValidationResult.valid(
                            speakerWithoutSession.getId(),
                            null,
                            "No Session Speaker",
                            testEvent.getEventCode(),
                            testEvent.getTitle(),
                            null,
                            null, // No session title
                            null,
                            null,
                            false,
                            null,
                            null,
                            TokenAction.SUBMIT
                    ));

            // When
            SpeakerContentInfo result = contentSubmissionService.getContentInfo(validToken);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.hasSessionAssigned()).isFalse();
            assertThat(result.canSubmitContent()).isFalse();
        }

        @Test
        @DisplayName("Test 1.2: should_showContentForm_when_sessionAssigned")
        void should_returnContentForm_when_speakerHasSessionAssigned() {
            // Given: Token validation returns speaker with session
            when(magicLinkService.validateToken(validToken))
                    .thenReturn(TokenValidationResult.valid(
                            speakerWithSession.getId(),
                            null,
                            "Test Speaker",
                            testEvent.getEventCode(),
                            testEvent.getTitle(),
                            null,
                            testSession.getTitle(),
                            null,
                            null,
                            false,
                            null,
                            null,
                            TokenAction.SUBMIT
                    ));

            // When
            SpeakerContentInfo result = contentSubmissionService.getContentInfo(validToken);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.hasSessionAssigned()).isTrue();
            assertThat(result.canSubmitContent()).isTrue();
            assertThat(result.sessionTitle()).isEqualTo(testSession.getTitle());
        }

        @Test
        @DisplayName("Test 1.3: should_returnError400_when_submitWithoutSession")
        void should_throwException_when_submitWithoutSession() {
            // Given: Token validation returns speaker without session
            when(magicLinkService.validateToken(validToken))
                    .thenReturn(TokenValidationResult.valid(
                            speakerWithoutSession.getId(),
                            null,
                            "No Session Speaker",
                            testEvent.getEventCode(),
                            testEvent.getTitle(),
                            null,
                            null,
                            null,
                            null,
                            false,
                            null,
                            null,
                            TokenAction.SUBMIT
                    ));

            ContentSubmitRequest request = new ContentSubmitRequest(
                    validToken,
                    "Test Title",
                    "Test Abstract that is long enough to be valid."
            );

            // When/Then
            assertThatThrownBy(() -> contentSubmissionService.submitContent(request))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("session");
        }
    }

    @Nested
    @DisplayName("AC5: Content Submission")
    class ContentSubmissionTests {

        @Test
        @DisplayName("Test 5.1: should_createContentSubmission_when_validDataSubmitted")
        void should_createContentSubmission_when_validDataSubmitted() {
            // Given: Token validation returns speaker with session
            when(magicLinkService.validateToken(validToken))
                    .thenReturn(TokenValidationResult.valid(
                            speakerWithSession.getId(),
                            null,
                            "Test Speaker",
                            testEvent.getEventCode(),
                            testEvent.getTitle(),
                            null,
                            testSession.getTitle(),
                            null,
                            null,
                            false,
                            null,
                            null,
                            TokenAction.SUBMIT
                    ));

            ContentSubmitRequest request = new ContentSubmitRequest(
                    validToken,
                    "My Presentation Title",
                    "This is my presentation abstract. It describes what attendees will learn from my session."
            );

            // When
            ContentSubmitResponse result = contentSubmissionService.submitContent(request);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.submissionId()).isNotNull();
            assertThat(result.version()).isEqualTo(1);
            assertThat(result.status()).isEqualTo("SUBMITTED");

            // Verify submission was persisted
            Optional<ContentSubmission> saved = contentSubmissionRepository.findById(result.submissionId());
            assertThat(saved).isPresent();
            assertThat(saved.get().getTitle()).isEqualTo("My Presentation Title");
            assertThat(saved.get().getContentAbstract()).contains("presentation abstract");
        }

        @Test
        @DisplayName("Test 5.2: should_updateContentStatus_to_SUBMITTED")
        void should_updateContentStatus_when_submitted() {
            // Given
            when(magicLinkService.validateToken(validToken))
                    .thenReturn(TokenValidationResult.valid(
                            speakerWithSession.getId(),
                            null,
                            "Test Speaker",
                            testEvent.getEventCode(),
                            testEvent.getTitle(),
                            null,
                            testSession.getTitle(),
                            null,
                            null,
                            false,
                            null,
                            null,
                            TokenAction.SUBMIT
                    ));

            ContentSubmitRequest request = new ContentSubmitRequest(
                    validToken,
                    "Title",
                    "Abstract with sufficient length for testing."
            );

            // When
            contentSubmissionService.submitContent(request);

            // Then: speaker_pool content_status should be SUBMITTED
            SpeakerPool updated = speakerPoolRepository.findById(speakerWithSession.getId()).orElseThrow();
            assertThat(updated.getContentStatus()).isEqualTo("SUBMITTED");
        }

        @Test
        @DisplayName("Test 5.2b: should_updateWorkflowStatus_to_CONTENT_SUBMITTED - ensures speaker moves to 'Inhalt eingereicht' Kanban column")
        void should_updateWorkflowStatus_to_CONTENT_SUBMITTED_when_submitted() {
            // Given: Speaker starts in ACCEPTED status
            assertThat(speakerWithSession.getStatus()).isEqualTo(SpeakerWorkflowState.ACCEPTED);

            when(magicLinkService.validateToken(validToken))
                    .thenReturn(TokenValidationResult.valid(
                            speakerWithSession.getId(),
                            null,
                            "Test Speaker",
                            testEvent.getEventCode(),
                            testEvent.getTitle(),
                            null,
                            testSession.getTitle(),
                            null,
                            null,
                            false,
                            null,
                            null,
                            TokenAction.SUBMIT
                    ));

            ContentSubmitRequest request = new ContentSubmitRequest(
                    validToken,
                    "My Presentation",
                    "Abstract with sufficient length for testing the workflow status update."
            );

            // When
            contentSubmissionService.submitContent(request);

            // Then: speaker_pool.status should be CONTENT_SUBMITTED (not just content_status)
            // This ensures the speaker appears in the "Inhalt eingereicht" column in the Kanban board
            SpeakerPool updated = speakerPoolRepository.findById(speakerWithSession.getId()).orElseThrow();
            assertThat(updated.getStatus())
                    .as("Workflow status must be CONTENT_SUBMITTED so speaker appears in correct Kanban column")
                    .isEqualTo(SpeakerWorkflowState.CONTENT_SUBMITTED);
        }

        @Test
        @DisplayName("Test 5.2c: should_createStatusHistory_when_contentSubmitted - tracks workflow transition for audit")
        void should_createStatusHistory_when_contentSubmitted() {
            // Given: Speaker starts in ACCEPTED status
            assertThat(speakerWithSession.getStatus()).isEqualTo(SpeakerWorkflowState.ACCEPTED);

            when(magicLinkService.validateToken(validToken))
                    .thenReturn(TokenValidationResult.valid(
                            speakerWithSession.getId(),
                            null,
                            "Test Speaker",
                            testEvent.getEventCode(),
                            testEvent.getTitle(),
                            null,
                            testSession.getTitle(),
                            null,
                            null,
                            false,
                            null,
                            null,
                            TokenAction.SUBMIT
                    ));

            ContentSubmitRequest request = new ContentSubmitRequest(
                    validToken,
                    "My Presentation for History Test",
                    "Abstract with sufficient length for testing history creation."
            );

            // When
            contentSubmissionService.submitContent(request);

            // Then: Status history should be created for the ACCEPTED -> CONTENT_SUBMITTED transition
            java.util.List<SpeakerStatusHistory> historyList = statusHistoryRepository
                    .findBySpeakerPoolIdOrderByChangedAtDesc(speakerWithSession.getId());

            assertThat(historyList).isNotEmpty();
            SpeakerStatusHistory latestHistory = historyList.get(0);
            assertThat(latestHistory.getPreviousStatus()).isEqualTo(SpeakerWorkflowState.ACCEPTED);
            assertThat(latestHistory.getNewStatus()).isEqualTo(SpeakerWorkflowState.CONTENT_SUBMITTED);
            assertThat(latestHistory.getEventId()).isEqualTo(testEvent.getId());
            assertThat(latestHistory.getSessionId()).isEqualTo(testSession.getId());
            assertThat(latestHistory.getChangeReason()).contains("Content submitted via speaker portal");
        }

        @Test
        @DisplayName("Test 5.3: should_setContentSubmittedAt_timestamp")
        void should_setContentSubmittedAt_when_submitted() {
            // Given
            Instant before = Instant.now();
            when(magicLinkService.validateToken(validToken))
                    .thenReturn(TokenValidationResult.valid(
                            speakerWithSession.getId(),
                            null,
                            "Test Speaker",
                            testEvent.getEventCode(),
                            testEvent.getTitle(),
                            null,
                            testSession.getTitle(),
                            null,
                            null,
                            false,
                            null,
                            null,
                            TokenAction.SUBMIT
                    ));

            ContentSubmitRequest request = new ContentSubmitRequest(
                    validToken,
                    "Title",
                    "Abstract with sufficient length for testing."
            );

            // When
            contentSubmissionService.submitContent(request);

            // Then
            SpeakerPool updated = speakerPoolRepository.findById(speakerWithSession.getId()).orElseThrow();
            assertThat(updated.getContentSubmittedAt()).isAfterOrEqualTo(before);
        }

        @Test
        @DisplayName("Test 5.5: should_returnError400_when_titleMissing")
        void should_throwException_when_titleMissing() {
            // Given
            when(magicLinkService.validateToken(validToken))
                    .thenReturn(TokenValidationResult.valid(
                            speakerWithSession.getId(),
                            null,
                            "Test Speaker",
                            testEvent.getEventCode(),
                            testEvent.getTitle(),
                            null,
                            testSession.getTitle(),
                            null,
                            null,
                            false,
                            null,
                            null,
                            TokenAction.SUBMIT
                    ));

            ContentSubmitRequest request = new ContentSubmitRequest(
                    validToken,
                    "", // Empty title
                    "Valid abstract text."
            );

            // When/Then
            assertThatThrownBy(() -> contentSubmissionService.submitContent(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("title");
        }

        @Test
        @DisplayName("Test 5.6: should_returnError400_when_abstractMissing")
        void should_throwException_when_abstractMissing() {
            // Given
            when(magicLinkService.validateToken(validToken))
                    .thenReturn(TokenValidationResult.valid(
                            speakerWithSession.getId(),
                            null,
                            "Test Speaker",
                            testEvent.getEventCode(),
                            testEvent.getTitle(),
                            null,
                            testSession.getTitle(),
                            null,
                            null,
                            false,
                            null,
                            null,
                            TokenAction.SUBMIT
                    ));

            ContentSubmitRequest request = new ContentSubmitRequest(
                    validToken,
                    "Valid Title",
                    "" // Empty abstract
            );

            // When/Then
            assertThatThrownBy(() -> contentSubmissionService.submitContent(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("abstract");
        }
    }

    @Nested
    @DisplayName("AC4: Draft Auto-Save")
    class DraftTests {

        @Test
        @DisplayName("Test 4.3: should_restoreDraft_when_pageReloaded")
        void should_returnDraft_when_exists() {
            // Given: Create a draft submission
            ContentSubmission draft = ContentSubmission.builder()
                    .speakerPool(speakerWithSession)
                    .session(testSession)
                    .title("Draft Title")
                    .contentAbstract("Draft abstract content")
                    .abstractCharCount(22)
                    .submissionVersion(1)
                    .build();
            contentSubmissionRepository.save(draft);

            when(magicLinkService.validateToken(validToken))
                    .thenReturn(TokenValidationResult.valid(
                            speakerWithSession.getId(),
                            null,
                            "Test Speaker",
                            testEvent.getEventCode(),
                            testEvent.getTitle(),
                            null,
                            testSession.getTitle(),
                            null,
                            null,
                            false,
                            null,
                            null,
                            TokenAction.SUBMIT
                    ));

            // When
            SpeakerContentInfo result = contentSubmissionService.getContentInfo(validToken);

            // Then
            assertThat(result.hasDraft()).isTrue();
            assertThat(result.draftTitle()).isEqualTo("Draft Title");
            assertThat(result.draftAbstract()).isEqualTo("Draft abstract content");
        }

        @Test
        @DisplayName("Test 4.4: should_saveDraftManually_when_buttonClicked")
        void should_saveDraft_when_requested() {
            // Given
            when(magicLinkService.validateToken(validToken))
                    .thenReturn(TokenValidationResult.valid(
                            speakerWithSession.getId(),
                            null,
                            "Test Speaker",
                            testEvent.getEventCode(),
                            testEvent.getTitle(),
                            null,
                            testSession.getTitle(),
                            null,
                            null,
                            false,
                            null,
                            null,
                            TokenAction.SUBMIT
                    ));

            ContentDraftRequest request = new ContentDraftRequest(
                    validToken,
                    "Work in Progress Title",
                    "This is my draft abstract that I'm still working on."
            );

            // When
            ContentDraftResponse result = contentSubmissionService.saveDraft(request);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.draftId()).isNotNull();
            assertThat(result.savedAt()).isNotNull();

            // Verify draft was persisted
            Optional<ContentSubmission> saved = contentSubmissionRepository
                    .findFirstBySpeakerPoolIdOrderBySubmissionVersionDesc(speakerWithSession.getId());
            assertThat(saved).isPresent();
            assertThat(saved.get().getTitle()).isEqualTo("Work in Progress Title");
        }
    }

    @Nested
    @DisplayName("AC8: Revision Support")
    class RevisionTests {

        @Test
        @DisplayName("Test 8.1: should_showReviewerFeedback_when_statusRevisionNeeded")
        void should_returnFeedback_when_revisionNeeded() {
            // Given: Speaker with REVISION_NEEDED status
            speakerWithSession.setContentStatus("REVISION_NEEDED");
            speakerPoolRepository.save(speakerWithSession);

            ContentSubmission submission = ContentSubmission.builder()
                    .speakerPool(speakerWithSession)
                    .session(testSession)
                    .title("Original Title")
                    .contentAbstract("Original abstract")
                    .abstractCharCount(16)
                    .submissionVersion(1)
                    .reviewerFeedback("Please add more details about lessons learned.")
                    .reviewedBy("organizer@test.com")
                    .reviewedAt(Instant.now())
                    .build();
            contentSubmissionRepository.save(submission);

            when(magicLinkService.validateToken(validToken))
                    .thenReturn(TokenValidationResult.valid(
                            speakerWithSession.getId(),
                            null,
                            "Test Speaker",
                            testEvent.getEventCode(),
                            testEvent.getTitle(),
                            null,
                            testSession.getTitle(),
                            null,
                            null,
                            false,
                            null,
                            null,
                            TokenAction.SUBMIT
                    ));

            // When
            SpeakerContentInfo result = contentSubmissionService.getContentInfo(validToken);

            // Then
            assertThat(result.needsRevision()).isTrue();
            assertThat(result.reviewerFeedback()).isEqualTo("Please add more details about lessons learned.");
        }

        @Test
        @DisplayName("Test 8.2: should_incrementVersion_when_resubmitted")
        void should_incrementVersion_when_resubmitted() {
            // Given: Existing submission with version 1
            ContentSubmission v1 = ContentSubmission.builder()
                    .speakerPool(speakerWithSession)
                    .session(testSession)
                    .title("V1 Title")
                    .contentAbstract("V1 abstract")
                    .abstractCharCount(11)
                    .submissionVersion(1)
                    .build();
            contentSubmissionRepository.save(v1);

            speakerWithSession.setContentStatus("REVISION_NEEDED");
            speakerPoolRepository.save(speakerWithSession);

            when(magicLinkService.validateToken(validToken))
                    .thenReturn(TokenValidationResult.valid(
                            speakerWithSession.getId(),
                            null,
                            "Test Speaker",
                            testEvent.getEventCode(),
                            testEvent.getTitle(),
                            null,
                            testSession.getTitle(),
                            null,
                            null,
                            false,
                            null,
                            null,
                            TokenAction.SUBMIT
                    ));

            ContentSubmitRequest request = new ContentSubmitRequest(
                    validToken,
                    "V2 Title - Improved",
                    "V2 abstract with more detail about lessons learned."
            );

            // When
            ContentSubmitResponse result = contentSubmissionService.submitContent(request);

            // Then
            assertThat(result.version()).isEqualTo(2);

            // Both versions should exist
            assertThat(contentSubmissionRepository.countBySpeakerPoolId(speakerWithSession.getId())).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("Token Validation")
    class TokenValidationTests {

        @Test
        @DisplayName("should_throwException_when_tokenInvalid")
        void should_throwException_when_tokenInvalid() {
            // Given: Invalid token
            when(magicLinkService.validateToken(anyString()))
                    .thenReturn(TokenValidationResult.notFound());

            // When/Then
            assertThatThrownBy(() -> contentSubmissionService.getContentInfo("invalid-token"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid token");
        }

        @Test
        @DisplayName("should_throwException_when_tokenExpired")
        void should_throwException_when_tokenExpired() {
            // Given: Expired token
            when(magicLinkService.validateToken(anyString()))
                    .thenReturn(TokenValidationResult.expired());

            // When/Then
            assertThatThrownBy(() -> contentSubmissionService.getContentInfo("expired-token"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("expired");
        }
    }
}
