package ch.batbern.events.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for speaker portal material upload confirmation.
 * Story 6.3: Speaker Content Self-Submission Portal - AC7
 *
 * Contains confirmed material metadata.
 */
public record SpeakerMaterialConfirmResponse(
        UUID materialId,
        String uploadId,
        String fileName,
        String cloudFrontUrl,
        String materialType,
        Instant uploadedAt
) {}
