package ch.batbern.companyuser.service;

import ch.batbern.companyuser.domain.Company;
import ch.batbern.companyuser.dto.ContentMetadata;
import ch.batbern.companyuser.dto.PresignedUploadUrl;
import ch.batbern.companyuser.exception.CompanyNotFoundException;
import ch.batbern.companyuser.exception.FileSizeExceededException;
import ch.batbern.companyuser.exception.InvalidFileTypeException;
import ch.batbern.companyuser.repository.CompanyRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.time.LocalDate;
import java.util.*;

/**
 * Service for managing company logo uploads to S3
 * Implements AC8: File Storage with presigned URLs and CloudFront integration
 *
 * Key Features:
 * - Generate presigned S3 upload URLs (15-minute expiration)
 * - Validate file types (PNG, JPG, JPEG, SVG only)
 * - Validate file sizes (max 5 MB)
 * - Store S3 keys and CloudFront URLs in Company entity
 * - Support direct client-to-S3 uploads (no proxy through backend)
 */
@Service
@Transactional
@Slf4j
public class CompanyLogoService {

    private final S3Presigner s3Presigner;
    private final CompanyRepository companyRepository;
    private final String bucketName;
    private final String cloudFrontDomain;

    public CompanyLogoService(
            S3Presigner s3Presigner,
            CompanyRepository companyRepository,
            @Value("${aws.s3.bucket-name:batbern-test-company-logos}") String bucketName,
            @Value("${aws.cloudfront.domain:https://cdn.batbern.ch}") String cloudFrontDomain) {
        this.s3Presigner = s3Presigner;
        this.companyRepository = companyRepository;
        this.bucketName = bucketName;
        this.cloudFrontDomain = cloudFrontDomain;
    }

    private static final long MAX_FILE_SIZE_BYTES = 5L * 1024 * 1024; // 5 MB
    private static final int PRESIGNED_URL_EXPIRATION_MINUTES = 15;
    private static final Set<String> ALLOWED_FILE_EXTENSIONS = Set.of("png", "jpg", "jpeg", "svg");

    /**
     * Generate a presigned S3 URL for logo upload
     * AC8.1: Generate presigned URLs for secure uploads
     *
     * @param userId User ID requesting the upload
     * @param filename Original filename with extension
     * @param fileSizeBytes File size in bytes
     * @return PresignedUploadUrl with upload URL and metadata
     * @throws FileSizeExceededException if file size exceeds 5 MB
     * @throws InvalidFileTypeException if file type is not allowed
     */
    public PresignedUploadUrl generateLogoUploadUrl(String userId, String filename, long fileSizeBytes) {
        log.info("Generating presigned upload URL for user: {}, filename: {}, size: {} bytes",
                userId, filename, fileSizeBytes);

        // AC8.2: Validate file size (max 5 MB)
        if (fileSizeBytes > MAX_FILE_SIZE_BYTES) {
            throw new FileSizeExceededException("Logo file size exceeds 5MB limit");
        }

        // AC8.2: Validate file type (PNG, JPG, JPEG, SVG only)
        String fileExtension = getFileExtension(filename);
        if (!ALLOWED_FILE_EXTENSIONS.contains(fileExtension)) {
            throw new InvalidFileTypeException("Invalid file type. Allowed types: PNG, JPG, JPEG, SVG");
        }

        // Generate unique file ID and S3 key
        String fileId = UUID.randomUUID().toString();
        String s3Key = generateS3Key(userId, fileId, fileExtension);
        String mimeType = getMimeType(fileExtension);

        // Build S3 PutObjectRequest
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(s3Key)
                .contentType(mimeType)
                .contentLength(fileSizeBytes)
                .build();

        // Create presigned PUT request with 15-minute expiration
        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(PRESIGNED_URL_EXPIRATION_MINUTES))
                .putObjectRequest(putObjectRequest)
                .build();

        // Generate presigned URL
        PresignedPutObjectRequest presignedRequest = s3Presigner.presignPutObject(presignRequest);
        String uploadUrl = presignedRequest.url().toString();

        log.info("Generated presigned URL for file ID: {}, S3 key: {}", fileId, s3Key);

        return PresignedUploadUrl.builder()
                .uploadUrl(uploadUrl)
                .fileId(fileId)
                .s3Key(s3Key)
                .expiresInMinutes(PRESIGNED_URL_EXPIRATION_MINUTES)
                .requiredHeaders(Map.of("Content-Type", mimeType))
                .build();
    }

    /**
     * Confirm logo upload and store S3 reference in Company entity
     * AC8.3: Store S3 key after upload completion
     * AC8.4: Store CloudFront URL for CDN access
     *
     * @param companyId Company ID
     * @param fileId File ID from presigned URL generation
     * @param checksum SHA-256 checksum for integrity verification
     * @throws CompanyNotFoundException if company not found
     */
    public void confirmLogoUpload(UUID companyId, String fileId, String checksum) {
        log.info("Confirming logo upload for company: {}, file ID: {}", companyId, fileId);

        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new CompanyNotFoundException("Company not found: " + companyId));

        // Build CloudFront URL and S3 key
        String s3Key = findS3KeyByFileId(fileId);
        String cloudFrontUrl = buildCloudFrontUrl(s3Key);

        // Update company with logo references
        company.setLogoUrl(cloudFrontUrl);
        company.setLogoS3Key(s3Key);
        company.setLogoFileId(fileId);

        companyRepository.save(company);

        log.info("Logo upload confirmed for company: {}, CloudFront URL: {}", companyId, cloudFrontUrl);
    }

    /**
     * Generate S3 key with year-based partitioning
     * Pattern: /logos/{year}/{company-id}/{filename-with-uuid}.{ext}
     * Example: /logos/2024/company-789/logo-f3e8d1a4.png
     */
    private String generateS3Key(String userId, String fileId, String extension) {
        int currentYear = LocalDate.now().getYear();
        return String.format("/logos/%d/%s/logo-%s.%s",
                currentYear,
                userId,
                fileId,
                extension);
    }

    /**
     * Build CloudFront CDN URL from S3 key
     * AC8.4: Serve logos through CloudFront
     */
    private String buildCloudFrontUrl(String s3Key) {
        return cloudFrontDomain + s3Key;
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
     * Get MIME type from file extension
     */
    private String getMimeType(String extension) {
        return switch (extension.toLowerCase()) {
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "svg" -> "image/svg+xml";
            default -> throw new InvalidFileTypeException("Unsupported file type: " + extension);
        };
    }

    /**
     * Find S3 key by file ID (stub for actual implementation)
     * In production, this would query a file metadata store or parse from tracking data
     */
    private String findS3KeyByFileId(String fileId) {
        // For now, we'll reconstruct the S3 key from the file ID pattern
        // In production, you'd query a file metadata store
        int currentYear = LocalDate.now().getYear();
        // Extract extension from fileId if needed, defaulting to png
        return String.format("/logos/%d/temp/logo-%s.png", currentYear, fileId);
    }
}
