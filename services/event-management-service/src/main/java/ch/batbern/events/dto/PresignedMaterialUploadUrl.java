package ch.batbern.events.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * DTO for presigned material upload URL response
 * Story 5.9: Session Materials Upload
 *
 * Contains all information needed for client-side S3 upload:
 * - Presigned URL for PUT request
 * - Upload ID for tracking
 * - S3 key for file location
 * - Required HTTP headers
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PresignedMaterialUploadUrl {

    /**
     * Presigned S3 URL for PUT request (valid for 15 minutes)
     */
    private String uploadUrl;

    /**
     * Unique upload ID for tracking this upload
     * Named 'fileId' to match frontend PresignedUrlResponse interface
     */
    private String fileId;

    /**
     * S3 key where file will be stored
     * Format: materials/temp/{uploadId}/file-{uploadId}.{ext}
     */
    private String s3Key;

    /**
     * File extension (e.g., "pdf", "pptx", "mp4")
     */
    private String fileExtension;

    /**
     * Presigned URL expiration time in minutes
     */
    private int expiresInMinutes;

    /**
     * Required HTTP headers for S3 upload (e.g., Content-Type)
     */
    private Map<String, String> requiredHeaders;
}
