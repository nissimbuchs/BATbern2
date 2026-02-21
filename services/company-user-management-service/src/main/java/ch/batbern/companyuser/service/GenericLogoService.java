package ch.batbern.companyuser.service;

import ch.batbern.companyuser.domain.Logo;
import ch.batbern.companyuser.domain.LogoStatus;
import ch.batbern.companyuser.dto.PresignedUploadUrl;
import ch.batbern.companyuser.exception.FileSizeExceededException;
import ch.batbern.companyuser.exception.InvalidFileTypeException;
import ch.batbern.companyuser.exception.LogoNotFoundException;
import ch.batbern.companyuser.repository.LogoRepository;
import ch.batbern.shared.utils.CloudFrontUrlBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Generic file upload service for managing logo uploads to S3
 * Story 1.16.3: Generic File Upload Service
 * ADR-002: Generic File Upload Service Architecture
 *
 * Key Features:
 * - Entity-agnostic: Works for companies, users, events, partners, etc.
 * - Three-phase upload: Generate URL → Upload to S3 → Confirm → Associate with entity
 * - State machine: PENDING → CONFIRMED → ASSOCIATED
 * - S3 integration: Direct client-to-S3 uploads with presigned URLs
 * - Automatic cleanup: Orphaned uploads removed by LogoCleanupService
 *
 * S3 Key Strategy:
 * - Temp: logos/temp/{uploadId}/logo-{fileId}.{ext}
 * - Final: logos/{year}/{entity-type}/{entity-name}/logo-{fileId}.{ext}
 */
@Service
@Transactional
@Slf4j
public class GenericLogoService {

    private final S3Presigner s3Presigner;
    private final S3Client s3Client;
    private final LogoRepository logoRepository;
    private final String bucketName;
    private final String cloudFrontDomain;

    private static final long MAX_FILE_SIZE_BYTES = 5L * 1024 * 1024; // 5MB
    private static final int PRESIGNED_URL_EXPIRATION_MINUTES = 15;
    private static final Set<String> ALLOWED_FILE_EXTENSIONS = Set.of("png", "jpg", "jpeg", "svg");
    private static final Set<String> ALLOWED_MIME_TYPES = Set.of("image/png", "image/jpeg", "image/svg+xml");

    public GenericLogoService(
            S3Presigner s3Presigner,
            S3Client s3Client,
            LogoRepository logoRepository,
            @Value("${aws.s3.bucket-name:batbern-development-company-logos}") String bucketName,
            @Value("${aws.cloudfront.domain:https://cdn.batbern.ch}") String cloudFrontDomain) {
        this.s3Presigner = s3Presigner;
        this.s3Client = s3Client;
        this.logoRepository = logoRepository;
        this.bucketName = bucketName;
        this.cloudFrontDomain = cloudFrontDomain;
    }

    /**
     * Phase 1: Generate presigned URL for logo upload
     * Creates Logo entity with PENDING status
     * No entity association required at this stage
     *
     * @param fileName     Original filename with extension
     * @param fileSize    File size in bytes
     * @param mimeType    MIME type (image/png, image/jpeg, image/svg+xml)
     * @return PresignedUploadUrl with upload URL and metadata
     * @throws FileSizeExceededException if file size exceeds 5MB
     * @throws InvalidFileTypeException  if file type is not allowed
     */
    public PresignedUploadUrl generatePresignedUrl(String fileName, long fileSize, String mimeType) {
        log.info("Generating presigned upload URL for file: {}, size: {} bytes", fileName, fileSize);

        // Validate file size (max 5MB)
        if (fileSize > MAX_FILE_SIZE_BYTES) {
            throw new FileSizeExceededException("Logo file size exceeds 5MB limit");
        }

        // Validate file type
        String fileExtension = getFileExtension(fileName);
        if (!ALLOWED_FILE_EXTENSIONS.contains(fileExtension)) {
            throw new InvalidFileTypeException("Invalid file type. Allowed types: PNG, JPG, JPEG, SVG");
        }

        if (!ALLOWED_MIME_TYPES.contains(mimeType)) {
            throw new InvalidFileTypeException("Invalid MIME type. Allowed: image/png, image/jpeg, image/svg+xml");
        }

        // Generate unique identifiers
        String uploadId = UUID.randomUUID().toString();
        String fileId = UUID.randomUUID().toString();

        // Generate temp S3 key (no entity reference)
        String tempS3Key = generateTempS3Key(uploadId, fileId, fileExtension);

        // Create Logo entity with PENDING status
        Logo logo = Logo.builder()
                .uploadId(uploadId)
                .s3Key(tempS3Key)
                .fileExtension(fileExtension)
                .fileSize(fileSize)
                .mimeType(mimeType)
                .status(LogoStatus.PENDING)
                .expiresAt(Instant.now().plus(24, ChronoUnit.HOURS)) // Expires in 24h
                .build();

        logoRepository.save(logo);

        // Generate presigned URL
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(tempS3Key)
                .contentType(mimeType)
                .contentLength(fileSize)
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(PRESIGNED_URL_EXPIRATION_MINUTES))
                .putObjectRequest(putObjectRequest)
                .build();

