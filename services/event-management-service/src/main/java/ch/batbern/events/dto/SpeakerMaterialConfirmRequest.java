package ch.batbern.events.dto;

/**
 * Request DTO for speaker portal material upload confirmation.
 * Story 6.3: Speaker Content Self-Submission Portal - AC7
 *
 * Uses magic link token for authentication instead of JWT.
 */
public record SpeakerMaterialConfirmRequest(
        String token,
        String uploadId,
        String fileName,
        String fileExtension,
        long fileSize,
        String mimeType,
        String materialType
) {}
