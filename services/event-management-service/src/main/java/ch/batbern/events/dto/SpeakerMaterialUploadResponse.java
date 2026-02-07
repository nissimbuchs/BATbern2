package ch.batbern.events.dto;

import java.util.Map;

/**
 * Response DTO for speaker portal material upload presigned URL.
 * Story 6.3: Speaker Content Self-Submission Portal - AC7
 *
 * Contains presigned URL and metadata for direct S3 upload.
 */
public record SpeakerMaterialUploadResponse(
        String uploadUrl,
        String uploadId,
        String s3Key,
        String fileExtension,
        int expiresInMinutes,
        Map<String, String> requiredHeaders
) {}
