package ch.batbern.events.dto;

/**
 * Request DTO for speaker portal material upload confirmation.
 * Story 6.3: Speaker Content Self-Submission Portal - AC7.
 *
 * <p>Story 11.E.3: {@code token} field removed — the portal is now Cognito-secured and
 * the {@code eventCode} arrives as a path parameter.
 */
public record SpeakerMaterialConfirmRequest(
        String uploadId,
        String fileName,
        String fileExtension,
        long fileSize,
        String mimeType,
        String materialType
) {}
