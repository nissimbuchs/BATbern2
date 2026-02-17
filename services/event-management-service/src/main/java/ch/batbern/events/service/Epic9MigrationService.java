package ch.batbern.events.service;

import ch.batbern.events.client.UserApiClient;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.events.dto.MigrationReport;
import ch.batbern.events.dto.SpeakerMigrationResult;
import ch.batbern.events.dto.SpeakerMigrationResult.MigrationOutcome;
import ch.batbern.events.dto.SpeakerProvisionRequest;
import ch.batbern.events.dto.SpeakerProvisionResponse;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.SpeakerPoolRepository;
import ch.batbern.shared.types.SpeakerWorkflowState;
import ch.batbern.shared.types.TokenAction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Story 9.4: Migration service for Epic 6 staging users.
 *
 * Migrates all ACCEPTED speakers from token-based auth (Epic 6) to
 * Cognito-backed JWT authentication (Epic 9) before production deployment.
 *
 * Key design decisions:
 * - Idempotent: safe to run multiple times (provisionSpeakerAccount is idempotent)
 * - Error isolation: one speaker failure does not stop the migration
 * - Grace period: old tokens are NOT invalidated (natural expiry via expires_at)
 * - Dry-run: validates all speakers without creating accounts or sending emails
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class Epic9MigrationService {

    private final SpeakerPoolRepository speakerPoolRepository;
    private final EventRepository eventRepository;
    private final UserApiClient userApiClient;
    private final MagicLinkService magicLinkService;
    private final SpeakerInvitationEmailService speakerInvitationEmailService;

    /**
     * Migrate all ACCEPTED speakers to Epic 9 JWT authentication.
     *
     * AC1: Identifies all SpeakerPool records with status=ACCEPTED across all events.
     * AC2: Provisions Cognito accounts via UserApiClient.provisionSpeakerAccount().
     * AC3: Sends fresh invitation email for NEW accounts only.
     * AC4: Does NOT invalidate old tokens (grace period via natural expiry).
     * AC5: dryRun=true validates without calling external services.
     * AC6: Returns structured MigrationReport with per-speaker outcomes.
     * AC7: Idempotent — does NOT resend email when account already exists (EXTENDED).
     *
     * @param dryRun if true, validates all speakers and logs intent without external calls
     * @return MigrationReport with aggregate counts and per-speaker results
     */
    public MigrationReport migrate(boolean dryRun) {
        log.info("Starting Epic 9 migration (dryRun={})", dryRun);

        List<SpeakerPool> acceptedSpeakers =
                speakerPoolRepository.findByStatus(SpeakerWorkflowState.ACCEPTED);

        log.info("Found {} ACCEPTED speakers to migrate", acceptedSpeakers.size());

        List<SpeakerMigrationResult> results = new ArrayList<>();
        int provisionedNew = 0;
        int extended = 0;
        int emailsSent = 0;
        int errors = 0;

        for (SpeakerPool speakerPool : acceptedSpeakers) {
            SpeakerMigrationResult result;
            try {
                result = migrateSpeaker(speakerPool, dryRun);
            } catch (Exception e) {
                log.error("Unexpected error migrating speakerPool {}: {}",
                        speakerPool.getId(), e.getMessage(), e);
                result = new SpeakerMigrationResult(
                        speakerPool.getId(),
                        speakerPool.getEmail(),
                        speakerPool.getSpeakerName(),
                        MigrationOutcome.ERROR,
                        e.getMessage()
                );
            }

            results.add(result);

            // Update aggregate counters
            switch (result.getOutcome()) {
                case PROVISIONED_NEW -> provisionedNew++;
                case EMAIL_SENT -> {
                    provisionedNew++;
                    emailsSent++;
                }
                case EMAIL_FAILED -> {
                    provisionedNew++;
                    errors++;
                }
                case EXTENDED -> extended++;
                case SKIPPED -> { /* no counter */ }
                case ERROR -> errors++;
                default -> { /* unexpected outcome */ }
            }
        }

        MigrationReport report = MigrationReport.builder()
                .total(acceptedSpeakers.size())
                .provisionedNew(provisionedNew)
                .extended(extended)
                .emailsSent(emailsSent)
                .errors(errors)
                .results(results)
                .build();

        log.info("Epic 9 migration complete (dryRun={}): total={}, provisionedNew={}, "
                        + "extended={}, emailsSent={}, errors={}",
                dryRun, report.getTotal(), report.getProvisionedNew(),
                report.getExtended(), report.getEmailsSent(), report.getErrors());

        return report;
    }

    private SpeakerMigrationResult migrateSpeaker(SpeakerPool speakerPool, boolean dryRun) {
        Optional<Event> eventOpt = eventRepository.findById(speakerPool.getEventId());
        if (eventOpt.isEmpty()) {
            log.warn("No event found for speakerPool {} (eventId={}); skipping",
                    speakerPool.getId(), speakerPool.getEventId());
            return new SpeakerMigrationResult(
                    speakerPool.getId(),
                    speakerPool.getEmail(),
                    speakerPool.getSpeakerName(),
                    MigrationOutcome.SKIPPED,
                    "Event not found: " + speakerPool.getEventId()
            );
        }

        if (dryRun) {
            log.info("[DRY-RUN] Would provision account for speakerPool {} (email={})",
                    speakerPool.getId(), speakerPool.getEmail());
            return new SpeakerMigrationResult(
                    speakerPool.getId(),
                    speakerPool.getEmail(),
                    speakerPool.getSpeakerName(),
                    MigrationOutcome.PROVISIONED_NEW,
                    "Dry-run: would provision account"
            );
        }

        Event event = eventOpt.get();
        SpeakerProvisionRequest request = buildProvisionRequest(speakerPool);
        SpeakerProvisionResponse provision = userApiClient.provisionSpeakerAccount(request);

        if (provision.getAction() == SpeakerProvisionResponse.AccountAction.NEW) {
            // New Cognito account — send fresh invitation with credentials
            String respondToken = magicLinkService.generateToken(speakerPool.getId(), TokenAction.RESPOND);
            String dashboardToken = magicLinkService.generateToken(speakerPool.getId(), TokenAction.VIEW);

            try {
                speakerInvitationEmailService.sendInvitationEmailSync(
                        speakerPool, event, respondToken, dashboardToken, Locale.GERMAN);
                return new SpeakerMigrationResult(
                        speakerPool.getId(),
                        speakerPool.getEmail(),
                        speakerPool.getSpeakerName(),
                        MigrationOutcome.EMAIL_SENT,
                        "Cognito account created, invitation sent"
                );
            } catch (Exception e) {
                log.error("Failed to send migration email for speakerPool {}: {}",
                        speakerPool.getId(), e.getMessage());
                return new SpeakerMigrationResult(
                        speakerPool.getId(),
                        speakerPool.getEmail(),
                        speakerPool.getSpeakerName(),
                        MigrationOutcome.EMAIL_FAILED,
                        e.getMessage()
                );
            }
        } else {
            // EXTENDED — Cognito account already exists, idempotency: no email resent (AC7)
            log.info("Speaker {} already has Cognito account; extending SPEAKER role only",
                    speakerPool.getId());
            return new SpeakerMigrationResult(
                    speakerPool.getId(),
                    speakerPool.getEmail(),
                    speakerPool.getSpeakerName(),
                    MigrationOutcome.EXTENDED,
                    "Cognito account extended with SPEAKER role, no email resent"
            );
        }
    }

    /**
     * Build SpeakerProvisionRequest from SpeakerPool.
     *
     * AC1.4 (CRITICAL): speakerName is a single String field — must split for firstName/lastName.
     */
    private SpeakerProvisionRequest buildProvisionRequest(SpeakerPool speakerPool) {
        String name = speakerPool.getSpeakerName();
        String firstName;
        String lastName;

        if (name != null && name.contains(" ")) {
            int idx = name.indexOf(' ');
            firstName = name.substring(0, idx);
            lastName = name.substring(idx + 1);
        } else {
            firstName = name != null ? name : "";
            lastName = "";
        }

        return SpeakerProvisionRequest.builder()
                .email(speakerPool.getEmail())
                .firstName(firstName)
                .lastName(lastName)
                .build();
    }
}
