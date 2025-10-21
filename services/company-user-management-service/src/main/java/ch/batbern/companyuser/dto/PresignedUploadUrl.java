package ch.batbern.companyuser.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for presigned S3 upload URL response
 * Contains the temporary upload URL and metadata for client-side uploads
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PresignedUploadUrl {

    /**
     * The presigned URL for uploading to S3
     */
    private String uploadUrl;

    /**
     * Unique file identifier for tracking the upload
     */
    private String fileId;

    /**
     * S3 key where the file will be stored
     */
    private String s3Key;

    /**
     * File extension (e.g., "png", "jpg", "svg")
     * Required for reconstructing S3 key during upload confirmation
     */
    private String fileExtension;

    /**
     * Expiration time in minutes (typically 15 minutes)
     */
    private int expiresInMinutes;

    /**
     * Required headers for the upload request
     */
    private java.util.Map<String, String> requiredHeaders;
}
