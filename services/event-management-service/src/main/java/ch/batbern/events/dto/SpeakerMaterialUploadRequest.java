package ch.batbern.events.dto;

/**
 * Request DTO for speaker portal material upload presigned URL generation.
 * Story 6.3: Speaker Content Self-Submission Portal - AC7
 *
 * Uses magic link token for authentication instead of JWT.
 */
public record SpeakerMaterialUploadRequest(
        String token,
        String fileName,
        long fileSize,
        String mimeType
) {}
