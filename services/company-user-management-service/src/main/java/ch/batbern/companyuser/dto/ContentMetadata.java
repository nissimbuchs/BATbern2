package ch.batbern.companyuser.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for file content metadata after upload confirmation
 * Contains S3 storage information and CloudFront CDN URLs
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContentMetadata {

    /**
     * Unique file identifier
     */
    private String fileId;

    /**
     * S3 key where the file is stored
     */
    private String s3Key;

    /**
     * CloudFront CDN URL for accessing the file
     */
    private String cloudFrontUrl;

    /**
     * SHA-256 checksum for file integrity verification
     */
    private String checksumSha256;

    /**
     * File size in bytes
     */
    private Long fileSizeBytes;

    /**
     * MIME type of the uploaded file
     */
    private String mimeType;
}
