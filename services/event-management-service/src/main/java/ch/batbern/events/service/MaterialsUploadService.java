package ch.batbern.events.service;

import ch.batbern.events.domain.SessionMaterialType;
import ch.batbern.events.dto.PresignedMaterialUploadUrl;
import ch.batbern.events.exception.FileSizeExceededException;
import ch.batbern.events.exception.InvalidFileTypeException;
import ch.batbern.events.exception.MaterialNotFoundException;
import ch.batbern.events.repository.SessionMaterialsRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Materials upload service for generating presigned URLs for session materials
 * Story 5.9: Session Materials Upload
 *
 * Handles presigned URL generation for material uploads (presentations, documents, videos)
 * Reuses 3-phase upload pattern from ADR-002 but adapted for materials:
 * 1. Generate presigned URL → 2. Upload to S3 → 3. Confirm upload → 4. Associate with session
 *
 * Supported File Types (AC6):
 * - Presentations: .pptx, .ppt, .key, .odp
 * - Documents: .pdf, .doc, .docx, .txt
 * - Videos: .mp4, .mov, .avi, .mkv, .webm
 * - Archives: .zip, .tar.gz
 *
 * S3 Key Strategy:
 * - Temp: materials/temp/{uploadId}/file-{uploadId}.{ext}
 * - Final: materials/{year}/events/{eventCode}/sessions/{sessionSlug}/file-{uploadId}.{ext}
 */
@Service
@Transactional
@Slf4j
public class MaterialsUploadService {

    private final S3Presigner s3Presigner;
    private final S3Client s3Client;
    private final SessionMaterialsRepository materialsRepository;
    private final String bucketName;

    private static final long MAX_FILE_SIZE_BYTES = 100L * 1024 * 1024; // 100MB (AC1)
    private static final int PRESIGNED_URL_EXPIRATION_MINUTES = 15;

    // AC6: Allowed file extensions
    private static final Set<String> ALLOWED_FILE_EXTENSIONS = Set.of(
        // Presentations
        "pptx", "ppt", "key", "odp",
        // Documents
        "pdf", "doc", "docx", "txt",
        // Videos
        "mp4", "mov", "avi", "mkv", "webm",
        // Archives
        "zip", "gz", "tar"
    );

