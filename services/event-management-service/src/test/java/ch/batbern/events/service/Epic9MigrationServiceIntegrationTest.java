package ch.batbern.events.service;

import ch.batbern.events.AbstractIntegrationTest;
import ch.batbern.events.client.UserApiClient;
import ch.batbern.events.config.TestUserApiClientConfig;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.events.dto.MigrationReport;
import ch.batbern.events.dto.SpeakerMigrationResult.MigrationOutcome;
import ch.batbern.events.dto.SpeakerProvisionRequest;
import ch.batbern.events.dto.SpeakerProvisionResponse;
import ch.batbern.events.dto.SpeakerProvisionResponse.AccountAction;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.SpeakerPoolRepository;
import ch.batbern.shared.types.EventWorkflowState;
import ch.batbern.shared.types.SpeakerWorkflowState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;
import ch.batbern.events.dto.generated.EventType;

/**
 * Integration tests for Epic9MigrationService (Story 9.4, AC10).
 *
 * Tests the migration logic against a real PostgreSQL database (Testcontainers).
 * UserApiClient is mocked via TestUserApiClientConfig.
 * SpeakerInvitationEmailService is mocked via @MockitoBean.
 *
 * Pattern: @Import(TestUserApiClientConfig.class) + @MockitoBean SpeakerInvitationEmailService
 * (same as SpeakerMagicLinkRequestControllerIntegrationTest)
 */
