package ch.batbern.companyuser.service;

import ch.batbern.companyuser.domain.User;
import ch.batbern.companyuser.dto.PresignedUploadUrl;
import ch.batbern.companyuser.exception.FileSizeExceededException;
import ch.batbern.companyuser.exception.InvalidFileTypeException;
import ch.batbern.companyuser.exception.UserNotFoundException;
import ch.batbern.companyuser.repository.UserRepository;
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
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Service for managing user profile picture uploads to S3
 * Implements AC10: Profile picture upload with presigned URLs and CloudFront integration
 *
 * Key Features:
 * - Generate presigned S3 upload URLs (15-minute expiration)
 * - Validate file types (PNG, JPG, JPEG, SVG only)
 * - Validate file sizes (max 5 MB)
 * - Store S3 keys and CloudFront URLs in User entity
 * - Support direct client-to-S3 uploads (no proxy through backend)
 */
@Service
@Transactional
@Slf4j
public class ProfilePictureService {

    private final S3Presigner s3Presigner;
    private final UserRepository userRepository;
    private final String bucketName;
    private final String cloudFrontDomain;

    public ProfilePictureService(
            S3Presigner s3Presigner,
            UserRepository userRepository,
            @Value("${aws.s3.bucket-name:batbern-development-company-logos}") String bucketName,
            @Value("${aws.cloudfront.domain:https://cdn.batbern.ch}") String cloudFrontDomain) {
        this.s3Presigner = s3Presigner;
        this.userRepository = userRepository;
        this.bucketName = bucketName;
        this.cloudFrontDomain = cloudFrontDomain;
    }

    private static final long MAX_FILE_SIZE_BYTES = 5L * 1024 * 1024; // 5 MB
    private static final int PRESIGNED_URL_EXPIRATION_MINUTES = 15;
    private static final Set<String> ALLOWED_FILE_EXTENSIONS = Set.of("png", "jpg", "jpeg", "svg");

    /**
     * Generate a presigned S3 URL for profile picture upload
     * AC10.1: Generate presigned URLs for secure uploads
     *
     * @param userId User UUID (internal database ID)
     * @param username User's username (public identifier)
     * @param filename Original filename with extension
     * @param fileSizeBytes File size in bytes
     * @return PresignedUploadUrl with upload URL and metadata
     * @throws FileSizeExceededException if file size exceeds 5 MB
     * @throws InvalidFileTypeException if file type is not allowed
     */
    public PresignedUploadUrl generateProfilePictureUploadUrl(UUID userId, String username, String filename, long fileSizeBytes) {
        log.info("Generating presigned upload URL for user: {}, username: {}, filename: {}, size: {} bytes",
                userId, username, filename, fileSizeBytes);

        // AC10.2: Validate file size (max 5 MB)
        if (fileSizeBytes > MAX_FILE_SIZE_BYTES) {
            throw new FileSizeExceededException("Profile picture file size exceeds 5MB limit");
        }

        // AC10.2: Validate file type (PNG, JPG, JPEG, SVG only)
        String fileExtension = getFileExtension(filename);
        if (!ALLOWED_FILE_EXTENSIONS.contains(fileExtension)) {
            throw new InvalidFileTypeException("Invalid file type. Allowed types: PNG, JPG, JPEG, SVG");
        }

        // Generate unique file ID and S3 key
        String fileId = UUID.randomUUID().toString();
        String s3Key = generateS3Key(username, fileId, fileExtension);
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
                .fileExtension(fileExtension)
                .expiresInMinutes(PRESIGNED_URL_EXPIRATION_MINUTES)
                .requiredHeaders(Map.of("Content-Type", mimeType))
                .build();
    }

    /**
     * Confirm profile picture upload and store S3 reference in User entity
     * AC10.3: Store S3 key after upload completion
     * AC10.4: Store CloudFront URL for CDN access
     *
     * @param userId User UUID (internal database ID)
     * @param username User's username (public identifier)
     * @param fileId File ID from presigned URL generation
     * @param fileExtension File extension (e.g., "png", "jpg", "svg")
     * @throws UserNotFoundException if user not found
     */
    public void confirmProfilePictureUpload(UUID userId, String username, String fileId, String fileExtension) {
        log.info("Confirming profile picture upload for user: {}, username: {}, file ID: {}, extension: {}",
                userId, username, fileId, fileExtension);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(username));

        // Build CloudFront URL and S3 key
        String s3Key = findS3KeyByFileId(username, fileId, fileExtension);
        String cloudFrontUrl = buildCloudFrontUrl(s3Key);

        // Update user with profile picture references
        user.setProfilePictureUrl(cloudFrontUrl);
        user.setProfilePictureS3Key(s3Key);

        userRepository.save(user);

        log.info("Profile picture upload confirmed for user: {}, CloudFront URL: {}", username, cloudFrontUrl);
    }

    /**
     * Generate S3 key with year-based partitioning
     * Pattern: profile-pictures/{year}/{username}/{filename-with-uuid}.{ext}
     * Example: profile-pictures/2024/john.doe/profile-f3e8d1a4.png
     * Note: No leading slash to avoid double slash in presigned URL
     */
    private String generateS3Key(String username, String fileId, String extension) {
        int currentYear = LocalDate.now().getYear();
        return String.format("profile-pictures/%d/%s/profile-%s.%s",
                currentYear,
                username,
                fileId,
                extension);
    }

    /**
     * Build CloudFront CDN URL from S3 key
     * AC10.4: Serve profile pictures through CloudFront
     * Note: In local development (MinIO), we need to include bucket name in path
     * In production (CloudFront), the bucket is behind the CDN
     */
    private String buildCloudFrontUrl(String s3Key) {
        // Check if we're in local development (MinIO) by checking if domain contains localhost
        if (cloudFrontDomain.contains("localhost")) {
            // Local MinIO: include bucket name in path
            return cloudFrontDomain + "/" + bucketName + "/" + s3Key;
        }
        // Production CloudFront: bucket is behind CDN
        return cloudFrontDomain + "/" + s3Key;
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
     * Find S3 key by file ID and username
     * Reconstructs the S3 key using the same pattern as generateS3Key
     *
     * @param username User's username
     * @param fileId File ID from presigned URL generation
     * @param fileExtension File extension (e.g., "png", "jpg", "svg")
     * @return S3 key for the uploaded file
     */
    private String findS3KeyByFileId(String username, String fileId, String fileExtension) {
        int currentYear = LocalDate.now().getYear();
        return String.format("profile-pictures/%d/%s/profile-%s.%s", currentYear, username, fileId, fileExtension);
    }
}
