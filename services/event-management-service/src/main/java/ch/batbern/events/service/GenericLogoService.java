package ch.batbern.events.service;

import ch.batbern.events.domain.Logo;
import ch.batbern.events.domain.LogoStatus;
import ch.batbern.events.repository.LogoRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

/**
 * Generic Logo Service for event-management-service
 * Story 2.5.3a: Event Theme Image Upload
 *
 * Simplified version that only provides logo association functionality.
 * The full GenericLogoService in company-user-management-service handles
 * upload initiation and confirmation.
 *
 * This service works with the same logos table via shared database pattern.
 */
@Service
@Transactional
@Slf4j
public class GenericLogoService {

    private final S3Client s3Client;
    private final LogoRepository logoRepository;
    private final String bucketName;
    private final String cloudFrontDomain;

    public GenericLogoService(
            S3Client s3Client,
            LogoRepository logoRepository,
            @Value("${aws.s3.bucket-name:batbern-development-company-logos}") String bucketName,
            @Value("${aws.cloudfront.domain:https://cdn.batbern.ch}") String cloudFrontDomain) {
        this.s3Client = s3Client;
        this.logoRepository = logoRepository;
        this.bucketName = bucketName;
        this.cloudFrontDomain = cloudFrontDomain;
    }

    /**
     * Phase 3: Associate logo with entity
     * Copies S3 file from temp to final location
     * Updates Logo entity to ASSOCIATED status
     * Called during entity creation (e.g., EventService.createEvent)
     *
     * @param uploadId   Upload identifier from Phase 1
     * @param entityType Type of entity (COMPANY, USER, EVENT, etc.)
     * @param entityId   Entity identifier (e.g., company name, username, eventCode)
     * @param finalS3Key Final S3 location for the file
     * @return CloudFront URL for accessing the logo
     * @throws RuntimeException if upload ID not found or logo not in CONFIRMED state
     */
    public String associateLogoWithEntity(String uploadId, String entityType, String entityId, String finalS3Key) {
        log.info("Associating logo {} with entity: {} ({})", uploadId, entityType, entityId);

        Logo logo = logoRepository.findByUploadId(uploadId)
                .orElseThrow(() -> new RuntimeException("Logo not found for uploadId: " + uploadId));

        if (logo.getStatus() != LogoStatus.CONFIRMED) {
            throw new IllegalStateException(
                    "Logo must be CONFIRMED before association. Current status: " + logo.getStatus());
        }

        try {
            // Copy S3 object from temp to final location
            copyS3Object(logo.getS3Key(), finalS3Key);

            // Delete temp file
            deleteS3Object(logo.getS3Key());

            // Build CloudFront URL
            String cloudFrontUrl = buildCloudFrontUrl(finalS3Key);

            // Use domain method to transition state
            logo.associateWith(entityType, entityId, finalS3Key, cloudFrontUrl);

            logoRepository.save(logo);

            log.info("Logo associated successfully: {}, CloudFront URL: {}", uploadId, cloudFrontUrl);

            return cloudFrontUrl;
        } catch (Exception e) {
            log.error("Failed to associate logo {}: {}", uploadId, e.getMessage(), e);
            throw new RuntimeException("Failed to associate logo: " + e.getMessage(), e);
        }
    }

    /**
     * Copy S3 object from source to destination
     * Used when moving logo from temp to final location
     */
    private void copyS3Object(String sourceKey, String destinationKey) {
        try {
            CopyObjectRequest copyRequest = CopyObjectRequest.builder()
                    .sourceBucket(bucketName)
                    .sourceKey(sourceKey)
                    .destinationBucket(bucketName)
                    .destinationKey(destinationKey)
                    .build();

            s3Client.copyObject(copyRequest);

            log.info("S3 object copied: {} → {}", sourceKey, destinationKey);
        } catch (S3Exception e) {
            log.error("Failed to copy S3 object: {}", e.getMessage());
            throw new RuntimeException("Failed to copy S3 object", e);
        }
    }

    /**
     * Delete S3 object
     * Used after copying temp file to final location
     */
    private void deleteS3Object(String key) {
        try {
            DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();

            s3Client.deleteObject(deleteRequest);

            log.info("S3 object deleted: {}", key);
        } catch (S3Exception e) {
            log.error("Failed to delete S3 object: {}", e.getMessage());
            // Don't throw - deletion failure is not critical
        }
    }

    /**
     * Build CloudFront CDN URL from S3 key
     * Story 2.5.3a: Event Theme Image Upload
     *
     * For MinIO (local dev): http://localhost:8450/{bucketName}/{s3Key}
     * For CloudFront (staging/prod): https://cdn.batbern.ch/{s3Key}
     */
    private String buildCloudFrontUrl(String s3Key) {
        // For MinIO (local dev), include bucket name in URL path
        if (cloudFrontDomain.contains("localhost")) {
            return cloudFrontDomain + "/" + bucketName + "/" + s3Key;
        }
        // For CloudFront, bucket name is configured in CloudFront origin
        return cloudFrontDomain + "/" + s3Key;
    }
}