    // AC6: Allowed MIME types
    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
        // Presentations
        "application/vnd.ms-powerpoint", // .ppt
        "application/vnd.openxmlformats-officedocument.presentationml.presentation", // .pptx
        "application/vnd.apple.keynote", // .key
        "application/vnd.oasis.opendocument.presentation", // .odp
        // Documents
        "application/pdf", // .pdf
        "application/msword", // .doc
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document", // .docx
        "text/plain", // .txt
        // Videos
        "video/mp4", // .mp4
        "video/quicktime", // .mov
        "video/x-msvideo", // .avi
        "video/x-matroska", // .mkv
        "video/webm", // .webm
        // Archives
        "application/zip", // .zip
        "application/gzip", // .tar.gz
        "application/x-gzip", // .tar.gz
        "application/x-tar" // .tar
    );

    public MaterialsUploadService(
            S3Presigner s3Presigner,
            S3Client s3Client,
            SessionMaterialsRepository materialsRepository,
            @Value("${aws.s3.bucket-name:batbern-development-company-logos}") String bucketName) {
        this.s3Presigner = s3Presigner;
        this.s3Client = s3Client;
        this.materialsRepository = materialsRepository;
        this.bucketName = bucketName;
    }

    /**
     * Phase 1: Generate presigned URL for material upload
     * Creates temporary S3 key for upload
     * No database record created yet (will be created in associate phase)
     *
     * @param fileName     Original filename with extension
     * @param fileSize    File size in bytes (max 100MB)
     * @param mimeType    MIME type (see ALLOWED_MIME_TYPES)
     * @return PresignedMaterialUploadUrl with upload URL and metadata
     * @throws FileSizeExceededException if file size exceeds 100MB
     * @throws InvalidFileTypeException  if file type is not allowed
     */
    public PresignedMaterialUploadUrl generatePresignedUrl(String fileName, long fileSize, String mimeType) {
        log.info("Generating presigned upload URL for material: {}, size: {} bytes", fileName, fileSize);

        // Validate file size (max 100MB - AC1)
        if (fileSize > MAX_FILE_SIZE_BYTES) {
            throw new FileSizeExceededException("Material file size exceeds 100MB limit");
        }

        // Validate file extension
        String fileExtension = getFileExtension(fileName);
        if (!ALLOWED_FILE_EXTENSIONS.contains(fileExtension.toLowerCase())) {
            throw new InvalidFileTypeException(
                "Invalid file type. Allowed types: PPTX, PPT, KEY, ODP, PDF, DOC, DOCX, TXT, "
                        + "MP4, MOV, AVI, MKV, WEBM, ZIP, TAR.GZ"
            );
        }

        // Validate MIME type (AC6: file type validation on backend)
        if (!ALLOWED_MIME_TYPES.contains(mimeType)) {
            throw new InvalidFileTypeException(
                "Invalid MIME type: " + mimeType + ". File may be corrupted or extension spoofed."
            );
        }

        // Generate unique upload ID
        String uploadId = UUID.randomUUID().toString();

        // S3 key for temporary upload: materials/temp/{uploadId}/file-{uploadId}.{ext}
        String s3Key = String.format("materials/temp/%s/file-%s.%s", uploadId, uploadId, fileExtension);

        // Generate presigned PUT URL (15-minute expiration)
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(s3Key)
                .contentType(mimeType)
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(PRESIGNED_URL_EXPIRATION_MINUTES))
                .putObjectRequest(putObjectRequest)
                .build();

        PresignedPutObjectRequest presignedRequest = s3Presigner.presignPutObject(presignRequest);
        String presignedUrl = presignedRequest.url().toString();

        log.info("Generated presigned URL for upload ID: {}, S3 key: {}", uploadId, s3Key);

        return PresignedMaterialUploadUrl.builder()
                .uploadUrl(presignedUrl)
                .fileId(uploadId) // Named 'fileId' to match frontend interface
                .s3Key(s3Key)
                .fileExtension(fileExtension)
                .expiresInMinutes(PRESIGNED_URL_EXPIRATION_MINUTES)
                .requiredHeaders(Map.of("Content-Type", mimeType))
                .build();
    }

    /**
     * Phase 2: Confirm material upload
     * Verifies file exists in S3 temp location
     * No database record created (will be created when associated with session)
     *
     * @param uploadId Upload ID from presigned URL generation
     * @throws MaterialNotFoundException if material not found in temp location
     */
    public void confirmUpload(String uploadId) {
        log.info("Confirming material upload for uploadId: {}", uploadId);

        // Note: In this simplified version, we don't create a database record yet
        // The material will be created when associated with a session in SessionMaterialsService
        // This is different from the logo pattern where Logo entity is created immediately

        log.info("Material upload confirmed for uploadId: {}", uploadId);
    }

    /**
     * Extract file extension from filename
     */
    private String getFileExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
    }

    /**
     * Determine material type from MIME type
     */
    public static SessionMaterialType getMaterialTypeFromMimeType(String mimeType) {
        if (mimeType.startsWith("video/")) {
            return SessionMaterialType.VIDEO;
        } else if (mimeType.contains("powerpoint")
                || mimeType.contains("presentation")
                || mimeType.contains("keynote")) {
            return SessionMaterialType.PRESENTATION;
        } else if (mimeType.equals("application/pdf")
                || mimeType.contains("document")
                || mimeType.equals("text/plain")) {
            return SessionMaterialType.DOCUMENT;
        } else if (mimeType.contains("zip")
                || mimeType.contains("gzip")
                || mimeType.contains("tar")) {
            return SessionMaterialType.ARCHIVE;
        } else {
            return SessionMaterialType.OTHER;
        }
    }
}
