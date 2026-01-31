package ch.batbern.events.service;

import ch.batbern.events.domain.Speaker;
import ch.batbern.events.dto.PhotoConfirmRequest;
import ch.batbern.events.dto.PhotoUploadRequest;
import ch.batbern.events.dto.PresignedPhotoUploadResponse;
import ch.batbern.events.dto.TokenValidationResult;
import ch.batbern.events.exception.FileSizeExceededException;
import ch.batbern.events.exception.InvalidFileTypeException;
import ch.batbern.events.exception.InvalidTokenException;
import ch.batbern.events.exception.PhotoUploadNotFoundException;
import ch.batbern.events.repository.SpeakerRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.time.Year;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Service for speaker profile photo upload operations.
 * Story 6.2b: Speaker Profile Update Portal - AC7 (Profile Photo Upload)
 *
 * Handles:
 * - Presigned URL generation for direct S3 upload
 * - Upload confirmation and S3 verification
 * - Cross-service sync to User.profilePictureUrl
 *
 * Uses 3-phase upload pattern from ADR-002:
 * 1. Generate presigned URL
 * 2. Client uploads directly to S3
 * 3. Confirm upload and update User profile
 */
@Service
@Transactional
@Slf4j
public class SpeakerProfilePhotoService {

    private final S3Presigner s3Presigner;
    private final S3Client s3Client;
    private final MagicLinkService magicLinkService;
    private final SpeakerRepository speakerRepository;
    private final String bucketName;
    private final String cloudFrontDomain;

    private static final long MAX_FILE_SIZE_BYTES = 5L * 1024 * 1024; // 5MB (AC7.3)
    private static final int PRESIGNED_URL_EXPIRATION_SECONDS = 900; // 15 minutes

