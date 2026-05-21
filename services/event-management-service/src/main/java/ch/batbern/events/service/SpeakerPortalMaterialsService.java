package ch.batbern.events.service;

import ch.batbern.events.domain.Session;
import ch.batbern.events.domain.SessionMaterial;
import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.events.dto.SpeakerMaterialConfirmRequest;
import ch.batbern.events.dto.SpeakerMaterialConfirmResponse;
import ch.batbern.events.dto.SpeakerMaterialUploadRequest;
import ch.batbern.events.dto.SpeakerMaterialUploadResponse;
import ch.batbern.events.exception.FileSizeExceededException;
import ch.batbern.events.exception.InvalidFileTypeException;
import ch.batbern.events.repository.SessionMaterialsRepository;
import ch.batbern.events.repository.SessionRepository;
import ch.batbern.shared.utils.CloudFrontUrlBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.time.Year;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Service for speaker self-service material uploads.
 * Story 6.3: Speaker Content Self-Submission Portal - AC7.
 *
 * <p>Handles:
 * <ul>
 *   <li>Presigned URL generation for presentation files</li>
 *   <li>Upload confirmation and session association</li>
 * </ul>
 *
 * <p>Supported file types (AC7): PPTX, PPT, KEY, PDF (max 50MB).
 *
 * <p>Story 11.E.3 (ADR-009 §Decision 3): magic-link token bridge removed. The controller
 * now authenticates the request via Cognito Bearer + {@code @PreAuthorize("hasRole('SPEAKER')")},
 * resolves the {@link SpeakerPool} via {@link SpeakerPortalAuthorizationService}, and hands
 * it to the service methods directly.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SpeakerPortalMaterialsService {

    private static final long MAX_FILE_SIZE_BYTES = 50L * 1024 * 1024; // 50MB for speaker uploads
    private static final int PRESIGNED_URL_EXPIRATION_MINUTES = 15;

    // AC7: Allowed file extensions for speaker uploads (presentation files only)
    private static final Set<String> ALLOWED_FILE_EXTENSIONS = Set.of(
        "pptx", "ppt", "key", "pdf"
    );

    // AC7: Allowed MIME types for speaker uploads
    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
        "application/vnd.ms-powerpoint", // .ppt
        "application/vnd.openxmlformats-officedocument.presentationml.presentation", // .pptx
        "application/vnd.apple.keynote", // .key
        "application/pdf" // .pdf
    );

    private final SessionRepository sessionRepository;
    private final SessionMaterialsRepository sessionMaterialsRepository;
    private final S3Presigner s3Presigner;
    private final S3Client s3Client;
    private final PrimarySpeakerResolver primarySpeakerResolver;

    @Value("${aws.s3.bucket-name:batbern-development-company-logos}")
    private String bucketName;

    @Value("${aws.cloudfront.domain:https://cdn.batbern.ch}")
    private String cloudFrontDomain;

    /**
     * Generate presigned URL for speaker material upload.
     * Story 6.3 AC7 + Story 11.E.3: caller has resolved {@code SpeakerPool} via Cognito auth.
     *
     * @param speaker  speaker_pool row (pre-resolved by the controller)
     * @param request  upload request with fileName, fileSize, mimeType
     * @return presigned URL + upload metadata
     */
    @Transactional(readOnly = true)
    public SpeakerMaterialUploadResponse generatePresignedUrl(
            SpeakerPool speaker, SpeakerMaterialUploadRequest request) {
        log.info("Generating presigned URL for speaker material upload: {} (speakerPoolId={})",
                request.fileName(), speaker.getId());

        // Validate file size (max 50MB - AC7)
        if (request.fileSize() > MAX_FILE_SIZE_BYTES) {
            throw new FileSizeExceededException("File size exceeds 50MB limit");
        }

        // Validate file extension
        String fileExtension = getFileExtension(request.fileName());
        if (!ALLOWED_FILE_EXTENSIONS.contains(fileExtension.toLowerCase())) {
            throw new InvalidFileTypeException(
                "Invalid file type. Allowed types: PPTX, PPT, KEY, PDF"
            );
        }

        // Validate MIME type
        if (!ALLOWED_MIME_TYPES.contains(request.mimeType())) {
            throw new InvalidFileTypeException(
                "Invalid MIME type: " + request.mimeType() + ". File may be corrupted."
            );
        }

        // Generate unique upload ID
        String uploadId = UUID.randomUUID().toString();

        // S3 key for temporary upload
        String s3Key = String.format("materials/temp/%s/file-%s.%s", uploadId, uploadId, fileExtension);

        // Generate presigned PUT URL
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(s3Key)
                .contentType(request.mimeType())
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(PRESIGNED_URL_EXPIRATION_MINUTES))
                .putObjectRequest(putObjectRequest)
                .build();

        PresignedPutObjectRequest presignedRequest = s3Presigner.presignPutObject(presignRequest);
        String presignedUrl = presignedRequest.url().toString();

        log.info("Generated presigned URL for speaker {}, uploadId: {}",
                speaker.getSpeakerName(), uploadId);

        return new SpeakerMaterialUploadResponse(
                presignedUrl,
                uploadId,
                s3Key,
                fileExtension,
                PRESIGNED_URL_EXPIRATION_MINUTES,
                Map.of("Content-Type", request.mimeType())
        );
    }

    /**
     * Confirm material upload and associate with speaker's session.
     * Story 6.3 AC7 + Story 11.E.3: caller has resolved {@code SpeakerPool} via Cognito auth.
     *
     * @param speaker  speaker_pool row (pre-resolved by the controller)
     * @param request  confirm request with upload details
     */
    @Transactional
    public SpeakerMaterialConfirmResponse confirmUpload(
            SpeakerPool speaker, SpeakerMaterialConfirmRequest request) {
        log.info("Confirming speaker material upload: {} (speakerPoolId={})",
                request.uploadId(), speaker.getId());

        // Validate session assignment
        if (speaker.getSessionId() == null) {
            throw new IllegalStateException("No session assigned - cannot upload materials");
        }

        Session session = sessionRepository.findById(speaker.getSessionId())
                .orElseThrow(() -> new IllegalStateException("Session not found"));

        // Generate final S3 key
        int year = Year.now().getValue();
        String finalS3Key = String.format("materials/%d/events/%s/sessions/%s/file-%s.%s",
                year, session.getEventCode(), session.getSessionSlug(),
                request.uploadId(), request.fileExtension());

        // Copy file from temp location to final location in S3
        String tempS3Key = String.format("materials/temp/%s/file-%s.%s",
                request.uploadId(), request.uploadId(), request.fileExtension());
        copyS3Object(tempS3Key, finalS3Key);

        // Build CloudFront URL
        String cloudFrontUrl = CloudFrontUrlBuilder.buildUrl(cloudFrontDomain, bucketName, finalS3Key);

        // Create SessionMaterial entity
        SessionMaterial material = SessionMaterial.builder()
                .session(session)
                .uploadId(request.uploadId())
                .s3Key(finalS3Key)
                .cloudFrontUrl(cloudFrontUrl)
                .fileName(request.fileName())
                .fileExtension(request.fileExtension())
                .fileSize(request.fileSize())
                .mimeType(request.mimeType())
                .materialType(request.materialType() != null ? request.materialType() : "PRESENTATION")
                // Story 11.E.9: username comes from session_users via PrimarySpeakerResolver
                // (the speaker_pool.username column is gone). Speakers reaching material upload
                // are CONTENT_SUBMITTED+ and always have a primary session_users row; the
                // resolver returning empty here would be a real provisioning bug.
                .uploadedBy(primarySpeakerResolver.resolve(speaker)
                        .map(PrimarySpeakerResolver.PrimarySpeakerProfile::username)
                        .orElseThrow(() -> new IllegalStateException(
                                "Speaker " + speaker.getId() + " has no resolvable username — "
                                        + "missing PRIMARY_SPEAKER session_users row")))
                .contentExtracted(false)
                .extractionStatus("PENDING")
                .build();

        material = sessionMaterialsRepository.save(material);

        log.info("Material confirmed for speaker {}, materialId: {}",
                speaker.getSpeakerName(), material.getId());

        return new SpeakerMaterialConfirmResponse(
                material.getId(),
                request.uploadId(),
                request.fileName(),
                cloudFrontUrl,
                material.getMaterialType(),
                material.getCreatedAt()
        );
    }

    /**
     * Copy S3 object from temp upload location to final location.
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
            log.info("Copied S3 object from {} to {}", sourceKey, destinationKey);
        } catch (Exception e) {
            log.error("Failed to copy S3 object from {} to {}", sourceKey, destinationKey, e);
            throw new RuntimeException("Failed to move uploaded file to final location", e);
        }
    }

    /**
     * Extract file extension from filename.
     */
    private String getFileExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
    }
}
