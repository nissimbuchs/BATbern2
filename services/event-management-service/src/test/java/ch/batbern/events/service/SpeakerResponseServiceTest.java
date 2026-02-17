package ch.batbern.events.service;

import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.events.domain.SpeakerStatusHistory;
import ch.batbern.events.repository.SpeakerStatusHistoryRepository;
import ch.batbern.events.dto.SpeakerResponseRequest;
import ch.batbern.events.dto.SpeakerResponsePreferences;
import ch.batbern.events.dto.SpeakerResponseResult;
import ch.batbern.events.dto.TokenValidationResult;
import ch.batbern.events.exception.AlreadyRespondedException;
import ch.batbern.events.exception.InvalidTokenException;
import ch.batbern.shared.exception.ValidationException;
import ch.batbern.events.client.UserApiClient;
import ch.batbern.events.dto.generated.users.GetOrCreateUserResponse;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.SpeakerPoolRepository;
import ch.batbern.events.repository.SpeakerRepository;
import ch.batbern.shared.events.SpeakerResponseReceivedEvent;
import ch.batbern.shared.types.SpeakerResponseType;
import ch.batbern.shared.types.SpeakerWorkflowState;
import ch.batbern.shared.types.TokenAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for SpeakerResponseService
 * Story 6.2a: Invitation Response Portal - Task 1 (RED Phase)
 *
 * RED PHASE (TDD): These tests will FAIL until SpeakerResponseService is implemented.
 *
 * Tests cover:
 * - AC3: Accept response flow (workflow transition, token consumption, preferences)
 * - AC4: Decline response flow (reason required, workflow transition)
 * - AC5: Tentative response flow (reason required, token NOT consumed)
 * - AC6: Organizer notification (domain event publishing)
 * - AC7: Already responded handling
 */
@ExtendWith(MockitoExtension.class)
class SpeakerResponseServiceTest {

    @Mock
    private SpeakerPoolRepository speakerPoolRepository;

    @Mock
    private SpeakerRepository speakerRepository;

    @Mock
    private EventRepository eventRepository;

    @Mock
    private MagicLinkService magicLinkService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private OrganizerNotificationService notificationService;

    @Mock
    private UserApiClient userApiClient;

    @Mock
    private SpeakerAcceptanceEmailService acceptanceEmailService;

    @Mock
    private SpeakerStatusHistoryRepository statusHistoryRepository;

    @Mock
    private SpeakerAccountCreationService speakerAccountCreationService;

    private SpeakerResponseService speakerResponseService;

    private UUID testSpeakerPoolId;
    private UUID testEventId;
    private SpeakerPool testSpeakerPool;
    private Event testEvent;
    private String validToken;

    @BeforeEach
    void setUp() {
        speakerResponseService = new SpeakerResponseService(
                speakerPoolRepository,
                speakerRepository,
                eventRepository,
                magicLinkService,
                eventPublisher,
                notificationService,
                userApiClient,
                acceptanceEmailService,
                statusHistoryRepository,
                speakerAccountCreationService
        );

        testSpeakerPoolId = UUID.randomUUID();
        testEventId = UUID.randomUUID();
        validToken = "valid-test-token-abc123";

        testEvent = Event.builder()
                .id(testEventId)
                .eventCode("BAT2025Q1")
                .title("BATbern Q1 2025")
                .date(Instant.parse("2025-03-15T18:00:00Z"))
                .build();

        testSpeakerPool = SpeakerPool.builder()
                .id(testSpeakerPoolId)
                .eventId(testEventId)
                .username("jane.speaker")
                .speakerName("Jane Speaker")
                .company("Tech Corp")
                .email("jane@techcorp.ch")
                .status(SpeakerWorkflowState.INVITED)
                .invitedAt(Instant.now().minus(5, ChronoUnit.DAYS))
                .responseDeadline(LocalDate.now().plusDays(10))
                .contentDeadline(LocalDate.now().plusDays(30))
                .createdAt(Instant.now().minus(10, ChronoUnit.DAYS))
                .updatedAt(Instant.now().minus(5, ChronoUnit.DAYS))
                .build();

        // Default mock for UserApiClient (used when speaker accepts)
        // Use lenient() since this mock is only used in Accept tests
        GetOrCreateUserResponse userResponse = new GetOrCreateUserResponse();
        userResponse.setUsername("jane.speaker");
        userResponse.setCreated(false);
        lenient().when(userApiClient.getOrCreateUser(any())).thenReturn(userResponse);

        // Default mock for SpeakerRepository (speaker record creation)
        // Use lenient() since this mock is only used in Accept tests
        lenient().when(speakerRepository.findByUsername(anyString())).thenReturn(Optional.empty());

        // Default mock for SpeakerAccountCreationService (Story 9.2): return null (EXTENDED or error)
        // Use lenient() since this mock is only relevant in Accept tests
        lenient().when(speakerAccountCreationService.processInvitationAcceptance(any()))
                .thenReturn(null);
    }

    // ==================== AC3: Accept Response Flow Tests ====================

    @Nested
    @DisplayName("AC3: Accept Response Flow")
    class AcceptResponseTests {