@Import(TestUserApiClientConfig.class)
@DisplayName("Epic9MigrationService - migration logic")
@Transactional
class Epic9MigrationServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private Epic9MigrationService migrationService;

    @Autowired
    private UserApiClient userApiClient;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private SpeakerPoolRepository speakerPoolRepository;

    @MockitoBean
    private SpeakerInvitationEmailService speakerInvitationEmailService;

    private Event testEvent;

    @BeforeEach
    void setUp() {
        reset(userApiClient);
        // Default: returns NEW account action for any provision call
        SpeakerProvisionResponse newAccountResponse = SpeakerProvisionResponse.builder()
                .username("test-user")
                .cognitoUserId(UUID.randomUUID().toString())
                .action(AccountAction.NEW)
                .temporaryPassword("TempPass1!")
                .build();
        when(userApiClient.provisionSpeakerAccount(any())).thenReturn(newAccountResponse);
        doNothing().when(speakerInvitationEmailService)
                .sendInvitationEmailSync(any(), any(), any(), any(), any());

        // Create a test event for all speakers to reference
        testEvent = Event.builder()
                .eventCode("BAT-MIGRATION-TEST-" + UUID.randomUUID().toString().substring(0, 8))
                .eventNumber(Math.abs(UUID.randomUUID().hashCode()))
                .title("BATbern Migration Test Event")
                .eventType(EventType.EVENING)
                .workflowState(EventWorkflowState.SPEAKER_IDENTIFICATION)
                .date(Instant.now().plus(90, ChronoUnit.DAYS))
                .registrationDeadline(Instant.now().plus(80, ChronoUnit.DAYS))
                .venueName("Test Venue")
                .venueAddress("123 Test Street, Bern")
                .venueCapacity(200)
                .organizerUsername("test.organizer")
                .build();
        testEvent = eventRepository.save(testEvent);
    }

    // ─── AC1 + AC6: Empty state ───────────────────────────────────────────────

    @Test
    @DisplayName("should return empty report when no ACCEPTED speakers exist")
    void should_returnEmptyReport_when_noAcceptedSpeakers() {
        // Given: No ACCEPTED speakers (only other statuses)
        createSpeaker("Other Speaker", "other@example.com", SpeakerWorkflowState.IDENTIFIED);

        // When
        MigrationReport report = migrationService.migrate(false);

        // Then
        assertThat(report.getTotal()).isEqualTo(0);
        assertThat(report.getProvisionedNew()).isEqualTo(0);
        assertThat(report.getExtended()).isEqualTo(0);
        assertThat(report.getEmailsSent()).isEqualTo(0);
        assertThat(report.getErrors()).isEqualTo(0);
        assertThat(report.getResults()).isEmpty();
        verify(userApiClient, never()).provisionSpeakerAccount(any());
    }

    // ─── AC2 + AC3: NEW account provisioning + email ─────────────────────────

    @Test
    @DisplayName("should provision NEW account and send email when action=NEW")
    void should_provisionNewAndSendEmail_when_actionIsNew() {
        // Given: one ACCEPTED speaker
        SpeakerPool speaker = createSpeaker("Hans Muster", "hans@example.com", SpeakerWorkflowState.ACCEPTED);

        // When
        MigrationReport report = migrationService.migrate(false);

        // Then: report has EMAIL_SENT outcome (implies PROVISIONED_NEW)
        assertThat(report.getTotal()).isEqualTo(1);
        assertThat(report.getProvisionedNew()).isEqualTo(1);
        assertThat(report.getEmailsSent()).isEqualTo(1);
        assertThat(report.getExtended()).isEqualTo(0);
        assertThat(report.getErrors()).isEqualTo(0);

        assertThat(report.getResults()).hasSize(1);
        assertThat(report.getResults().get(0).getOutcome()).isEqualTo(MigrationOutcome.EMAIL_SENT);
        assertThat(report.getResults().get(0).getSpeakerPoolId()).isEqualTo(speaker.getId());
        assertThat(report.getResults().get(0).getEmail()).isEqualTo("hans@example.com");

        verify(userApiClient, times(1)).provisionSpeakerAccount(any());
        verify(speakerInvitationEmailService, times(1)).sendInvitationEmailSync(any(), any(), any(), any(), any());
    }

    // ─── AC1.4: Name-splitting logic ─────────────────────────────────────────

    @Test
    @DisplayName("should split speakerName into firstName and lastName correctly (AC1.4)")
    void should_splitName_correctly_when_provisioningAccount() {
        // Given: speaker with full name "Hans Muster"
        createSpeaker("Hans Muster", "hans@example.com", SpeakerWorkflowState.ACCEPTED);

        // When
        migrationService.migrate(false);

        // Then: provisionSpeakerAccount was called with firstName="Hans", lastName="Muster"
        ArgumentCaptor<SpeakerProvisionRequest> captor = ArgumentCaptor.forClass(SpeakerProvisionRequest.class);
        verify(userApiClient, times(1)).provisionSpeakerAccount(captor.capture());
        SpeakerProvisionRequest captured = captor.getValue();
        assertThat(captured.getFirstName()).isEqualTo("Hans");
        assertThat(captured.getLastName()).isEqualTo("Muster");
        assertThat(captured.getEmail()).isEqualTo("hans@example.com");
    }

    @Test
    @DisplayName("should use full name as firstName and empty lastName when speakerName has no space (AC1.4 edge case)")
    void should_useFullNameAsFirstName_when_speakerNameHasNoSpace() {
        // Given: speaker with single-word name
        createSpeaker("Cher", "cher@example.com", SpeakerWorkflowState.ACCEPTED);

        // When
        migrationService.migrate(false);

        // Then: firstName=full name, lastName=empty string
        ArgumentCaptor<SpeakerProvisionRequest> captor = ArgumentCaptor.forClass(SpeakerProvisionRequest.class);
        verify(userApiClient, times(1)).provisionSpeakerAccount(captor.capture());
        SpeakerProvisionRequest captured = captor.getValue();
        assertThat(captured.getFirstName()).isEqualTo("Cher");
        assertThat(captured.getLastName()).isEqualTo("");
    }

    // ─── AC7: Idempotency — EXTENDED account, no email resent ────────────────

    @Test
    @DisplayName("should record EXTENDED and NOT send email when account already exists (action=EXTENDED)")
    void should_recordExtended_and_notSendEmail_when_actionIsExtended() {
        // Given: one ACCEPTED speaker, Cognito account already exists
        SpeakerProvisionResponse extendedResponse = SpeakerProvisionResponse.builder()
                .username("existing-user")
                .cognitoUserId(UUID.randomUUID().toString())
                .action(AccountAction.EXTENDED)
                .temporaryPassword(null)
                .build();
        when(userApiClient.provisionSpeakerAccount(any())).thenReturn(extendedResponse);

        createSpeaker("Anna Bauer", "anna@example.com", SpeakerWorkflowState.ACCEPTED);

        // When
        MigrationReport report = migrationService.migrate(false);

        // Then: EXTENDED outcome, no email sent
        assertThat(report.getTotal()).isEqualTo(1);
        assertThat(report.getExtended()).isEqualTo(1);
        assertThat(report.getProvisionedNew()).isEqualTo(0);
        assertThat(report.getEmailsSent()).isEqualTo(0);
        assertThat(report.getErrors()).isEqualTo(0);

        assertThat(report.getResults()).hasSize(1);
        assertThat(report.getResults().get(0).getOutcome()).isEqualTo(MigrationOutcome.EXTENDED);

        verify(userApiClient, times(1)).provisionSpeakerAccount(any());
        verify(speakerInvitationEmailService, never()).sendInvitationEmailSync(any(), any(), any(), any(), any());
    }

    // ─── AC5: Dry-run mode ────────────────────────────────────────────────────

    @Test
    @DisplayName("should return report without calling external services in dry-run mode (AC5)")
    void should_returnReportWithoutExternalCalls_when_dryRun() {
        // Given: one ACCEPTED speaker
        createSpeaker("Dry Run Speaker", "dryrun@example.com", SpeakerWorkflowState.ACCEPTED);

        // When: dry-run
        MigrationReport report = migrationService.migrate(true);

        // Then: report returned but NO external service calls
        assertThat(report.getTotal()).isEqualTo(1);
        assertThat(report.getResults()).hasSize(1);
        assertThat(report.getResults().get(0).getOutcome()).isEqualTo(MigrationOutcome.PROVISIONED_NEW);

        verify(userApiClient, never()).provisionSpeakerAccount(any());
        verify(speakerInvitationEmailService, never()).sendInvitationEmailSync(any(), any(), any(), any(), any());
    }

    // ─── Email failure — error isolation ─────────────────────────────────────

    @Test
    @DisplayName("should record EMAIL_FAILED and continue migration when email send throws")
    void should_recordEmailFailed_and_continueMigration_when_emailThrows() {
        // Given: speaker, but email sending fails
        doThrow(new RuntimeException("SMTP connection refused"))
                .when(speakerInvitationEmailService)
                .sendInvitationEmailSync(any(), any(), any(), any(), any());

        createSpeaker("Email Fail Speaker", "emailfail@example.com", SpeakerWorkflowState.ACCEPTED);

        // When
        MigrationReport report = migrationService.migrate(false);

        // Then: EMAIL_FAILED recorded, migration completes, errors=1
        assertThat(report.getTotal()).isEqualTo(1);
        assertThat(report.getProvisionedNew()).isEqualTo(1);  // account was created
        assertThat(report.getEmailsSent()).isEqualTo(0);
        assertThat(report.getErrors()).isEqualTo(1);

        assertThat(report.getResults()).hasSize(1);
        assertThat(report.getResults().get(0).getOutcome()).isEqualTo(MigrationOutcome.EMAIL_FAILED);
    }

    // ─── AC7/AC10: Idempotency + partial error isolation ────────────────────

    @Test
    @DisplayName("should record first speaker success and second speaker ERROR when second provisioning fails")
    void should_recordFirstSuccess_and_secondError_when_secondProvisionFails() {
        // Given: two speakers, second provisioning throws
        createSpeaker("Speaker One", "one@example.com", SpeakerWorkflowState.ACCEPTED);
        createSpeaker("Speaker Two", "two@example.com", SpeakerWorkflowState.ACCEPTED);

        // First call returns NEW, second throws
        when(userApiClient.provisionSpeakerAccount(any()))
                .thenReturn(SpeakerProvisionResponse.builder()
                        .username("user-one")
                        .action(AccountAction.NEW)
                        .temporaryPassword("TempPass1!")
                        .build())
                .thenThrow(new RuntimeException("Cognito service unavailable"));

        // When
        MigrationReport report = migrationService.migrate(false);

        // Then: total=2, first=EMAIL_SENT, second=ERROR, migration completes
        assertThat(report.getTotal()).isEqualTo(2);
        assertThat(report.getResults()).hasSize(2);
        assertThat(report.getErrors()).isEqualTo(1);

        // Find results by email (order not guaranteed)
        long emailSentCount = report.getResults().stream()
                .filter(r -> r.getOutcome() == MigrationOutcome.EMAIL_SENT)
                .count();
        long errorCount = report.getResults().stream()
                .filter(r -> r.getOutcome() == MigrationOutcome.ERROR)
                .count();

        assertThat(emailSentCount).isEqualTo(1);
        assertThat(errorCount).isEqualTo(1);
    }

    // ─── Helper methods ───────────────────────────────────────────────────────

    private SpeakerPool createSpeaker(String name, String email, SpeakerWorkflowState status) {
        SpeakerPool speaker = new SpeakerPool();
        speaker.setEventId(testEvent.getId());
        speaker.setSpeakerName(name);
        speaker.setEmail(email);
        speaker.setCompany("Test Corp");
        speaker.setStatus(status);
        return speakerPoolRepository.save(speaker);
    }
}
