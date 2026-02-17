package ch.batbern.events.service;

import ch.batbern.events.client.UserApiClient;
import ch.batbern.events.domain.SpeakerAccountCreationAudit;
import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.events.dto.SpeakerProvisionRequest;
import ch.batbern.events.dto.SpeakerProvisionResponse;
import ch.batbern.events.repository.SpeakerAccountCreationAuditRepository;
import ch.batbern.events.repository.SpeakerPoolRepository;
import ch.batbern.shared.events.SpeakerAccountCreatedEvent;
import ch.batbern.shared.types.SpeakerWorkflowState;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for SpeakerAccountCreationService
 * Story 9.2: Automatic Account Creation & Role Extension on Invitation Acceptance
 *
 * Tests cover:
 * - AC1: New Cognito account creation returns temporary password
 * - AC2: Existing account role extension returns null password
 * - AC3: Idempotency / missing email short-circuit
 * - AC4: Audit record persisted with hashed email
 * - AC5: Temporary password returned to caller
 */
@ExtendWith(MockitoExtension.class)
class SpeakerAccountCreationServiceTest {

    @Mock
    private UserApiClient userApiClient;

    @Mock
    private SpeakerPoolRepository speakerPoolRepository;

    @Mock
    private SpeakerAccountCreationAuditRepository auditRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private SpeakerAccountCreationService service;

    private UUID speakerPoolId;
    private SpeakerPool speaker;

