package ch.batbern.events.dto;

/**
 * Request DTO for speaker portal material upload presigned URL generation.
 * Story 6.3: Speaker Content Self-Submission Portal - AC7.
 *
 * <p>Story 11.E.3: {@code token} field removed — the portal is now Cognito-secured and
 * the {@code eventCode} arrives as a path parameter.
 */
public record SpeakerMaterialUploadRequest(
        String fileName,
        long fileSize,
        String mimeType
) {}
