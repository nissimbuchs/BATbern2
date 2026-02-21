package ch.batbern.events.service;

import ch.batbern.events.client.UserApiClient;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.OutreachHistory;
import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.events.domain.SpeakerStatusHistory;
import ch.batbern.events.dto.BatchInviteRequest;
import ch.batbern.events.dto.BatchInviteResponse;
import ch.batbern.events.dto.InviteSpeakerRequest;
import ch.batbern.events.dto.InviteSpeakerResponse;
import ch.batbern.events.dto.SendInvitationRequest;
import ch.batbern.events.dto.SendInvitationResponse;
import ch.batbern.events.dto.generated.users.GetOrCreateUserRequest;
import ch.batbern.events.dto.generated.users.GetOrCreateUserResponse;
import ch.batbern.events.exception.EventNotFoundException;
import ch.batbern.events.exception.SpeakerNotFoundException;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.OutreachHistoryRepository;
import ch.batbern.events.repository.SpeakerPoolRepository;
import ch.batbern.events.repository.SpeakerStatusHistoryRepository;
import ch.batbern.events.security.SecurityContextHelper;
import ch.batbern.shared.events.SpeakerInvitationSentEvent;
import ch.batbern.shared.types.SpeakerWorkflowState;
import ch.batbern.shared.types.TokenAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for SpeakerInvitationService - Story 6.1b.
 *
 * Tests service logic with mocked repositories and external services.
 * Covers: AC1 (invite speaker), AC2 (auto-create user), AC3 (send email),
 * AC5 (batch), AC6 (domain events), AC7 (idempotency).
 */
@ExtendWith(MockitoExtension.class)
class SpeakerInvitationServiceTest {

    @Mock
    private SpeakerPoolRepository speakerPoolRepository;

    @Mock
    private EventRepository eventRepository;

    @Mock
    private UserApiClient userApiClient;

    @Mock
    private MagicLinkService magicLinkService;

    @Mock
    private SpeakerInvitationEmailService emailService;

    @Mock
    private SecurityContextHelper securityContextHelper;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private OutreachHistoryRepository outreachHistoryRepository;

    @Mock
    private SpeakerStatusHistoryRepository statusHistoryRepository;

    @InjectMocks
    private SpeakerInvitationService speakerInvitationService;

    private Event testEvent;
    private SpeakerPool testSpeaker;
    private final String testEventCode = "batbern-2026-spring";
    private final String testEmail = "speaker@example.com";
    private final String testUsername = "speaker.test";
    private final UUID testEventId = UUID.randomUUID();
    private final UUID testSpeakerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        testEvent = Event.builder()
                .id(testEventId)
                .eventCode(testEventCode)
                .eventNumber(56)
                .title("BATbern Spring 2026")
                .date(Instant.now().plus(60, ChronoUnit.DAYS))
                .build();