    @BeforeEach
    void setUp() {
        service = new SpeakerAccountCreationService(
                userApiClient,
                speakerPoolRepository,
                auditRepository,
                eventPublisher
        );

        speakerPoolId = UUID.randomUUID();
        speaker = SpeakerPool.builder()
                .id(speakerPoolId)
                .eventId(UUID.randomUUID())
                .speakerName("Jane Speaker")
                .email("jane@techcorp.ch")
                .status(SpeakerWorkflowState.ACCEPTED)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    // ==================== AC1: New Account — Returns Temporary Password ====================

    @Nested
    @DisplayName("AC1/AC5: New Cognito account creation")
    class NewAccountTests {

        @Test
        @DisplayName("should return temporary password when new account is created")
        void should_returnTemporaryPassword_when_newAccountCreated() {
            // Given
            SpeakerProvisionResponse response = SpeakerProvisionResponse.builder()
                    .username("jane.speaker")
                    .cognitoUserId("cognito-uuid-123")
                    .action(SpeakerProvisionResponse.AccountAction.NEW)
                    .temporaryPassword("TempPass123!")
                    .build();

            when(speakerPoolRepository.findById(speakerPoolId)).thenReturn(Optional.of(speaker));
            when(userApiClient.provisionSpeakerAccount(any())).thenReturn(response);
            when(auditRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // When
            String result = service.processInvitationAcceptance(speakerPoolId);

            // Then
            assertThat(result).isEqualTo("TempPass123!");
        }

        @Test
        @DisplayName("should send correct email and firstName/lastName to provision request")
        void should_sendCorrectNameAndEmail_in_provisionRequest() {
            // Given
            SpeakerProvisionResponse response = SpeakerProvisionResponse.builder()
                    .username("jane.speaker")
                    .cognitoUserId("cognito-uuid-123")
                    .action(SpeakerProvisionResponse.AccountAction.NEW)
                    .temporaryPassword("TempPass123!")
                    .build();

            when(speakerPoolRepository.findById(speakerPoolId)).thenReturn(Optional.of(speaker));
            when(userApiClient.provisionSpeakerAccount(any())).thenReturn(response);
            when(auditRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // When
            service.processInvitationAcceptance(speakerPoolId);

            // Then
            ArgumentCaptor<SpeakerProvisionRequest> captor = ArgumentCaptor.forClass(SpeakerProvisionRequest.class);
            verify(userApiClient).provisionSpeakerAccount(captor.capture());
            assertThat(captor.getValue().getEmail()).isEqualTo("jane@techcorp.ch");
            assertThat(captor.getValue().getFirstName()).isEqualTo("Jane");
            assertThat(captor.getValue().getLastName()).isEqualTo("Speaker");
        }

        @Test
        @DisplayName("should persist audit record with hashed email for AC4")
        void should_persistAuditRecord_with_hashedEmail() {
            // Given
            SpeakerProvisionResponse response = SpeakerProvisionResponse.builder()
                    .username("jane.speaker")
                    .cognitoUserId("cognito-uuid-456")
                    .action(SpeakerProvisionResponse.AccountAction.NEW)
                    .temporaryPassword("TempPass!")
                    .build();

            when(speakerPoolRepository.findById(speakerPoolId)).thenReturn(Optional.of(speaker));
            when(userApiClient.provisionSpeakerAccount(any())).thenReturn(response);
            when(auditRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // When
            service.processInvitationAcceptance(speakerPoolId);

            // Then
            ArgumentCaptor<SpeakerAccountCreationAudit> auditCaptor =
                    ArgumentCaptor.forClass(SpeakerAccountCreationAudit.class);
            verify(auditRepository).save(auditCaptor.capture());

            SpeakerAccountCreationAudit audit = auditCaptor.getValue();
            assertThat(audit.getSpeakerPoolId()).isEqualTo(speakerPoolId);
            assertThat(audit.getCognitoUserId()).isEqualTo("cognito-uuid-456");
            assertThat(audit.getAction()).isEqualTo(SpeakerAccountCreationAudit.Action.NEW);
            // Email must be stored as hash (SHA-256 hex = 64 chars), NOT plaintext
            assertThat(audit.getEmailHash()).hasSize(64);
            assertThat(audit.getEmailHash()).doesNotContain("jane@techcorp.ch");
        }

        @Test
        @DisplayName("should publish SpeakerAccountCreatedEvent for AC4")
        void should_publishDomainEvent_when_accountCreated() {
            // Given
            SpeakerProvisionResponse response = SpeakerProvisionResponse.builder()
                    .username("jane.speaker")
                    .cognitoUserId("cognito-uuid-789")
                    .action(SpeakerProvisionResponse.AccountAction.NEW)
                    .temporaryPassword("TempPass!")
                    .build();

            when(speakerPoolRepository.findById(speakerPoolId)).thenReturn(Optional.of(speaker));
            when(userApiClient.provisionSpeakerAccount(any())).thenReturn(response);
            when(auditRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // When
            service.processInvitationAcceptance(speakerPoolId);

            // Then
            ArgumentCaptor<SpeakerAccountCreatedEvent> eventCaptor =
                    ArgumentCaptor.forClass(SpeakerAccountCreatedEvent.class);
            verify(eventPublisher).publishEvent(eventCaptor.capture());

            SpeakerAccountCreatedEvent event = eventCaptor.getValue();
            assertThat(event.getCognitoUserId()).isEqualTo("cognito-uuid-789");
            assertThat(event.getAccountAction()).isEqualTo(SpeakerAccountCreatedEvent.AccountAction.NEW);
        }
    }

    // ==================== AC2: Extended Account — Returns Null Password ====================

    @Nested
    @DisplayName("AC2: Existing account role extension")
    class ExtendedAccountTests {

        @Test
        @DisplayName("should return null when existing account is extended")
        void should_returnNull_when_existingAccountExtended() {
            // Given
            SpeakerProvisionResponse response = SpeakerProvisionResponse.builder()
                    .username("jane.speaker")
                    .cognitoUserId("cognito-uuid-existing")
                    .action(SpeakerProvisionResponse.AccountAction.EXTENDED)
                    .temporaryPassword(null)
                    .build();

            when(speakerPoolRepository.findById(speakerPoolId)).thenReturn(Optional.of(speaker));
            when(userApiClient.provisionSpeakerAccount(any())).thenReturn(response);
            when(auditRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // When
            String result = service.processInvitationAcceptance(speakerPoolId);

            // Then
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("should persist EXTENDED audit record")
        void should_persistAuditRecord_with_extendedAction() {
            // Given
            SpeakerProvisionResponse response = SpeakerProvisionResponse.builder()
                    .username("jane.speaker")
                    .cognitoUserId("cognito-uuid-existing")
                    .action(SpeakerProvisionResponse.AccountAction.EXTENDED)
                    .temporaryPassword(null)
                    .build();

            when(speakerPoolRepository.findById(speakerPoolId)).thenReturn(Optional.of(speaker));
            when(userApiClient.provisionSpeakerAccount(any())).thenReturn(response);
            when(auditRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // When
            service.processInvitationAcceptance(speakerPoolId);

            // Then
            ArgumentCaptor<SpeakerAccountCreationAudit> auditCaptor =
                    ArgumentCaptor.forClass(SpeakerAccountCreationAudit.class);
            verify(auditRepository).save(auditCaptor.capture());
            assertThat(auditCaptor.getValue().getAction())
                    .isEqualTo(SpeakerAccountCreationAudit.Action.EXTENDED);
        }
    }

    // ==================== AC3: Edge Cases ====================

    @Nested
    @DisplayName("AC3: Edge cases and idempotency guards")
    class EdgeCaseTests {

        @Test
        @DisplayName("should return null and skip provisioning when speaker has no email")
        void should_returnNull_when_speakerHasNoEmail() {
            // Given
            speaker.setEmail(null);
            when(speakerPoolRepository.findById(speakerPoolId)).thenReturn(Optional.of(speaker));

            // When
            String result = service.processInvitationAcceptance(speakerPoolId);

            // Then
            assertThat(result).isNull();
            verify(userApiClient, never()).provisionSpeakerAccount(any());
            verify(auditRepository, never()).save(any());
        }

        @Test
        @DisplayName("should return null and skip provisioning when speaker email is blank")
        void should_returnNull_when_speakerEmailIsBlank() {
            // Given
            speaker.setEmail("   ");
            when(speakerPoolRepository.findById(speakerPoolId)).thenReturn(Optional.of(speaker));

            // When
            String result = service.processInvitationAcceptance(speakerPoolId);

            // Then
            assertThat(result).isNull();
            verify(userApiClient, never()).provisionSpeakerAccount(any());
        }

        @Test
        @DisplayName("should return null when provision API returns null response")
        void should_returnNull_when_provisionApiReturnsNull() {
            // Given
            when(speakerPoolRepository.findById(speakerPoolId)).thenReturn(Optional.of(speaker));
            when(userApiClient.provisionSpeakerAccount(any())).thenReturn(null);

            // When
            String result = service.processInvitationAcceptance(speakerPoolId);

            // Then
            assertThat(result).isNull();
            verify(auditRepository, never()).save(any());
        }

        @Test
        @DisplayName("should handle single-word speaker name gracefully")
        void should_handleSingleWordName_gracefully() {
            // Given
            speaker.setSpeakerName("Mononymous");
            SpeakerProvisionResponse response = SpeakerProvisionResponse.builder()
                    .username("mononymous")
                    .cognitoUserId("cognito-uuid-mono")
                    .action(SpeakerProvisionResponse.AccountAction.NEW)
                    .temporaryPassword("Pass123!")
                    .build();

            when(speakerPoolRepository.findById(speakerPoolId)).thenReturn(Optional.of(speaker));
            when(userApiClient.provisionSpeakerAccount(any())).thenReturn(response);
            when(auditRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // When
            service.processInvitationAcceptance(speakerPoolId);

            // Then
            ArgumentCaptor<SpeakerProvisionRequest> captor = ArgumentCaptor.forClass(SpeakerProvisionRequest.class);
            verify(userApiClient).provisionSpeakerAccount(captor.capture());
            assertThat(captor.getValue().getFirstName()).isEqualTo("Mononymous");
            assertThat(captor.getValue().getLastName()).isEqualTo("");
        }
    }

    // ==================== hashEmail utility ====================

    @Nested
    @DisplayName("hashEmail: PII-safe SHA-256 hashing")
    class HashEmailTests {

        @Test
        @DisplayName("should produce consistent 64-char hex hash")
        void should_produceConsistentHash_for_sameEmail() {
            String hash1 = SpeakerAccountCreationService.hashEmail("test@example.com");
            String hash2 = SpeakerAccountCreationService.hashEmail("test@example.com");

            assertThat(hash1).hasSize(64);
            assertThat(hash1).isEqualTo(hash2);
        }

        @Test
        @DisplayName("should produce different hashes for different emails")
        void should_produceDifferentHash_for_differentEmails() {
            String hash1 = SpeakerAccountCreationService.hashEmail("alice@example.com");
            String hash2 = SpeakerAccountCreationService.hashEmail("bob@example.com");

            assertThat(hash1).isNotEqualTo(hash2);
        }

        @Test
        @DisplayName("should normalize email to lowercase before hashing")
        void should_normalizeEmailToLowercase_before_hashing() {
            String lower = SpeakerAccountCreationService.hashEmail("jane@example.com");
            String upper = SpeakerAccountCreationService.hashEmail("JANE@EXAMPLE.COM");

            assertThat(lower).isEqualTo(upper);
        }
    }
}
