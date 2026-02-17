package ch.batbern.events.service;

import ch.batbern.events.client.UserApiClient;
import ch.batbern.events.domain.SpeakerAccountCreationAudit;
import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.events.dto.SpeakerProvisionRequest;
import ch.batbern.events.dto.SpeakerProvisionResponse;
import ch.batbern.events.repository.SpeakerAccountCreationAuditRepository;
import ch.batbern.events.repository.SpeakerPoolRepository;
import ch.batbern.shared.events.SpeakerAccountCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Story 9.2 Task 4: Orchestrates speaker Cognito account creation on invitation acceptance.
 *
 * Called from SpeakerResponseService.processAcceptResponse() after invitation is accepted.
 * Failure MUST NOT block the acceptance — wrapped in try/catch by the caller.
 *
 * Flow:
 * 1. Load speaker from SpeakerPool by speakerPoolId
 * 2. Call UserApiClient.provisionSpeakerAccount (Cognito + local DB role)
 * 3. Persist audit record (SpeakerAccountCreationAudit)
 * 4. Publish SpeakerAccountCreatedEvent via ApplicationEventPublisher
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SpeakerAccountCreationService {

    private final UserApiClient userApiClient;
    private final SpeakerPoolRepository speakerPoolRepository;
    private final SpeakerAccountCreationAuditRepository auditRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Process invitation acceptance and provision Cognito account.
     *
     * Called after acceptance is persisted. Failure is logged but does NOT propagate —
     * the caller (SpeakerResponseService) must wrap in try/catch to ensure acceptance completes.
     *
     * Idempotent: safe to call multiple times for the same speakerPoolId.
     *
     * @param speakerPoolId UUID of the accepted speaker pool entry
     * @return temporary password for NEW accounts (AC5), null for EXTENDED accounts or on error
     */
    public String processInvitationAcceptance(UUID speakerPoolId) {
        log.info("Processing account creation for speakerPoolId: {}", speakerPoolId);

        // Load speaker details
        SpeakerPool speaker = speakerPoolRepository.findById(speakerPoolId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "SpeakerPool not found: " + speakerPoolId));

        String email = speaker.getEmail();
        if (email == null || email.isBlank()) {
            log.error("Speaker {} has no email — skipping account creation", speakerPoolId);
            return null;
        }

        // Call company-user-management-service to provision Cognito account + local SPEAKER role
        // SpeakerPool uses a single speakerName field — split for the provision request
        String speakerName = speaker.getSpeakerName() != null ? speaker.getSpeakerName() : "";
        String[] nameParts = speakerName.trim().split("\\s+", 2);
        String firstName = nameParts.length > 0 ? nameParts[0] : speakerName;
        String lastName  = nameParts.length > 1 ? nameParts[1] : "";

        SpeakerProvisionRequest request = SpeakerProvisionRequest.builder()
                .email(email)
                .firstName(firstName)
                .lastName(lastName)
                .build();

        SpeakerProvisionResponse provisionResult = userApiClient.provisionSpeakerAccount(request);

        if (provisionResult == null) {
            log.error("Speaker provision returned null for speakerPoolId: {}", speakerPoolId);
            return null;
        }

        // Persist audit record (AC4)
        SpeakerAccountCreationAudit audit = SpeakerAccountCreationAudit.builder()
                .speakerPoolId(speakerPoolId)
                .emailHash(hashEmail(email))
                .cognitoUserId(provisionResult.getCognitoUserId())
                .action(toAuditAction(provisionResult.getAction()))
                .build();

        auditRepository.save(audit);
        log.debug("Saved audit record for speakerPoolId: {}, action: {}", speakerPoolId, audit.getAction());

        // Publish domain event (AC4) — use email hash, never plain email, to avoid PII in event bus
        SpeakerAccountCreatedEvent event = new SpeakerAccountCreatedEvent(
                speakerPoolId,
                hashEmail(email),
                provisionResult.getCognitoUserId(),
                toEventAction(provisionResult.getAction())
        );
        eventPublisher.publishEvent(event);
        log.debug("Published SpeakerAccountCreatedEvent for speakerPoolId: {}", speakerPoolId);

        log.info("Account creation complete for speakerPoolId: {}, action: {}",
                speakerPoolId, provisionResult.getAction());

        // AC5: return temporary password for NEW accounts so caller can include in email
        return provisionResult.getAction() == SpeakerProvisionResponse.AccountAction.NEW
                ? provisionResult.getTemporaryPassword()
                : null;
    }

    private SpeakerAccountCreationAudit.Action toAuditAction(SpeakerProvisionResponse.AccountAction action) {
        return action == SpeakerProvisionResponse.AccountAction.NEW
                ? SpeakerAccountCreationAudit.Action.NEW
                : SpeakerAccountCreationAudit.Action.EXTENDED;
    }

    private SpeakerAccountCreatedEvent.AccountAction toEventAction(SpeakerProvisionResponse.AccountAction action) {
        return action == SpeakerProvisionResponse.AccountAction.NEW
                ? SpeakerAccountCreatedEvent.AccountAction.NEW
                : SpeakerAccountCreatedEvent.AccountAction.EXTENDED;
    }

    /**
     * SHA-256 hex hash of email for PII-safe audit storage.
     */
    static String hashEmail(String email) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(email.toLowerCase().trim().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
