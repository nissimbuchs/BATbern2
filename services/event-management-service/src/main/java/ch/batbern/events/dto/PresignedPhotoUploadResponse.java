package ch.batbern.events.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for speaker profile photo presigned URL.
 * Story 6.2b: Speaker Profile Update Portal - AC7 (Profile Photo Upload)
 *
 * Returned by POST /api/v1/speaker-portal/profile/photo/presigned-url
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PresignedPhotoUploadResponse {

    /**
     * Presigned S3 PUT URL for direct upload.
     * Client should PUT the file to this URL with Content-Type header.
     */
    private String uploadUrl;

    /**
     * Unique upload ID for tracking.
     * Must be passed to /confirm endpoint after upload.
     */
    private String uploadId;

    /**
     * S3 object key where file will be stored.
     * Format: speaker-profiles/{year}/{username}/photo-{uploadId}.{ext}
     */
    private String s3Key;

    /**
     * URL expiration time in seconds (default: 900 = 15 minutes)
     */
    private int expiresIn;

    /**
     * Maximum allowed file size in bytes (5MB = 5,242,880)
     */
    private long maxSizeBytes;
}