        /**
         * Test 1.1: Should transition workflow state to ACCEPTED when accept response
         * AC3: workflow_state transitions to ACCEPTED
         */
        @Test
        void should_transitionToAccepted_when_acceptResponse() {
            // Given
            when(magicLinkService.validateToken(validToken))
                    .thenReturn(TokenValidationResult.valid(testSpeakerPoolId, "jane.speaker", "BAT2025Q1", TokenAction.RESPOND));
            when(speakerPoolRepository.findById(testSpeakerPoolId))
                    .thenReturn(Optional.of(testSpeakerPool));
            when(eventRepository.findById(testEventId))
                    .thenReturn(Optional.of(testEvent));
            when(speakerPoolRepository.save(any(SpeakerPool.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            SpeakerResponseRequest request = SpeakerResponseRequest.builder()
                    .token(validToken)
                    .response(SpeakerResponseType.ACCEPT)
                    .build();

            // When
            SpeakerResponseResult result = speakerResponseService.processResponse(request);

            // Then
            ArgumentCaptor<SpeakerPool> captor = ArgumentCaptor.forClass(SpeakerPool.class);
            verify(speakerPoolRepository).save(captor.capture());

            SpeakerPool savedSpeakerPool = captor.getValue();
            assertThat(savedSpeakerPool.getStatus()).isEqualTo(SpeakerWorkflowState.ACCEPTED);
            assertThat(result.isSuccess()).isTrue();
        }

        /**
         * Test 1.2: Should set accepted_at timestamp when accept response
         * AC3: accepted_at timestamp is set
         */
        @Test
        void should_setAcceptedAtTimestamp_when_acceptResponse() {
            // Given
            when(magicLinkService.validateToken(validToken))
                    .thenReturn(TokenValidationResult.valid(testSpeakerPoolId, "jane.speaker", "BAT2025Q1", TokenAction.RESPOND));
            when(speakerPoolRepository.findById(testSpeakerPoolId))
                    .thenReturn(Optional.of(testSpeakerPool));
            when(eventRepository.findById(testEventId))
                    .thenReturn(Optional.of(testEvent));
            when(speakerPoolRepository.save(any(SpeakerPool.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            Instant before = Instant.now();

            SpeakerResponseRequest request = SpeakerResponseRequest.builder()
                    .token(validToken)
                    .response(SpeakerResponseType.ACCEPT)
                    .build();

            // When
            speakerResponseService.processResponse(request);

            Instant after = Instant.now();

            // Then
            ArgumentCaptor<SpeakerPool> captor = ArgumentCaptor.forClass(SpeakerPool.class);
            verify(speakerPoolRepository).save(captor.capture());

            SpeakerPool savedSpeakerPool = captor.getValue();
            assertThat(savedSpeakerPool.getAcceptedAt()).isNotNull();
            assertThat(savedSpeakerPool.getAcceptedAt()).isAfterOrEqualTo(before);
            assertThat(savedSpeakerPool.getAcceptedAt()).isBeforeOrEqualTo(after);
        }

        /**
         * Test 1.3: Should store preferences when accept with preferences
         * AC3: Preferences are stored (time slot, travel, tech requirements)
         */
        @Test
        void should_storePreferences_when_acceptWithPreferences() {
            // Given
            when(magicLinkService.validateToken(validToken))
                    .thenReturn(TokenValidationResult.valid(testSpeakerPoolId, "jane.speaker", "BAT2025Q1", TokenAction.RESPOND));
            when(speakerPoolRepository.findById(testSpeakerPoolId))
                    .thenReturn(Optional.of(testSpeakerPool));
            when(eventRepository.findById(testEventId))
                    .thenReturn(Optional.of(testEvent));
            when(speakerPoolRepository.save(any(SpeakerPool.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            SpeakerResponsePreferences preferences = SpeakerResponsePreferences.builder()
                    .timeSlot("morning")
                    .travelRequirements("local")
                    .technicalRequirements(new String[]{"mac_adapter", "remote_option"})
                    .initialTitle("Cloud Architecture Patterns")
                    .comments("Looking forward to it!")
                    .build();

            SpeakerResponseRequest request = SpeakerResponseRequest.builder()
                    .token(validToken)
                    .response(SpeakerResponseType.ACCEPT)
                    .preferences(preferences)
                    .build();

            // When
            speakerResponseService.processResponse(request);

            // Then
            ArgumentCaptor<SpeakerPool> captor = ArgumentCaptor.forClass(SpeakerPool.class);
            verify(speakerPoolRepository).save(captor.capture());

            SpeakerPool savedSpeakerPool = captor.getValue();
            assertThat(savedSpeakerPool.getPreferredTimeSlot()).isEqualTo("morning");
            assertThat(savedSpeakerPool.getTravelRequirements()).isEqualTo("local");
            assertThat(savedSpeakerPool.getTechnicalRequirements()).contains("mac_adapter", "remote_option");
            assertThat(savedSpeakerPool.getInitialPresentationTitle()).isEqualTo("Cloud Architecture Patterns");
            assertThat(savedSpeakerPool.getPreferenceComments()).isEqualTo("Looking forward to it!");
        }

        /**
         * Test 1.4: Should consume token when accept response
         * AC3: Token is marked as used (single-use enforcement)
         */
        @Test
        void should_consumeToken_when_acceptResponse() {
            // Given
            when(magicLinkService.validateToken(validToken))
                    .thenReturn(TokenValidationResult.valid(testSpeakerPoolId, "jane.speaker", "BAT2025Q1", TokenAction.RESPOND));
            when(speakerPoolRepository.findById(testSpeakerPoolId))
                    .thenReturn(Optional.of(testSpeakerPool));
            when(eventRepository.findById(testEventId))
                    .thenReturn(Optional.of(testEvent));
            when(speakerPoolRepository.save(any(SpeakerPool.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            SpeakerResponseRequest request = SpeakerResponseRequest.builder()
                    .token(validToken)
                    .response(SpeakerResponseType.ACCEPT)
                    .build();

            // When
            speakerResponseService.processResponse(request);

            // Then
            verify(magicLinkService).markTokenAsUsed(validToken);
        }

        /**
         * Test 1.5: Should return next steps when accept response
         * AC3: Success page shows next steps and content deadline
         */
        @Test
        void should_returnNextSteps_when_acceptResponse() {
            // Given
            when(magicLinkService.validateToken(validToken))
                    .thenReturn(TokenValidationResult.valid(testSpeakerPoolId, "jane.speaker", "BAT2025Q1", TokenAction.RESPOND));
            when(speakerPoolRepository.findById(testSpeakerPoolId))
                    .thenReturn(Optional.of(testSpeakerPool));
            when(eventRepository.findById(testEventId))
                    .thenReturn(Optional.of(testEvent));
            when(speakerPoolRepository.save(any(SpeakerPool.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            SpeakerResponseRequest request = SpeakerResponseRequest.builder()
                    .token(validToken)
                    .response(SpeakerResponseType.ACCEPT)
                    .build();

            // When
            SpeakerResponseResult result = speakerResponseService.processResponse(request);

            // Then
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getSpeakerName()).isEqualTo("Jane Speaker");
            assertThat(result.getEventName()).isEqualTo("BATbern Q1 2025");
            assertThat(result.getNextSteps()).isNotEmpty();
            assertThat(result.getContentDeadline()).isEqualTo(testSpeakerPool.getContentDeadline());
        }

        /**
         * Test 1.6: Should clear tentative flag when accept response (if previously tentative)
         * AC3: is_tentative set to false
         */
        @Test
        void should_clearTentativeFlag_when_acceptResponseAfterTentative() {
            // Given
            testSpeakerPool.setIsTentative(true);
            testSpeakerPool.setTentativeReason("Was checking dates");

            when(magicLinkService.validateToken(validToken))
                    .thenReturn(TokenValidationResult.valid(testSpeakerPoolId, "jane.speaker", "BAT2025Q1", TokenAction.RESPOND));
            when(speakerPoolRepository.findById(testSpeakerPoolId))
                    .thenReturn(Optional.of(testSpeakerPool));
            when(eventRepository.findById(testEventId))
                    .thenReturn(Optional.of(testEvent));
            when(speakerPoolRepository.save(any(SpeakerPool.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            SpeakerResponseRequest request = SpeakerResponseRequest.builder()
                    .token(validToken)
                    .response(SpeakerResponseType.ACCEPT)
                    .build();

            // When
            speakerResponseService.processResponse(request);

            // Then
            ArgumentCaptor<SpeakerPool> captor = ArgumentCaptor.forClass(SpeakerPool.class);
            verify(speakerPoolRepository).save(captor.capture());

            SpeakerPool savedSpeakerPool = captor.getValue();
            assertThat(savedSpeakerPool.getIsTentative()).isFalse();
        }
    }

    // ==================== AC4: Decline Response Flow Tests ====================

    @Nested
    @DisplayName("AC4: Decline Response Flow")
    class DeclineResponseTests {

        /**
         * Test 2.1: Should transition workflow state to DECLINED when decline response
         * AC4: workflow_state transitions to DECLINED (terminal state)
         */
        @Test
        void should_transitionToDeclined_when_declineResponse() {
            // Given
            when(magicLinkService.validateToken(validToken))
                    .thenReturn(TokenValidationResult.valid(testSpeakerPoolId, "jane.speaker", "BAT2025Q1", TokenAction.RESPOND));
            when(speakerPoolRepository.findById(testSpeakerPoolId))
                    .thenReturn(Optional.of(testSpeakerPool));
            when(eventRepository.findById(testEventId))
                    .thenReturn(Optional.of(testEvent));
            when(speakerPoolRepository.save(any(SpeakerPool.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            SpeakerResponseRequest request = SpeakerResponseRequest.builder()
                    .token(validToken)
                    .response(SpeakerResponseType.DECLINE)
                    .reason("Schedule conflict with another conference")
                    .build();

            // When
            SpeakerResponseResult result = speakerResponseService.processResponse(request);

            // Then
            ArgumentCaptor<SpeakerPool> captor = ArgumentCaptor.forClass(SpeakerPool.class);
            verify(speakerPoolRepository).save(captor.capture());

            SpeakerPool savedSpeakerPool = captor.getValue();
            assertThat(savedSpeakerPool.getStatus()).isEqualTo(SpeakerWorkflowState.DECLINED);
            assertThat(result.isSuccess()).isTrue();
        }

        /**
         * Test 2.2: Should require reason when decline response
         * AC4: Reason is required - validation error if empty
         */
        @Test
        void should_requireReason_when_declineWithoutReason() {
            // Given
            when(magicLinkService.validateToken(validToken))
                    .thenReturn(TokenValidationResult.valid(testSpeakerPoolId, "jane.speaker", "BAT2025Q1", TokenAction.RESPOND));
            when(speakerPoolRepository.findById(testSpeakerPoolId))
                    .thenReturn(Optional.of(testSpeakerPool));

            SpeakerResponseRequest request = SpeakerResponseRequest.builder()
                    .token(validToken)
                    .response(SpeakerResponseType.DECLINE)
                    .reason(null) // No reason provided
                    .build();

            // When/Then
            assertThatThrownBy(() -> speakerResponseService.processResponse(request))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Reason is required for decline");
        }

        /**
         * Test 2.3: Should require reason when decline with blank reason
         * AC4: Reason is required - validation error if blank
         */
        @Test
        void should_requireReason_when_declineWithBlankReason() {
            // Given
            when(magicLinkService.validateToken(validToken))
                    .thenReturn(TokenValidationResult.valid(testSpeakerPoolId, "jane.speaker", "BAT2025Q1", TokenAction.RESPOND));
            when(speakerPoolRepository.findById(testSpeakerPoolId))
                    .thenReturn(Optional.of(testSpeakerPool));

            SpeakerResponseRequest request = SpeakerResponseRequest.builder()
                    .token(validToken)
                    .response(SpeakerResponseType.DECLINE)
                    .reason("   ") // Blank reason
                    .build();

            // When/Then
            assertThatThrownBy(() -> speakerResponseService.processResponse(request))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Reason is required for decline");
        }

        /**
         * Test 2.4: Should store decline reason when decline response
         * AC4: decline_reason is stored
         */
        @Test
        void should_storeDeclineReason_when_declineResponse() {
            // Given
            String declineReason = "Schedule conflict with another conference";

            when(magicLinkService.validateToken(validToken))
                    .thenReturn(TokenValidationResult.valid(testSpeakerPoolId, "jane.speaker", "BAT2025Q1", TokenAction.RESPOND));
            when(speakerPoolRepository.findById(testSpeakerPoolId))
                    .thenReturn(Optional.of(testSpeakerPool));
            when(eventRepository.findById(testEventId))
                    .thenReturn(Optional.of(testEvent));
            when(speakerPoolRepository.save(any(SpeakerPool.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            SpeakerResponseRequest request = SpeakerResponseRequest.builder()
                    .token(validToken)
                    .response(SpeakerResponseType.DECLINE)
                    .reason(declineReason)
                    .build();

            // When
            speakerResponseService.processResponse(request);

            // Then
            ArgumentCaptor<SpeakerPool> captor = ArgumentCaptor.forClass(SpeakerPool.class);
            verify(speakerPoolRepository).save(captor.capture());

            SpeakerPool savedSpeakerPool = captor.getValue();
            assertThat(savedSpeakerPool.getDeclineReason()).isEqualTo(declineReason);
        }

        /**
         * Test 2.5: Should set declined_at timestamp when decline response
         * AC4: declined_at timestamp is set
         */
        @Test
        void should_setDeclinedAtTimestamp_when_declineResponse() {
            // Given
            when(magicLinkService.validateToken(validToken))
                    .thenReturn(TokenValidationResult.valid(testSpeakerPoolId, "jane.speaker", "BAT2025Q1", TokenAction.RESPOND));
            when(speakerPoolRepository.findById(testSpeakerPoolId))
                    .thenReturn(Optional.of(testSpeakerPool));
            when(eventRepository.findById(testEventId))
                    .thenReturn(Optional.of(testEvent));
            when(speakerPoolRepository.save(any(SpeakerPool.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            Instant before = Instant.now();

            SpeakerResponseRequest request = SpeakerResponseRequest.builder()
                    .token(validToken)
                    .response(SpeakerResponseType.DECLINE)
                    .reason("Schedule conflict")
                    .build();

            // When
            speakerResponseService.processResponse(request);

            Instant after = Instant.now();

            // Then
            ArgumentCaptor<SpeakerPool> captor = ArgumentCaptor.forClass(SpeakerPool.class);
            verify(speakerPoolRepository).save(captor.capture());

            SpeakerPool savedSpeakerPool = captor.getValue();
            assertThat(savedSpeakerPool.getDeclinedAt()).isNotNull();
            assertThat(savedSpeakerPool.getDeclinedAt()).isAfterOrEqualTo(before);
            assertThat(savedSpeakerPool.getDeclinedAt()).isBeforeOrEqualTo(after);
        }

        /**
         * Test 2.6: Should consume token when decline response
         * AC4: Token is marked as used (single-use enforcement)
         */
        @Test
        void should_consumeToken_when_declineResponse() {
            // Given
            when(magicLinkService.validateToken(validToken))
                    .thenReturn(TokenValidationResult.valid(testSpeakerPoolId, "jane.speaker", "BAT2025Q1", TokenAction.RESPOND));
            when(speakerPoolRepository.findById(testSpeakerPoolId))
                    .thenReturn(Optional.of(testSpeakerPool));
            when(eventRepository.findById(testEventId))
                    .thenReturn(Optional.of(testEvent));
            when(speakerPoolRepository.save(any(SpeakerPool.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            SpeakerResponseRequest request = SpeakerResponseRequest.builder()
                    .token(validToken)
                    .response(SpeakerResponseType.DECLINE)
                    .reason("Cannot attend")
                    .build();

            // When
            speakerResponseService.processResponse(request);

            // Then
            verify(magicLinkService).markTokenAsUsed(validToken);
        }
    }

    // ==================== AC5: Tentative Response Flow Tests ====================

    @Nested
    @DisplayName("AC5: Tentative Response Flow")
    class TentativeResponseTests {

        /**
         * Test 3.1: Should set tentative flag when tentative response
         * AC5: is_tentative set to true
         */
        @Test
        void should_setTentativeFlag_when_tentativeResponse() {
            // Given
            when(magicLinkService.validateToken(validToken))
                    .thenReturn(TokenValidationResult.valid(testSpeakerPoolId, "jane.speaker", "BAT2025Q1", TokenAction.RESPOND));
            when(speakerPoolRepository.findById(testSpeakerPoolId))
                    .thenReturn(Optional.of(testSpeakerPool));
            when(eventRepository.findById(testEventId))
                    .thenReturn(Optional.of(testEvent));
            when(speakerPoolRepository.save(any(SpeakerPool.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            SpeakerResponseRequest request = SpeakerResponseRequest.builder()
                    .token(validToken)
                    .response(SpeakerResponseType.TENTATIVE)
                    .reason("Need to check travel dates")
                    .build();

            // When
            SpeakerResponseResult result = speakerResponseService.processResponse(request);

            // Then
            ArgumentCaptor<SpeakerPool> captor = ArgumentCaptor.forClass(SpeakerPool.class);
            verify(speakerPoolRepository).save(captor.capture());

            SpeakerPool savedSpeakerPool = captor.getValue();
            assertThat(savedSpeakerPool.getIsTentative()).isTrue();
            assertThat(result.isSuccess()).isTrue();
        }

        /**
         * Test 3.2: Should keep workflow state as INVITED when tentative response
         * AC5: workflow_state stays INVITED
         */
        @Test
        void should_keepWorkflowStateInvited_when_tentativeResponse() {
            // Given
            when(magicLinkService.validateToken(validToken))
                    .thenReturn(TokenValidationResult.valid(testSpeakerPoolId, "jane.speaker", "BAT2025Q1", TokenAction.RESPOND));
            when(speakerPoolRepository.findById(testSpeakerPoolId))
                    .thenReturn(Optional.of(testSpeakerPool));
            when(eventRepository.findById(testEventId))
                    .thenReturn(Optional.of(testEvent));
            when(speakerPoolRepository.save(any(SpeakerPool.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            SpeakerResponseRequest request = SpeakerResponseRequest.builder()
                    .token(validToken)
                    .response(SpeakerResponseType.TENTATIVE)
                    .reason("Awaiting budget approval")
                    .build();

            // When
            speakerResponseService.processResponse(request);

            // Then
            ArgumentCaptor<SpeakerPool> captor = ArgumentCaptor.forClass(SpeakerPool.class);
            verify(speakerPoolRepository).save(captor.capture());

            SpeakerPool savedSpeakerPool = captor.getValue();
            assertThat(savedSpeakerPool.getStatus()).isEqualTo(SpeakerWorkflowState.INVITED);
        }

        /**
         * Test 3.3: Should store tentative reason when tentative response
         * AC5: tentative_reason is stored
         */
        @Test
        void should_storeTentativeReason_when_tentativeResponse() {
            // Given
            String tentativeReason = "Need to check travel dates";

            when(magicLinkService.validateToken(validToken))
                    .thenReturn(TokenValidationResult.valid(testSpeakerPoolId, "jane.speaker", "BAT2025Q1", TokenAction.RESPOND));
            when(speakerPoolRepository.findById(testSpeakerPoolId))
                    .thenReturn(Optional.of(testSpeakerPool));
            when(eventRepository.findById(testEventId))
                    .thenReturn(Optional.of(testEvent));
            when(speakerPoolRepository.save(any(SpeakerPool.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            SpeakerResponseRequest request = SpeakerResponseRequest.builder()
                    .token(validToken)
                    .response(SpeakerResponseType.TENTATIVE)
                    .reason(tentativeReason)
                    .build();

            // When
            speakerResponseService.processResponse(request);

            // Then
            ArgumentCaptor<SpeakerPool> captor = ArgumentCaptor.forClass(SpeakerPool.class);
            verify(speakerPoolRepository).save(captor.capture());

            SpeakerPool savedSpeakerPool = captor.getValue();
            assertThat(savedSpeakerPool.getTentativeReason()).isEqualTo(tentativeReason);
        }

        /**
         * Test 3.4: Should require reason when tentative response
         * AC5: Reason is required for tentative
         */
        @Test
        void should_requireReason_when_tentativeWithoutReason() {
            // Given
            when(magicLinkService.validateToken(validToken))
                    .thenReturn(TokenValidationResult.valid(testSpeakerPoolId, "jane.speaker", "BAT2025Q1", TokenAction.RESPOND));
            when(speakerPoolRepository.findById(testSpeakerPoolId))
                    .thenReturn(Optional.of(testSpeakerPool));

            SpeakerResponseRequest request = SpeakerResponseRequest.builder()
                    .token(validToken)
                    .response(SpeakerResponseType.TENTATIVE)
                    .reason(null) // No reason provided
                    .build();

            // When/Then
            assertThatThrownBy(() -> speakerResponseService.processResponse(request))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Reason is required for tentative response");
        }

        /**
         * Test 3.5: Should NOT consume token when tentative response
         * AC5: Token is NOT consumed (speaker can return and change response)
         */
        @Test
        void should_notConsumeToken_when_tentativeResponse() {
            // Given
            when(magicLinkService.validateToken(validToken))
                    .thenReturn(TokenValidationResult.valid(testSpeakerPoolId, "jane.speaker", "BAT2025Q1", TokenAction.RESPOND));
            when(speakerPoolRepository.findById(testSpeakerPoolId))
                    .thenReturn(Optional.of(testSpeakerPool));
            when(eventRepository.findById(testEventId))
                    .thenReturn(Optional.of(testEvent));
            when(speakerPoolRepository.save(any(SpeakerPool.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            SpeakerResponseRequest request = SpeakerResponseRequest.builder()
                    .token(validToken)
                    .response(SpeakerResponseType.TENTATIVE)
                    .reason("Checking calendar")
                    .build();

            // When
            speakerResponseService.processResponse(request);

            // Then - Token should NOT be marked as used
            verify(magicLinkService, never()).markTokenAsUsed(anyString());
        }
    }

    // ==================== AC6: Domain Event Publishing Tests ====================

    @Nested
    @DisplayName("AC6: Organizer Notification")
    class OrganizerNotificationTests {

        /**
         * Test 4.1: Should publish domain event when accept response
         * AC6: On any response, emit SpeakerResponseReceivedEvent domain event
         */
        @Test
        void should_publishDomainEvent_when_acceptResponse() {
            // Given
            when(magicLinkService.validateToken(validToken))
                    .thenReturn(TokenValidationResult.valid(testSpeakerPoolId, "jane.speaker", "BAT2025Q1", TokenAction.RESPOND));
            when(speakerPoolRepository.findById(testSpeakerPoolId))
                    .thenReturn(Optional.of(testSpeakerPool));
            when(eventRepository.findById(testEventId))
                    .thenReturn(Optional.of(testEvent));
            when(speakerPoolRepository.save(any(SpeakerPool.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            SpeakerResponseRequest request = SpeakerResponseRequest.builder()
                    .token(validToken)
                    .response(SpeakerResponseType.ACCEPT)
                    .build();

            // When
            speakerResponseService.processResponse(request);

            // Then
            ArgumentCaptor<SpeakerResponseReceivedEvent> eventCaptor =
                    ArgumentCaptor.forClass(SpeakerResponseReceivedEvent.class);
            verify(eventPublisher).publishEvent(eventCaptor.capture());

            SpeakerResponseReceivedEvent publishedEvent = eventCaptor.getValue();
            assertThat(publishedEvent.getSpeakerPoolId()).isEqualTo(testSpeakerPoolId);
            assertThat(publishedEvent.getUsername()).isEqualTo("jane.speaker");
            assertThat(publishedEvent.getEventCode()).isEqualTo("BAT2025Q1");
            assertThat(publishedEvent.getResponseType()).isEqualTo(SpeakerResponseType.ACCEPT);
        }

        /**
         * Test 4.2: Should publish domain event when decline response
         * AC6: On any response, emit SpeakerResponseReceivedEvent domain event
         */
        @Test
        void should_publishDomainEvent_when_declineResponse() {
            // Given
            String declineReason = "Cannot attend due to conflict";

            when(magicLinkService.validateToken(validToken))
                    .thenReturn(TokenValidationResult.valid(testSpeakerPoolId, "jane.speaker", "BAT2025Q1", TokenAction.RESPOND));
            when(speakerPoolRepository.findById(testSpeakerPoolId))
                    .thenReturn(Optional.of(testSpeakerPool));
            when(eventRepository.findById(testEventId))
                    .thenReturn(Optional.of(testEvent));
            when(speakerPoolRepository.save(any(SpeakerPool.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            SpeakerResponseRequest request = SpeakerResponseRequest.builder()
                    .token(validToken)
                    .response(SpeakerResponseType.DECLINE)
                    .reason(declineReason)
                    .build();

            // When
            speakerResponseService.processResponse(request);

            // Then
            ArgumentCaptor<SpeakerResponseReceivedEvent> eventCaptor =
                    ArgumentCaptor.forClass(SpeakerResponseReceivedEvent.class);
            verify(eventPublisher).publishEvent(eventCaptor.capture());

            SpeakerResponseReceivedEvent publishedEvent = eventCaptor.getValue();
            assertThat(publishedEvent.getResponseType()).isEqualTo(SpeakerResponseType.DECLINE);
            assertThat(publishedEvent.getReason()).isEqualTo(declineReason);
        }

        /**
         * Test 4.3: Should notify organizer when response received
         * AC6: Send email notification to event organizer(s)
         */
        @Test
        void should_notifyOrganizer_when_responseReceived() {
            // Given
            when(magicLinkService.validateToken(validToken))
                    .thenReturn(TokenValidationResult.valid(testSpeakerPoolId, "jane.speaker", "BAT2025Q1", TokenAction.RESPOND));
            when(speakerPoolRepository.findById(testSpeakerPoolId))
                    .thenReturn(Optional.of(testSpeakerPool));
            when(eventRepository.findById(testEventId))
                    .thenReturn(Optional.of(testEvent));
            when(speakerPoolRepository.save(any(SpeakerPool.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            SpeakerResponseRequest request = SpeakerResponseRequest.builder()
                    .token(validToken)
                    .response(SpeakerResponseType.ACCEPT)
                    .build();

            // When
            speakerResponseService.processResponse(request);

            // Then
            verify(notificationService).notifyOrganizerOfResponse(
                    any(SpeakerPool.class),
                    any(Event.class),
                    any(SpeakerResponseType.class)
            );
        }
    }

    // ==================== Token Validation Error Tests ====================

    @Nested
    @DisplayName("Token Validation Errors")
    class TokenValidationErrorTests {

        /**
         * Test 5.1: Should throw InvalidTokenException when token not found
         * AC1: Invalid token shows error
         */
        @Test
        void should_throwInvalidTokenException_when_tokenNotFound() {
            // Given
            when(magicLinkService.validateToken(validToken))
                    .thenReturn(TokenValidationResult.notFound());

            SpeakerResponseRequest request = SpeakerResponseRequest.builder()
                    .token(validToken)
                    .response(SpeakerResponseType.ACCEPT)
                    .build();

            // When/Then
            assertThatThrownBy(() -> speakerResponseService.processResponse(request))
                    .isInstanceOf(InvalidTokenException.class)
                    .extracting("errorCode")
                    .isEqualTo("NOT_FOUND");
        }

        /**
         * Test 5.2: Should throw InvalidTokenException when token expired
         * AC1: Expired token shows error
         */
        @Test
        void should_throwInvalidTokenException_when_tokenExpired() {
            // Given
            when(magicLinkService.validateToken(validToken))
                    .thenReturn(TokenValidationResult.expired());

            SpeakerResponseRequest request = SpeakerResponseRequest.builder()
                    .token(validToken)
                    .response(SpeakerResponseType.ACCEPT)
                    .build();

            // When/Then
            assertThatThrownBy(() -> speakerResponseService.processResponse(request))
                    .isInstanceOf(InvalidTokenException.class)
                    .extracting("errorCode")
                    .isEqualTo("EXPIRED");
        }

        /**
         * Test 5.3: Should throw InvalidTokenException when token already used
         * AC1: Used token shows error
         */
        @Test
        void should_throwInvalidTokenException_when_tokenAlreadyUsed() {
            // Given
            when(magicLinkService.validateToken(validToken))
                    .thenReturn(TokenValidationResult.alreadyUsed());

            SpeakerResponseRequest request = SpeakerResponseRequest.builder()
                    .token(validToken)
                    .response(SpeakerResponseType.ACCEPT)
                    .build();

            // When/Then
            assertThatThrownBy(() -> speakerResponseService.processResponse(request))
                    .isInstanceOf(InvalidTokenException.class)
                    .extracting("errorCode")
                    .isEqualTo("ALREADY_USED");
        }
    }

    // ==================== Status History Tracking Tests ====================

    @Nested
    @DisplayName("Status History Tracking")
    class StatusHistoryTrackingTests {

        /**
         * Test: Should create status history when speaker accepts invitation
         */
        @Test
        void should_createStatusHistory_when_speakerAccepts() {
            // Given
            when(magicLinkService.validateToken(validToken))
                    .thenReturn(TokenValidationResult.valid(testSpeakerPoolId, "jane.speaker", "BAT2025Q1", TokenAction.RESPOND));
            when(speakerPoolRepository.findById(testSpeakerPoolId))
                    .thenReturn(Optional.of(testSpeakerPool));
            when(eventRepository.findById(testEventId))
                    .thenReturn(Optional.of(testEvent));
            when(speakerPoolRepository.save(any(SpeakerPool.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            SpeakerResponseRequest request = SpeakerResponseRequest.builder()
                    .token(validToken)
                    .response(SpeakerResponseType.ACCEPT)
                    .build();

            // When
            speakerResponseService.processResponse(request);

            // Then: Status history should be created
            ArgumentCaptor<SpeakerStatusHistory> captor = ArgumentCaptor.forClass(SpeakerStatusHistory.class);
            verify(statusHistoryRepository).save(captor.capture());

            SpeakerStatusHistory history = captor.getValue();
            assertThat(history.getSpeakerPoolId()).isEqualTo(testSpeakerPoolId);
            assertThat(history.getEventId()).isEqualTo(testEventId);
            assertThat(history.getPreviousStatus()).isEqualTo(SpeakerWorkflowState.INVITED);
            assertThat(history.getNewStatus()).isEqualTo(SpeakerWorkflowState.ACCEPTED);
            assertThat(history.getChangeReason()).contains("Accepted invitation via speaker portal");
        }

        /**
         * Test: Should create status history when speaker declines invitation
         */
        @Test
        void should_createStatusHistory_when_speakerDeclines() {
            // Given
            String declineReason = "Schedule conflict";

            when(magicLinkService.validateToken(validToken))
                    .thenReturn(TokenValidationResult.valid(testSpeakerPoolId, "jane.speaker", "BAT2025Q1", TokenAction.RESPOND));
            when(speakerPoolRepository.findById(testSpeakerPoolId))
                    .thenReturn(Optional.of(testSpeakerPool));
            when(eventRepository.findById(testEventId))
                    .thenReturn(Optional.of(testEvent));
            when(speakerPoolRepository.save(any(SpeakerPool.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            SpeakerResponseRequest request = SpeakerResponseRequest.builder()
                    .token(validToken)
                    .response(SpeakerResponseType.DECLINE)
                    .reason(declineReason)
                    .build();

            // When
            speakerResponseService.processResponse(request);

            // Then: Status history should be created
            ArgumentCaptor<SpeakerStatusHistory> captor = ArgumentCaptor.forClass(SpeakerStatusHistory.class);
            verify(statusHistoryRepository).save(captor.capture());

            SpeakerStatusHistory history = captor.getValue();
            assertThat(history.getPreviousStatus()).isEqualTo(SpeakerWorkflowState.INVITED);
            assertThat(history.getNewStatus()).isEqualTo(SpeakerWorkflowState.DECLINED);
            assertThat(history.getChangeReason()).contains("Declined invitation");
            assertThat(history.getChangeReason()).contains(declineReason);
        }

        /**
         * Test: Should NOT create status history when speaker marks tentative
         * (TENTATIVE does not change the workflow state - stays INVITED)
         */
        @Test
        void should_notCreateStatusHistory_when_speakerMarksTentative() {
            // Given
            when(magicLinkService.validateToken(validToken))
                    .thenReturn(TokenValidationResult.valid(testSpeakerPoolId, "jane.speaker", "BAT2025Q1", TokenAction.RESPOND));
            when(speakerPoolRepository.findById(testSpeakerPoolId))
                    .thenReturn(Optional.of(testSpeakerPool));
            when(eventRepository.findById(testEventId))
                    .thenReturn(Optional.of(testEvent));
            when(speakerPoolRepository.save(any(SpeakerPool.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            SpeakerResponseRequest request = SpeakerResponseRequest.builder()
                    .token(validToken)
                    .response(SpeakerResponseType.TENTATIVE)
                    .reason("Checking calendar")
                    .build();

            // When
            speakerResponseService.processResponse(request);

            // Then: Status history should NOT be created (status stays INVITED)
            verify(statusHistoryRepository, never()).save(any(SpeakerStatusHistory.class));
        }

        /**
         * Test: Should use speaker name when username is null
         */
        @Test
        void should_useSpeakerName_when_usernameIsNull() {
            // Given: Speaker without username
            testSpeakerPool.setUsername(null);

            when(magicLinkService.validateToken(validToken))
                    .thenReturn(TokenValidationResult.valid(testSpeakerPoolId, null, "BAT2025Q1", TokenAction.RESPOND));
            when(speakerPoolRepository.findById(testSpeakerPoolId))
                    .thenReturn(Optional.of(testSpeakerPool));
            when(eventRepository.findById(testEventId))
                    .thenReturn(Optional.of(testEvent));
            when(speakerPoolRepository.save(any(SpeakerPool.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            SpeakerResponseRequest request = SpeakerResponseRequest.builder()
                    .token(validToken)
                    .response(SpeakerResponseType.DECLINE)
                    .reason("Cannot attend")
                    .build();

            // When
            speakerResponseService.processResponse(request);

            // Then: Should use speaker name as changedBy
            ArgumentCaptor<SpeakerStatusHistory> captor = ArgumentCaptor.forClass(SpeakerStatusHistory.class);
            verify(statusHistoryRepository).save(captor.capture());
            assertThat(captor.getValue().getChangedByUsername()).isEqualTo("Jane Speaker");
        }
    }

    // ==================== AC7: Already Responded Handling Tests ====================

    @Nested
    @DisplayName("AC7: Already Responded Handling")
    class AlreadyRespondedTests {

        /**
         * Test 6.1: Should throw AlreadyRespondedException when speaker already accepted
         * AC7: Already responded speakers get error
         */
        @Test
        void should_throwAlreadyRespondedException_when_speakerAlreadyAccepted() {
            // Given
            testSpeakerPool.setStatus(SpeakerWorkflowState.ACCEPTED);
            testSpeakerPool.setAcceptedAt(Instant.now().minus(1, ChronoUnit.DAYS));

            when(magicLinkService.validateToken(validToken))
                    .thenReturn(TokenValidationResult.valid(testSpeakerPoolId, "jane.speaker", "BAT2025Q1", TokenAction.RESPOND));
            when(speakerPoolRepository.findById(testSpeakerPoolId))
                    .thenReturn(Optional.of(testSpeakerPool));

            SpeakerResponseRequest request = SpeakerResponseRequest.builder()
                    .token(validToken)
                    .response(SpeakerResponseType.ACCEPT)
                    .build();

            // When/Then
            assertThatThrownBy(() -> speakerResponseService.processResponse(request))
                    .isInstanceOf(AlreadyRespondedException.class);
        }

        /**
         * Test 6.2: Should throw AlreadyRespondedException when speaker already declined
         * AC7: Already responded speakers get error
         */
        @Test
        void should_throwAlreadyRespondedException_when_speakerAlreadyDeclined() {
            // Given
            testSpeakerPool.setStatus(SpeakerWorkflowState.DECLINED);
            testSpeakerPool.setDeclinedAt(Instant.now().minus(1, ChronoUnit.DAYS));

            when(magicLinkService.validateToken(validToken))
                    .thenReturn(TokenValidationResult.valid(testSpeakerPoolId, "jane.speaker", "BAT2025Q1", TokenAction.RESPOND));
            when(speakerPoolRepository.findById(testSpeakerPoolId))
                    .thenReturn(Optional.of(testSpeakerPool));

            SpeakerResponseRequest request = SpeakerResponseRequest.builder()
                    .token(validToken)
                    .response(SpeakerResponseType.DECLINE)
                    .reason("Trying again")
                    .build();

            // When/Then
            assertThatThrownBy(() -> speakerResponseService.processResponse(request))
                    .isInstanceOf(AlreadyRespondedException.class);
        }

        /**
         * Test 6.3: Should allow response when currently tentative (token still valid)
         * AC7: For TENTATIVE, allow updating response
         */
        @Test
        void should_allowResponse_when_currentlyTentative() {
            // Given - Speaker previously marked tentative
            testSpeakerPool.setIsTentative(true);
            testSpeakerPool.setTentativeReason("Was checking dates");
            // Status is still INVITED (tentative doesn't change workflow state)

            when(magicLinkService.validateToken(validToken))
                    .thenReturn(TokenValidationResult.valid(testSpeakerPoolId, "jane.speaker", "BAT2025Q1", TokenAction.RESPOND));
            when(speakerPoolRepository.findById(testSpeakerPoolId))
                    .thenReturn(Optional.of(testSpeakerPool));
            when(eventRepository.findById(testEventId))
                    .thenReturn(Optional.of(testEvent));
            when(speakerPoolRepository.save(any(SpeakerPool.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            SpeakerResponseRequest request = SpeakerResponseRequest.builder()
                    .token(validToken)
                    .response(SpeakerResponseType.ACCEPT)
                    .build();

            // When
            SpeakerResponseResult result = speakerResponseService.processResponse(request);

            // Then - Should succeed and transition to ACCEPTED
            assertThat(result.isSuccess()).isTrue();

            ArgumentCaptor<SpeakerPool> captor = ArgumentCaptor.forClass(SpeakerPool.class);
            verify(speakerPoolRepository).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(SpeakerWorkflowState.ACCEPTED);
        }
    }

    // ==================== Story 9.2: Account Creation & Temporary Password ====================

    @Nested
    @DisplayName("Story 9.2: Account Creation on Acceptance")
    class AccountCreationOnAcceptanceTests {

        private void arrangeAccept() {
            when(magicLinkService.validateToken(validToken))
                    .thenReturn(TokenValidationResult.valid(testSpeakerPoolId, "jane.speaker", "BAT2025Q1", TokenAction.RESPOND));
            when(speakerPoolRepository.findById(testSpeakerPoolId))
                    .thenReturn(Optional.of(testSpeakerPool));
            when(eventRepository.findById(testEventId))
                    .thenReturn(Optional.of(testEvent));
            when(speakerPoolRepository.save(any(SpeakerPool.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            // generateToken is called in buildResult() to create the VIEW token for email links
            // Uses lenient() to avoid strict-stub mismatch on the long parameter
            lenient().when(magicLinkService.generateToken(any(), any(), anyLong()))
                    .thenReturn("view-token-test");
        }

        /**
         * AC1/AC2: processInvitationAcceptance is called on accept.
         */
        @Test
        void should_triggerAccountCreation_when_speakerAccepts() {
            arrangeAccept();

            SpeakerResponseRequest request = SpeakerResponseRequest.builder()
                    .token(validToken)
                    .response(SpeakerResponseType.ACCEPT)
                    .build();

            speakerResponseService.processResponse(request);

            verify(speakerAccountCreationService, times(1))
                    .processInvitationAcceptance(testSpeakerPoolId);
        }

        /**
         * AC5: Temporary password from NEW account is threaded to the acceptance email.
         */
        @Test
        void should_passTemporaryPassword_when_newAccountCreated() {
            arrangeAccept();
            String tempPassword = "TempPass123!";
            when(speakerAccountCreationService.processInvitationAcceptance(testSpeakerPoolId))
                    .thenReturn(tempPassword);

            SpeakerResponseRequest request = SpeakerResponseRequest.builder()
                    .token(validToken)
                    .response(SpeakerResponseType.ACCEPT)
                    .build();

            speakerResponseService.processResponse(request);

            // Verify the 5-arg overload was called with the non-null password
            ArgumentCaptor<String> passwordCaptor = ArgumentCaptor.forClass(String.class);
            verify(acceptanceEmailService).sendAcceptanceConfirmationEmail(
                    any(), any(), anyString(), any(), passwordCaptor.capture());
            assertThat(passwordCaptor.getValue()).isEqualTo(tempPassword);
        }

        /**
         * AC5: Null password for EXTENDED accounts means email has no credentials block.
         */
        @Test
        void should_passNullPassword_when_existingAccountExtended() {
            arrangeAccept();
            when(speakerAccountCreationService.processInvitationAcceptance(testSpeakerPoolId))
                    .thenReturn(null); // EXTENDED account

            SpeakerResponseRequest request = SpeakerResponseRequest.builder()
                    .token(validToken)
                    .response(SpeakerResponseType.ACCEPT)
                    .build();

            speakerResponseService.processResponse(request);

            ArgumentCaptor<String> passwordCaptor = ArgumentCaptor.forClass(String.class);
            verify(acceptanceEmailService).sendAcceptanceConfirmationEmail(
                    any(), any(), anyString(), any(), passwordCaptor.capture());
            assertThat(passwordCaptor.getValue()).isNull();
        }

        /**
         * AC3 (acceptance not blocked): Account creation failure does NOT prevent acceptance.
         */
        @Test
        void should_completeAcceptance_when_accountCreationThrows() {
            arrangeAccept();
            when(speakerAccountCreationService.processInvitationAcceptance(any()))
                    .thenThrow(new RuntimeException("Cognito unavailable"));

            SpeakerResponseRequest request = SpeakerResponseRequest.builder()
                    .token(validToken)
                    .response(SpeakerResponseType.ACCEPT)
                    .build();

            // Acceptance must succeed even if account creation throws
            SpeakerResponseResult result = speakerResponseService.processResponse(request);
            assertThat(result.isSuccess()).isTrue();

            // Email must still be sent (with null password due to error)
            ArgumentCaptor<String> passwordCaptor = ArgumentCaptor.forClass(String.class);
            verify(acceptanceEmailService).sendAcceptanceConfirmationEmail(
                    any(), any(), anyString(), any(), passwordCaptor.capture());
            assertThat(passwordCaptor.getValue()).isNull();
        }

        /**
         * Account creation is NOT triggered for DECLINE responses.
         */
        @Test
        void should_notTriggerAccountCreation_when_speakerDeclines() {
            when(magicLinkService.validateToken(validToken))
                    .thenReturn(TokenValidationResult.valid(testSpeakerPoolId, "jane.speaker", "BAT2025Q1", TokenAction.RESPOND));
            when(speakerPoolRepository.findById(testSpeakerPoolId))
                    .thenReturn(Optional.of(testSpeakerPool));
            when(eventRepository.findById(testEventId))
                    .thenReturn(Optional.of(testEvent));
            when(speakerPoolRepository.save(any(SpeakerPool.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            SpeakerResponseRequest request = SpeakerResponseRequest.builder()
                    .token(validToken)
                    .response(SpeakerResponseType.DECLINE)
                    .reason("Not available")
                    .build();

            speakerResponseService.processResponse(request);

            verify(speakerAccountCreationService, never()).processInvitationAcceptance(any());
        }
    }
}
