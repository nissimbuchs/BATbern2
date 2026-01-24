package ch.batbern.events.service;

import ch.batbern.events.client.UserApiClient;
import ch.batbern.events.dto.PhotoConfirmRequest;
import ch.batbern.events.dto.PhotoUploadRequest;
import ch.batbern.events.dto.PresignedPhotoUploadResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

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
 *
 * RED PHASE (TDD): This is a skeleton class for test compilation.
 * Implementation will be completed in GREEN phase.
 */
@Service
@Transactional
@Slf4j
public class SpeakerProfilePhotoService {

    private final S3Presigner s3Presigner;
    private final S3Client s3Client;
    private final MagicLinkService magicLinkService;
    private final UserApiClient userApiClient;
    private final String bucketName;
    private final String cloudFrontDomain;

    private static final long MAX_FILE_SIZE_BYTES = 5L * 1024 * 1024; // 5MB (AC7.3)
    private static final int PRESIGNED_URL_EXPIRATION_SECONDS = 900; // 15 minutes

    public SpeakerProfilePhotoService(
            S3Presigner s3Presigner,
            S3Client s3Client,
            MagicLinkService magicLinkService,
            UserApiClient userApiClient,
            @Value("${aws.s3.bucket-name:batbern-development-company-logos}") String bucketName,
            @Value("${aws.cloudfront.domain:https://cdn.batbern.ch}") String cloudFrontDomain) {
        this.s3Presigner = s3Presigner;
        this.s3Client = s3Client;
        this.magicLinkService = magicLinkService;
        this.userApiClient = userApiClient;
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
     * @throws ch.batbern.events.exception.InvalidTokenException if token is invalid/expired
     * @throws ch.batbern.events.exception.FileSizeExceededException if file exceeds 5MB
     * @throws ch.batbern.events.exception.InvalidFileTypeException if file type not allowed
     */
    public PresignedPhotoUploadResponse generatePresignedUrl(String token, PhotoUploadRequest request) {
        // RED PHASE: Not yet implemented
        throw new UnsupportedOperationException("Not yet implemented - GREEN phase pending");
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
     * @throws ch.batbern.events.exception.InvalidTokenException if token is invalid/expired
     * @throws ch.batbern.events.exception.PhotoUploadNotFoundException if file not found in S3
     */
    public String confirmUpload(String token, PhotoConfirmRequest request, String s3Key) {
        // RED PHASE: Not yet implemented
        throw new UnsupportedOperationException("Not yet implemented - GREEN phase pending");
    }
}