    // AC7.2: Allowed content types for profile photos
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp"
    );

    public SpeakerProfilePhotoService(
            S3Presigner s3Presigner,
            S3Client s3Client,
            MagicLinkService magicLinkService,
            SpeakerRepository speakerRepository,
            @Value("${aws.s3.bucket-name:batbern-development-company-logos}") String bucketName,
            @Value("${aws.cloudfront.domain:https://cdn.batbern.ch}") String cloudFrontDomain) {
        this.s3Presigner = s3Presigner;
        this.s3Client = s3Client;
        this.magicLinkService = magicLinkService;
        this.speakerRepository = speakerRepository;
        this.bucketName = bucketName;
        this.cloudFrontDomain = cloudFrontDomain;
    }

    /**
     * Phase 1: Generate presigned URL for photo upload.
     * AC7.1: Photo upload via presigned URL
     *
     * @param token Magic link token for authentication
     * @param request Photo upload request with file metadata
     * @return Presigned URL response with upload details
     * @throws InvalidTokenException if token is invalid/expired
     * @throws FileSizeExceededException if file exceeds 5MB
     * @throws InvalidFileTypeException if file type not allowed
     */
    public PresignedPhotoUploadResponse generatePresignedUrl(String token, PhotoUploadRequest request) {
        log.info("Generating presigned URL for photo upload, fileName: {}", request.getFileName());

        // 1. Validate token
        TokenValidationResult tokenResult = magicLinkService.validateToken(token);
        if (!tokenResult.valid()) {
            log.warn("Photo upload token validation failed: {}", tokenResult.error());
            throw new InvalidTokenException(tokenResult.error());
        }

        String username = tokenResult.username();
        log.debug("Token valid for username: {}", username);

        // 2. Validate file size (AC7.3: max 5MB)
        if (request.getFileSize() > MAX_FILE_SIZE_BYTES) {
            log.warn("Photo upload rejected - file size {} exceeds 5MB limit for user: {}",
                    request.getFileSize(), username);
            throw new FileSizeExceededException(
                    "Profile photo exceeds maximum size of 5MB");
        }

        // 3. Validate content type (AC7.2: JPEG, PNG, WebP only)
        if (!ALLOWED_CONTENT_TYPES.contains(request.getContentType())) {
            log.warn("Photo upload rejected - invalid content type {} for user: {}",
                    request.getContentType(), username);
            throw new InvalidFileTypeException(
                    "Invalid content type. Allowed types: image/jpeg, image/png, image/webp");
        }

        // 4. Generate unique upload ID
        String uploadId = UUID.randomUUID().toString();

        // 5. Extract file extension
        String fileExtension = getFileExtension(request.getFileName(), request.getContentType());

        // 6. Build S3 key: speaker-profiles/{year}/{username}/photo-{uploadId}.{ext}
        String s3Key = String.format("speaker-profiles/%s/%s/photo-%s.%s",
                Year.now().getValue(),
                username,
                uploadId,
                fileExtension);

        // 7. Generate presigned PUT URL
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(s3Key)
                .contentType(request.getContentType())
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofSeconds(PRESIGNED_URL_EXPIRATION_SECONDS))
                .putObjectRequest(putObjectRequest)
                .build();

        PresignedPutObjectRequest presignedRequest = s3Presigner.presignPutObject(presignRequest);
        String presignedUrl = presignedRequest.url().toString();

        log.info("Generated presigned URL for photo upload - uploadId: {}, s3Key: {}, user: {}",
                uploadId, s3Key, username);

        return PresignedPhotoUploadResponse.builder()
                .uploadUrl(presignedUrl)
                .uploadId(uploadId)
                .s3Key(s3Key)
                .expiresIn(PRESIGNED_URL_EXPIRATION_SECONDS)
                .maxSizeBytes(MAX_FILE_SIZE_BYTES)
                .build();
    }

    /**
     * Phase 3: Confirm photo upload and update User profile.
     * AC7.4: Upload confirmation
     * AC7.5: Cross-service sync
     *
     * @param token Magic link token for authentication
     * @param request Confirm request with uploadId
     * @param s3Key S3 object key where file was uploaded
     * @return CloudFront URL of the uploaded photo
     * @throws InvalidTokenException if token is invalid/expired
     * @throws PhotoUploadNotFoundException if file not found in S3
     */
    public String confirmUpload(String token, PhotoConfirmRequest request, String s3Key) {
        log.info("Confirming photo upload - uploadId: {}", request.getUploadId());

        // 1. Validate token
        TokenValidationResult tokenResult = magicLinkService.validateToken(token);
        if (!tokenResult.valid()) {
            log.warn("Photo confirm token validation failed: {}", tokenResult.error());
            throw new InvalidTokenException(tokenResult.error());
        }

        String username = tokenResult.username();
        log.debug("Token valid for username: {}", username);

        // 2. Verify file exists in S3
        try {
            HeadObjectRequest headRequest = HeadObjectRequest.builder()
                    .bucket(bucketName)
                    .key(s3Key)
                    .build();
            s3Client.headObject(headRequest);
            log.debug("Photo upload verified in S3: {}", s3Key);

        } catch (NoSuchKeyException e) {
            log.warn("Photo upload not found in S3 - uploadId: {}, s3Key: {}",
                    request.getUploadId(), s3Key);
            throw new PhotoUploadNotFoundException(request.getUploadId());
        }

        // 3. Build CloudFront URL
        String cloudFrontUrl = cloudFrontDomain + "/" + s3Key;

        // 4. Update Speaker entity with photo URL (stored locally, no auth required)
        Optional<Speaker> speakerOpt = speakerRepository.findByUsername(username);
        if (speakerOpt.isPresent()) {
            Speaker speaker = speakerOpt.get();
            speaker.setProfilePictureUrl(cloudFrontUrl);
            speakerRepository.save(speaker);
            log.info("Photo upload confirmed - user: {}, url: {}", username, cloudFrontUrl);
        } else {
            log.warn("Speaker not found for username: {} - photo URL not saved to profile", username);
        }

        return cloudFrontUrl;
    }

    /**
     * Extract file extension from filename or derive from content type.
     */
    private String getFileExtension(String fileName, String contentType) {
        // Try to get from filename first
        if (fileName != null && fileName.contains(".")) {
            String ext = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
            if (Set.of("jpg", "jpeg", "png", "webp").contains(ext)) {
                return ext.equals("jpeg") ? "jpg" : ext;
            }
        }

        // Fall back to content type
        return switch (contentType) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            default -> "jpg";
        };
    }
}