        testSpeaker = SpeakerPool.builder()
                .id(testSpeakerId)
                .eventId(testEventId)
                .username(testUsername)
                .email(testEmail)
                .speakerName("Test Speaker")
                .status(SpeakerWorkflowState.IDENTIFIED)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        // Default lenient stubs for history repositories (used in sendInvitation)
        lenient().when(outreachHistoryRepository.save(any(OutreachHistory.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        lenient().when(statusHistoryRepository.save(any(SpeakerStatusHistory.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    // ==========================================================================
    // AC1: Invite Speaker by Email
    // ==========================================================================

    @Nested
    @DisplayName("AC1: Invite Speaker by Email")
    class InviteSpeakerTests {

        @Test
        @DisplayName("6.1b-UNIT-001: should create SpeakerPool when new email provided")
        void should_createSpeakerPool_when_newEmailProvided() {
            // Given
            InviteSpeakerRequest request = new InviteSpeakerRequest(
                    testEmail, "John", "Doe", "Swiss Tech AG", null, null
            );

            GetOrCreateUserResponse userResponse = new GetOrCreateUserResponse();
            userResponse.setUsername(testUsername);
            userResponse.setCreated(true);

            when(eventRepository.findByEventCode(testEventCode)).thenReturn(Optional.of(testEvent));
            when(speakerPoolRepository.findByEventIdAndEmail(testEventId, testEmail)).thenReturn(Optional.empty());
            when(userApiClient.getOrCreateUser(any(GetOrCreateUserRequest.class))).thenReturn(userResponse);
            when(speakerPoolRepository.save(any(SpeakerPool.class))).thenAnswer(inv -> {
                SpeakerPool sp = inv.getArgument(0);
                sp.setId(testSpeakerId);
                sp.setCreatedAt(Instant.now());
                return sp;
            });

            // When
            InviteSpeakerResponse response = speakerInvitationService.inviteSpeaker(testEventCode, request);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.created()).isTrue();
            assertThat(response.username()).isEqualTo(testUsername);
            assertThat(response.email()).isEqualTo(testEmail);
            assertThat(response.speakerName()).isEqualTo("John Doe");
            verify(speakerPoolRepository).save(any(SpeakerPool.class));
        }

        @Test
        @DisplayName("6.1b-UNIT-002: should link existing user when email already exists")
        void should_linkExistingUser_when_emailAlreadyExists() {
            // Given
            InviteSpeakerRequest request = new InviteSpeakerRequest(
                    testEmail, "John", "Doe", null, null, null
            );

            GetOrCreateUserResponse userResponse = new GetOrCreateUserResponse();
            userResponse.setUsername(testUsername);
            userResponse.setCreated(false); // User already exists

            when(eventRepository.findByEventCode(testEventCode)).thenReturn(Optional.of(testEvent));
            when(speakerPoolRepository.findByEventIdAndEmail(testEventId, testEmail)).thenReturn(Optional.empty());
            when(userApiClient.getOrCreateUser(any(GetOrCreateUserRequest.class))).thenReturn(userResponse);
            when(speakerPoolRepository.save(any(SpeakerPool.class))).thenAnswer(inv -> {
                SpeakerPool sp = inv.getArgument(0);
                sp.setId(testSpeakerId);
                sp.setCreatedAt(Instant.now());
                return sp;
            });

            // When
            InviteSpeakerResponse response = speakerInvitationService.inviteSpeaker(testEventCode, request);

            // Then
            assertThat(response.userCreated()).isFalse();
            assertThat(response.created()).isTrue(); // SpeakerPool was created
        }

        @Test
        @DisplayName("6.1b-UNIT-003: should return existing SpeakerPool when already invited to event (idempotency)")
        void should_returnExistingSpeakerPool_when_alreadyInvitedToEvent() {
            // Given
            InviteSpeakerRequest request = new InviteSpeakerRequest(
                    testEmail, "John", "Doe", null, null, null
            );

            when(eventRepository.findByEventCode(testEventCode)).thenReturn(Optional.of(testEvent));
            when(speakerPoolRepository.findByEventIdAndEmail(testEventId, testEmail))
                    .thenReturn(Optional.of(testSpeaker));

            // When
            InviteSpeakerResponse response = speakerInvitationService.inviteSpeaker(testEventCode, request);

            // Then
            assertThat(response.created()).isFalse(); // Not newly created
            assertThat(response.speakerPoolId()).isEqualTo(testSpeakerId);
            verify(userApiClient, never()).getOrCreateUser(any()); // No user creation
            verify(speakerPoolRepository, never()).save(any()); // No save
        }

        @Test
        @DisplayName("should throw EventNotFoundException when event code not found")
        void should_throwEventNotFoundException_when_eventCodeNotFound() {
            // Given
            InviteSpeakerRequest request = new InviteSpeakerRequest(
                    testEmail, "John", "Doe", null, null, null
            );

            when(eventRepository.findByEventCode("non-existent")).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> speakerInvitationService.inviteSpeaker("non-existent", request))
                    .isInstanceOf(EventNotFoundException.class);
        }
    }

    // ==========================================================================
    // AC2: Auto-Create User
    // ==========================================================================

    @Nested
    @DisplayName("AC2: Auto-Create User")
    class AutoCreateUserTests {

        @Test
        @DisplayName("6.1b-UNIT-005: should call UserApiClient to get or create user")
        void should_callUserApiClient_when_creatingUser() {
            // Given
            InviteSpeakerRequest request = new InviteSpeakerRequest(
                    "john.doe@example.com", "John", "Doe", "Test Company", null, null
            );

            GetOrCreateUserResponse userResponse = new GetOrCreateUserResponse();
            userResponse.setUsername("john.doe");
            userResponse.setCreated(true);

            when(eventRepository.findByEventCode(testEventCode)).thenReturn(Optional.of(testEvent));
            when(speakerPoolRepository.findByEventIdAndEmail(any(), any())).thenReturn(Optional.empty());
            when(userApiClient.getOrCreateUser(any(GetOrCreateUserRequest.class))).thenReturn(userResponse);
            when(speakerPoolRepository.save(any())).thenAnswer(inv -> {
                SpeakerPool sp = inv.getArgument(0);
                sp.setId(UUID.randomUUID());
                sp.setCreatedAt(Instant.now());
                return sp;
            });

            // When
            speakerInvitationService.inviteSpeaker(testEventCode, request);

            // Then
            ArgumentCaptor<GetOrCreateUserRequest> captor = ArgumentCaptor.forClass(GetOrCreateUserRequest.class);
            verify(userApiClient).getOrCreateUser(captor.capture());

            GetOrCreateUserRequest capturedRequest = captor.getValue();
            assertThat(capturedRequest.getEmail()).isEqualTo("john.doe@example.com");
            assertThat(capturedRequest.getFirstName()).isEqualTo("John");
            assertThat(capturedRequest.getLastName()).isEqualTo("Doe");
            assertThat(capturedRequest.getCompanyId()).isEqualTo("Test Company");
            assertThat(capturedRequest.getCognitoSync()).isFalse(); // Speakers don't need Cognito initially
        }

        @Test
        @DisplayName("6.1b-UNIT-008: should parse name correctly when full name provided")
        void should_parseNameCorrectly_when_fullNameProvided() {
            // Given
            InviteSpeakerRequest request = new InviteSpeakerRequest(
                    testEmail, "John", "Doe", null, null, null
            );

            // When
            String displayName = request.getDisplayName();

            // Then
            assertThat(displayName).isEqualTo("John Doe");
        }

        @Test
        @DisplayName("6.1b-UNIT-009: should use email prefix when no name provided")
        void should_useEmailPrefix_when_noNameProvided() {
            // Given
            InviteSpeakerRequest request = new InviteSpeakerRequest(
                    "speaker@example.com", null, null, null, null, null
            );

            // When
            String displayName = request.getDisplayName();

            // Then
            assertThat(displayName).isEqualTo("speaker@example.com");
        }

        @Test
        @DisplayName("should use first name only when last name not provided")
        void should_useFirstNameOnly_when_lastNameNotProvided() {
            // Given
            InviteSpeakerRequest request = new InviteSpeakerRequest(
                    testEmail, "John", null, null, null, null
            );

            // When
            String displayName = request.getDisplayName();

            // Then
            assertThat(displayName).isEqualTo("John");
        }
    }

    // ==========================================================================
    // AC3: Send Invitation Email
    // ==========================================================================

    @Nested
    @DisplayName("AC3: Send Invitation Email")
    class SendInvitationEmailTests {

        @Test
        @DisplayName("6.1b-UNIT-012: should build and send email when invitation triggered")
        void should_buildEmail_when_invitationTriggered() {
            // Given
            LocalDate responseDeadline = LocalDate.now().plusDays(14);
            SendInvitationRequest request = new SendInvitationRequest(responseDeadline, null, "de", null);

            when(eventRepository.findByEventCode(testEventCode)).thenReturn(Optional.of(testEvent));
            when(speakerPoolRepository.findByEventIdAndUsername(testEventId, testUsername))
                    .thenReturn(Optional.of(testSpeaker));
            when(magicLinkService.generateToken(any(), any())).thenReturn("test-token-123");
            when(speakerPoolRepository.save(any())).thenReturn(testSpeaker);
            when(securityContextHelper.getCurrentUsername()).thenReturn("organizer.test");

            // When
            speakerInvitationService.sendInvitation(testEventCode, testUsername, request);

            // Then
            verify(emailService).sendInvitationEmail(
                    any(), eq(testEvent), eq("test-token-123"), eq("test-token-123"), any());
        }

        @Test
        @DisplayName("6.1b-UNIT-013: should include magic link when email built")
        void should_includeMagicLink_when_emailBuilt() {
            // Given
            LocalDate responseDeadline = LocalDate.now().plusDays(14);
            SendInvitationRequest request = new SendInvitationRequest(responseDeadline, null, null, null);

            when(eventRepository.findByEventCode(testEventCode)).thenReturn(Optional.of(testEvent));
            when(speakerPoolRepository.findByEventIdAndUsername(testEventId, testUsername))
                    .thenReturn(Optional.of(testSpeaker));
            when(magicLinkService.generateToken(eq(testSpeakerId), any())).thenReturn("magic-token-xyz");
            when(speakerPoolRepository.save(any())).thenReturn(testSpeaker);
            when(securityContextHelper.getCurrentUsername()).thenReturn("organizer.test");

            // When
            speakerInvitationService.sendInvitation(testEventCode, testUsername, request);

            // Then
            verify(magicLinkService).generateToken(testSpeakerId, TokenAction.RESPOND);
            verify(magicLinkService).generateToken(testSpeakerId, TokenAction.VIEW);
            verify(emailService).sendInvitationEmail(
                    any(), any(), eq("magic-token-xyz"), eq("magic-token-xyz"), any());
        }

        @Test
        @DisplayName("should transition speaker to INVITED state when email sent")
        void should_transitionToInvited_when_emailSent() {
            // Given
            LocalDate responseDeadline = LocalDate.now().plusDays(14);
            SendInvitationRequest request = new SendInvitationRequest(responseDeadline, null, null, null);

            when(eventRepository.findByEventCode(testEventCode)).thenReturn(Optional.of(testEvent));
            when(speakerPoolRepository.findByEventIdAndUsername(testEventId, testUsername))
                    .thenReturn(Optional.of(testSpeaker));
            when(magicLinkService.generateToken(any(), any())).thenReturn("token");
            when(speakerPoolRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(securityContextHelper.getCurrentUsername()).thenReturn("organizer.test");

            // When
            SendInvitationResponse response = speakerInvitationService.sendInvitation(
                    testEventCode, testUsername, request
            );

            // Then
            assertThat(response.status()).isEqualTo(SpeakerWorkflowState.INVITED);

            ArgumentCaptor<SpeakerPool> captor = ArgumentCaptor.forClass(SpeakerPool.class);
            verify(speakerPoolRepository).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(SpeakerWorkflowState.INVITED);
            assertThat(captor.getValue().getInvitedAt()).isNotNull();
        }

        @Test
        @DisplayName("should throw SpeakerNotFoundException when speaker not found")
        void should_throwSpeakerNotFoundException_when_speakerNotFound() {
            // Given
            LocalDate responseDeadline = LocalDate.now().plusDays(14);
            SendInvitationRequest request = new SendInvitationRequest(responseDeadline, null, null, null);

            when(eventRepository.findByEventCode(testEventCode)).thenReturn(Optional.of(testEvent));
            when(speakerPoolRepository.findByEventIdAndUsername(testEventId, "unknown"))
                    .thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> speakerInvitationService.sendInvitation(testEventCode, "unknown", request))
                    .isInstanceOf(SpeakerNotFoundException.class);
        }
    }

    // ==========================================================================
    // AC5: Batch Invitation
    // ==========================================================================

    @Nested
    @DisplayName("AC5: Batch Invitation")
    class BatchInvitationTests {

        @Test
        @DisplayName("6.1b-INT-014: should return summary when batch completes")
        void should_returnSummary_when_batchCompletes() {
            // Given
            List<InviteSpeakerRequest> speakers = List.of(
                    new InviteSpeakerRequest("speaker1@test.com", "Speaker", "One", null, null, null),
                    new InviteSpeakerRequest("speaker2@test.com", "Speaker", "Two", null, null, null)
            );
            BatchInviteRequest request = new BatchInviteRequest(speakers);

            GetOrCreateUserResponse userResponse = new GetOrCreateUserResponse();
            userResponse.setUsername("speaker.one");
            userResponse.setCreated(true);

            when(eventRepository.existsByEventCode(testEventCode)).thenReturn(true);
            when(eventRepository.findByEventCode(testEventCode)).thenReturn(Optional.of(testEvent));
            when(speakerPoolRepository.findByEventIdAndEmail(any(), any())).thenReturn(Optional.empty());
            when(userApiClient.getOrCreateUser(any())).thenReturn(userResponse);
            when(speakerPoolRepository.save(any())).thenAnswer(inv -> {
                SpeakerPool sp = inv.getArgument(0);
                sp.setId(UUID.randomUUID());
                sp.setCreatedAt(Instant.now());
                return sp;
            });

            // When
            BatchInviteResponse response = speakerInvitationService.inviteBatch(testEventCode, request);

            // Then
            assertThat(response.totalRequested()).isEqualTo(2);
            assertThat(response.successCount()).isEqualTo(2);
            assertThat(response.failedCount()).isEqualTo(0);
            assertThat(response.results()).hasSize(2);
        }

        @Test
        @DisplayName("6.1b-INT-015: should continue processing when one invitation fails")
        void should_continueProcessing_when_oneInvitationFails() {
            // Given
            List<InviteSpeakerRequest> speakers = List.of(
                    new InviteSpeakerRequest("valid@test.com", "Valid", "Speaker", null, null, null),
                    new InviteSpeakerRequest("fail@test.com", "Will", "Fail", null, null, null)
            );
            BatchInviteRequest request = new BatchInviteRequest(speakers);

            GetOrCreateUserResponse userResponse = new GetOrCreateUserResponse();
            userResponse.setUsername("valid.speaker");
            userResponse.setCreated(true);

            when(eventRepository.existsByEventCode(testEventCode)).thenReturn(true);
            when(eventRepository.findByEventCode(testEventCode)).thenReturn(Optional.of(testEvent));
            when(speakerPoolRepository.findByEventIdAndEmail(any(), any())).thenReturn(Optional.empty());
            when(userApiClient.getOrCreateUser(any())).thenAnswer(inv -> {
                GetOrCreateUserRequest req = inv.getArgument(0);
                if (req.getEmail().contains("fail")) {
                    throw new RuntimeException("User service error");
                }
                return userResponse;
            });
            when(speakerPoolRepository.save(any())).thenAnswer(inv -> {
                SpeakerPool sp = inv.getArgument(0);
                sp.setId(UUID.randomUUID());
                sp.setCreatedAt(Instant.now());
                return sp;
            });

            // When
            BatchInviteResponse response = speakerInvitationService.inviteBatch(testEventCode, request);

            // Then
            assertThat(response.totalRequested()).isEqualTo(2);
            assertThat(response.successCount()).isEqualTo(1);
            assertThat(response.failedCount()).isEqualTo(1);
            assertThat(response.results()).hasSize(1);
            assertThat(response.errors()).hasSize(1);
            assertThat(response.errors().get(0).email()).isEqualTo("fail@test.com");
        }

        @Test
        @DisplayName("should throw EventNotFoundException when event not found for batch")
        void should_throwEventNotFoundException_when_eventNotFoundForBatch() {
            // Given
            List<InviteSpeakerRequest> speakers = List.of(
                    new InviteSpeakerRequest("speaker@test.com", "Test", "Speaker", null, null, null)
            );
            BatchInviteRequest request = new BatchInviteRequest(speakers);

            when(eventRepository.existsByEventCode("non-existent")).thenReturn(false);

            // When/Then
            assertThatThrownBy(() -> speakerInvitationService.inviteBatch("non-existent", request))
                    .isInstanceOf(EventNotFoundException.class);
        }
    }

    // ==========================================================================
    // History Tracking Tests
    // ==========================================================================

    @Nested
    @DisplayName("History Tracking: Outreach and Status History")
    class HistoryTrackingTests {

        @Test
        @DisplayName("should create outreach history when invitation email is sent")
        void should_createOutreachHistory_when_invitationEmailSent() {
            // Given
            LocalDate responseDeadline = LocalDate.now().plusDays(14);
            SendInvitationRequest request = new SendInvitationRequest(responseDeadline, null, null, null);

            when(eventRepository.findByEventCode(testEventCode)).thenReturn(Optional.of(testEvent));
            when(speakerPoolRepository.findByEventIdAndUsername(testEventId, testUsername))
                    .thenReturn(Optional.of(testSpeaker));
            when(magicLinkService.generateToken(any(), any())).thenReturn("token");
            when(speakerPoolRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(securityContextHelper.getCurrentUsername()).thenReturn("organizer.test");

            // When
            speakerInvitationService.sendInvitation(testEventCode, testUsername, request);

            // Then: Outreach history should be created
            ArgumentCaptor<OutreachHistory> outreachCaptor = ArgumentCaptor.forClass(OutreachHistory.class);
            verify(outreachHistoryRepository).save(outreachCaptor.capture());

            OutreachHistory savedOutreach = outreachCaptor.getValue();
            assertThat(savedOutreach.getSpeakerPoolId()).isEqualTo(testSpeakerId);
            assertThat(savedOutreach.getContactMethod()).isEqualTo("email");
            assertThat(savedOutreach.getNotes()).contains("Automated invitation email");
            assertThat(savedOutreach.getOrganizerUsername()).isEqualTo("organizer.test");
        }

        @Test
        @DisplayName("should create status history when speaker transitions to INVITED")
        void should_createStatusHistory_when_speakerTransitionsToInvited() {
            // Given: Speaker starts in IDENTIFIED state
            testSpeaker.setStatus(SpeakerWorkflowState.IDENTIFIED);

            LocalDate responseDeadline = LocalDate.now().plusDays(14);
            SendInvitationRequest request = new SendInvitationRequest(responseDeadline, null, null, null);

            when(eventRepository.findByEventCode(testEventCode)).thenReturn(Optional.of(testEvent));
            when(speakerPoolRepository.findByEventIdAndUsername(testEventId, testUsername))
                    .thenReturn(Optional.of(testSpeaker));
            when(magicLinkService.generateToken(any(), any())).thenReturn("token");
            when(speakerPoolRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(securityContextHelper.getCurrentUsername()).thenReturn("organizer.test");

            // When
            speakerInvitationService.sendInvitation(testEventCode, testUsername, request);

            // Then: Status history should be created
            ArgumentCaptor<SpeakerStatusHistory> statusCaptor = ArgumentCaptor.forClass(SpeakerStatusHistory.class);
            verify(statusHistoryRepository).save(statusCaptor.capture());

            SpeakerStatusHistory savedHistory = statusCaptor.getValue();
            assertThat(savedHistory.getSpeakerPoolId()).isEqualTo(testSpeakerId);
            assertThat(savedHistory.getEventId()).isEqualTo(testEventId);
            assertThat(savedHistory.getPreviousStatus()).isEqualTo(SpeakerWorkflowState.IDENTIFIED);
            assertThat(savedHistory.getNewStatus()).isEqualTo(SpeakerWorkflowState.INVITED);
            assertThat(savedHistory.getChangedByUsername()).isEqualTo("organizer.test");
            assertThat(savedHistory.getChangeReason()).isEqualTo("Invitation email sent");
        }

        @Test
        @DisplayName("should use 'system' as username when no user in security context")
        void should_useSystemUsername_when_noUserInSecurityContext() {
            // Given
            LocalDate responseDeadline = LocalDate.now().plusDays(14);
            SendInvitationRequest request = new SendInvitationRequest(responseDeadline, null, null, null);

            when(eventRepository.findByEventCode(testEventCode)).thenReturn(Optional.of(testEvent));
            when(speakerPoolRepository.findByEventIdAndUsername(testEventId, testUsername))
                    .thenReturn(Optional.of(testSpeaker));
            when(magicLinkService.generateToken(any(), any())).thenReturn("token");
            when(speakerPoolRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(securityContextHelper.getCurrentUsername()).thenReturn(null); // No user

            // When
            speakerInvitationService.sendInvitation(testEventCode, testUsername, request);

            // Then: Should use "system" as organizer username
            ArgumentCaptor<OutreachHistory> outreachCaptor = ArgumentCaptor.forClass(OutreachHistory.class);
            verify(outreachHistoryRepository).save(outreachCaptor.capture());
            assertThat(outreachCaptor.getValue().getOrganizerUsername()).isEqualTo("system");

            ArgumentCaptor<SpeakerStatusHistory> statusCaptor = ArgumentCaptor.forClass(SpeakerStatusHistory.class);
            verify(statusHistoryRepository).save(statusCaptor.capture());
            assertThat(statusCaptor.getValue().getChangedByUsername()).isEqualTo("system");
        }

        @Test
        @DisplayName("should not create duplicate status history when already INVITED")
        void should_notCreateDuplicateStatusHistory_when_alreadyInvited() {
            // Given: Speaker is already INVITED
            testSpeaker.setStatus(SpeakerWorkflowState.INVITED);

            LocalDate responseDeadline = LocalDate.now().plusDays(14);
            SendInvitationRequest request = new SendInvitationRequest(responseDeadline, null, null, null);

            when(eventRepository.findByEventCode(testEventCode)).thenReturn(Optional.of(testEvent));
            when(speakerPoolRepository.findByEventIdAndUsername(testEventId, testUsername))
                    .thenReturn(Optional.of(testSpeaker));
            when(magicLinkService.generateToken(any(), any())).thenReturn("token");
            when(speakerPoolRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(securityContextHelper.getCurrentUsername()).thenReturn("organizer.test");

            // When
            speakerInvitationService.sendInvitation(testEventCode, testUsername, request);

            // Then: Outreach history should still be created
            verify(outreachHistoryRepository).save(any(OutreachHistory.class));

            // But status history should NOT be created (no status change)
            verify(statusHistoryRepository, never()).save(any(SpeakerStatusHistory.class));
        }
    }

    // ==========================================================================
    // AC6: Domain Events
    // ==========================================================================

    @Nested
    @DisplayName("AC6: Domain Events")
    class DomainEventsTests {

        @Test
        @DisplayName("6.1b-INT-18: should publish event when invitation sent")
        void should_publishEvent_when_invitationSent() {
            // Given
            LocalDate responseDeadline = LocalDate.now().plusDays(14);
            SendInvitationRequest request = new SendInvitationRequest(responseDeadline, null, null, null);

            when(eventRepository.findByEventCode(testEventCode)).thenReturn(Optional.of(testEvent));
            when(speakerPoolRepository.findByEventIdAndUsername(testEventId, testUsername))
                    .thenReturn(Optional.of(testSpeaker));
            when(magicLinkService.generateToken(any(), any())).thenReturn("token");
            when(speakerPoolRepository.save(any())).thenReturn(testSpeaker);
            when(securityContextHelper.getCurrentUsername()).thenReturn("organizer.test");

            // When
            speakerInvitationService.sendInvitation(testEventCode, testUsername, request);

            // Then
            ArgumentCaptor<SpeakerInvitationSentEvent> captor =
                    ArgumentCaptor.forClass(SpeakerInvitationSentEvent.class);
            verify(eventPublisher).publishEvent(captor.capture());

            SpeakerInvitationSentEvent publishedEvent = captor.getValue();
            assertThat(publishedEvent.getSpeakerPoolId()).isEqualTo(testSpeakerId);
            assertThat(publishedEvent.getEventCode()).isEqualTo(testEventCode);
            assertThat(publishedEvent.getUsername()).isEqualTo(testUsername);
            assertThat(publishedEvent.getEmail()).isEqualTo(testEmail);
        }
    }
}