        PresignedPutObjectRequest presignedRequest = s3Presigner.presignPutObject(presignRequest);
        String uploadUrl = presignedRequest.url().toString();

        log.info("Generated presigned URL for upload ID: {}, S3 key: {}", uploadId, tempS3Key);

        return PresignedUploadUrl.builder()
                .uploadUrl(uploadUrl)
                .fileId(uploadId) // Use uploadId as fileId for client reference
                .s3Key(tempS3Key)
                .fileExtension(fileExtension)
                .expiresInMinutes(PRESIGNED_URL_EXPIRATION_MINUTES)
                .requiredHeaders(Map.of("Content-Type", mimeType))
                .build();
    }

    /**
     * Phase 2: Confirm logo upload completion
     * Updates Logo entity to CONFIRMED status
     * Client calls this after successfully uploading to S3
     *
     * @param uploadId Upload identifier from Phase 1
     * @param checksum SHA-256 checksum for integrity verification (optional)
     * @throws LogoNotFoundException if upload ID not found
     */
    public void confirmUpload(String uploadId, String checksum) {
        log.info("Confirming logo upload for uploadId: {}", uploadId);

        Logo logo = logoRepository.findByUploadId(uploadId)
                .orElseThrow(() -> new LogoNotFoundException(uploadId));

        // Use domain method to transition state
        logo.markAsConfirmed(checksum, Instant.now().plus(7, ChronoUnit.DAYS)); // Expires in 7 days

        logoRepository.save(logo);

        log.info("Logo upload confirmed: {}, status: {}", uploadId, logo.getStatus());
    }

    /**
     * Phase 3: Associate logo with entity
     * Copies S3 file from temp to final location
     * Updates Logo entity to ASSOCIATED status
     * Called during entity creation (e.g., CompanyService.createCompany)
     *
     * @param uploadId   Upload identifier from Phase 1
     * @param entityType Type of entity (COMPANY, USER, EVENT, etc.)
     * @param entityId   Entity identifier (e.g., company name, username)
     * @param finalS3Key Final S3 location for the file
     * @return CloudFront URL for accessing the logo
     * @throws LogoNotFoundException if upload ID not found
     * @throws IllegalStateException if logo not in CONFIRMED state
     */
    public String associateLogoWithEntity(String uploadId, String entityType, String entityId, String finalS3Key) {
        log.info("Associating logo {} with entity: {} ({})", uploadId, entityType, entityId);

        Logo logo = logoRepository.findByUploadId(uploadId)
                .orElseThrow(() -> new LogoNotFoundException(uploadId));

        if (logo.getStatus() != LogoStatus.CONFIRMED) {
            throw new IllegalStateException(
                    "Logo must be CONFIRMED before association. Current status: " + logo.getStatus());
        }

        // Copy S3 object from temp to final location
        copyS3Object(logo.getS3Key(), finalS3Key);

        // Delete temp file
        deleteS3Object(logo.getS3Key());

        // Build CloudFront URL
        String cloudFrontUrl = CloudFrontUrlBuilder.buildUrl(cloudFrontDomain, bucketName, finalS3Key);

        // Use domain method to transition state
        logo.associateWith(entityType, entityId, finalS3Key, cloudFrontUrl);

        logoRepository.save(logo);

        log.info("Logo associated successfully: {}, CloudFront URL: {}", uploadId, cloudFrontUrl);

        return cloudFrontUrl;
    }

    /**
     * Delete unused logo (before association)
     * Can only delete logos in PENDING or CONFIRMED status
     *
     * @param uploadId Upload identifier
     * @throws LogoNotFoundException if upload ID not found
     * @throws IllegalStateException if logo is already ASSOCIATED
     */
    public void deleteUnusedLogo(String uploadId) {
        log.info("Deleting unused logo: {}", uploadId);

        Logo logo = logoRepository.findByUploadId(uploadId)
                .orElseThrow(() -> new LogoNotFoundException(uploadId));

        if (logo.getStatus() == LogoStatus.ASSOCIATED) {
            throw new IllegalStateException("Cannot delete ASSOCIATED logo");
        }

        // Delete S3 file
        deleteS3Object(logo.getS3Key());

        // Delete database record
        logoRepository.delete(logo);

        log.info("Unused logo deleted: {}", uploadId);
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
        } catch (Exception e) {
            log.error("Failed to copy S3 object from {} to {}", sourceKey, destinationKey, e);
            throw new RuntimeException("Failed to copy S3 object", e);
        }
    }

    /**
     * Delete S3 object
     * Used for cleaning up temp files and orphaned uploads
     */
    private void deleteS3Object(String s3Key) {
        try {
            DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(s3Key)
                    .build();

            s3Client.deleteObject(deleteRequest);

            log.info("S3 object deleted: {}", s3Key);
        } catch (Exception e) {
            log.error("Failed to delete S3 object: {}", s3Key, e);
            // Don't throw - cleanup failures shouldn't break the flow
        }
    }

    /**
     * Generate temp S3 key for uploaded file
     * Pattern: logos/temp/{uploadId}/logo-{fileId}.{ext}
     * No leading slash - S3 keys should not start with /
     */
    private String generateTempS3Key(String uploadId, String fileId, String extension) {
        return String.format("logos/temp/%s/logo-%s.%s", uploadId, fileId, extension);
    }

    /**
     * Extract file extension from filename
     */
    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            throw new InvalidFileTypeException("Invalid file type. No file extension found.");
        }
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }

    /**
     * Upload logo directly to S3 (server-side only)
     * Used by uploadImageFromUrl endpoint to bypass frontend entirely
     * This avoids binary data corruption issues with JPEG/BMP files
     *
     * @param imageData Binary image data
     * @param fileName Original filename with extension
     * @param mimeType MIME type (image/png, image/jpeg, etc.)
     * @return Upload ID for use in company creation
     * @throws FileSizeExceededException if file size exceeds 5MB
     * @throws InvalidFileTypeException if file type is not allowed
     */
    public String uploadLogoDirectly(byte[] imageData, String fileName, String mimeType) {
        log.info("Uploading logo directly to S3: {}, size: {} bytes", fileName, imageData.length);

        // Validate file size (max 5MB)
        if (imageData.length > MAX_FILE_SIZE_BYTES) {
            throw new FileSizeExceededException("Logo file size exceeds 5MB limit");
        }

        // Validate file type
        String fileExtension = getFileExtension(fileName);
        if (!ALLOWED_FILE_EXTENSIONS.contains(fileExtension)) {
            throw new InvalidFileTypeException("Invalid file type. Allowed types: PNG, JPG, JPEG, SVG");
        }

        if (!ALLOWED_MIME_TYPES.contains(mimeType)) {
            throw new InvalidFileTypeException("Invalid MIME type. Allowed: image/png, image/jpeg, image/svg+xml");
        }

        // Generate unique identifiers
        String uploadId = UUID.randomUUID().toString();
        String fileId = UUID.randomUUID().toString();

        // Generate temp S3 key
        String tempS3Key = generateTempS3Key(uploadId, fileId, fileExtension);

        // Upload directly to S3
        try {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(tempS3Key)
                    .contentType(mimeType)
                    .contentLength((long) imageData.length)
                    .build();

            s3Client.putObject(putObjectRequest,
                    software.amazon.awssdk.core.sync.RequestBody.fromBytes(imageData));

            log.info("Uploaded file to S3: {}", tempS3Key);

        } catch (Exception e) {
            log.error("Failed to upload file to S3: {}", tempS3Key, e);
            throw new RuntimeException("Failed to upload file to S3", e);
        }

        // Create Logo entity with CONFIRMED status (skip PENDING since upload is complete)
        Logo logo = Logo.builder()
                .uploadId(uploadId)
                .s3Key(tempS3Key)
                .fileExtension(fileExtension)
                .fileSize((long) imageData.length)
                .mimeType(mimeType)
                .status(LogoStatus.CONFIRMED)
                .expiresAt(Instant.now().plus(7, ChronoUnit.DAYS)) // Expires in 7 days
                .build();

        logoRepository.save(logo);

        log.info("Logo uploaded directly with uploadId: {}", uploadId);

        return uploadId;
    }
}
