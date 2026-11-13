package ch.batbern.events.service;

import ch.batbern.shared.utils.S3Links;
import ch.batbern.events.domain.EventManagementUpload;
import ch.batbern.events.domain.Session;
import ch.batbern.events.domain.SessionMaterial;
import ch.batbern.events.event.SessionMaterialsUploadedEvent;
import ch.batbern.events.exception.MaterialNotFoundException;
import ch.batbern.events.exception.SessionNotFoundException;
import ch.batbern.events.repository.EventManagementUploadRepository;
import ch.batbern.events.repository.SessionMaterialsRepository;
import ch.batbern.events.repository.SessionRepository;
import ch.batbern.events.sessions.dto.generated.MaterialUploadItem;
import ch.batbern.events.sessions.dto.generated.SessionMaterialAssociationRequest;
import ch.batbern.events.sessions.dto.generated.SessionMaterialResponse;
import ch.batbern.shared.events.DomainEventPublisher;
import ch.batbern.shared.upload.UploadService;
import ch.batbern.shared.utils.CloudFrontUrlBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;

import java.time.Duration;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for managing session materials (presentations, documents, videos)
 * Story 5.9: Session Materials Upload - Task 2b (GREEN Phase)
 *
 * Key Features:
 * - Attaches confirmed uploads of purpose session-material to sessions (Story 17.11d; the upload
 *   itself runs on the upload contract, {@link SessionMaterialUploadHandler})
 * - Emits domain events for audit trail
 * - Handles S3 cleanup on material deletion
 *
 * S3 key: materials/events/{eventCode}/sessions/{sessionSlug}/file-{uploadId}.{ext} (no year since
 * Story 17.11d; earlier materials keep theirs)
 */
@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class SessionMaterialsService {

    private final SessionMaterialsRepository sessionMaterialsRepository;
    private final SessionRepository sessionRepository;
    private final S3Client s3Client;
    private final software.amazon.awssdk.services.s3.presigner.S3Presigner s3Presigner;
    private final DomainEventPublisher domainEventPublisher;
    private final UploadService<EventManagementUpload> uploadService;
    private final SessionMaterialUploadHandler materialUploads;
    private final EventManagementUploadRepository uploadRepository;

    @Value("${aws.s3.bucket-name:batbern-development-company-logos}")
    private String bucketName;

    @Value("${aws.cloudfront.domain:https://cdn.batbern.ch}")
    private String cloudFrontDomain;

    /**
     * Attaches confirmed {@code session-material} uploads to a session (Story 17.11d): the organizer's
     * session editor uploads before the session exists, confirms without a target and names the
     * uploads here once the session is saved. Name, size and type come from the upload; the material
     * type is the one given, else derived (PRESENTATION for a speaker). An unknown, expired,
     * other-purpose or other-session upload is 404, one not CONFIRMED 409.
     *
     * @throws SessionNotFoundException if the session is not in the event
     */
    public List<SessionMaterialResponse> associateMaterialsWithSession(
            String eventCode,
            String sessionSlug,
            SessionMaterialAssociationRequest request,
            String uploadedBy) {

        log.info("Associating {} materials with session: {}", request.getMaterials().size(), sessionSlug);

        Session session = materialUploads.session(eventCode, sessionSlug);
        materialUploads.checkMayChangeMaterials(session);
        String targetId = SessionMaterialUploadHandler.targetId(session.getEventCode(), sessionSlug);

        List<SessionMaterial> materials = new ArrayList<>();
        for (MaterialUploadItem item : request.getMaterials()) {
            EventManagementUpload upload = uploadService.associate(SessionMaterialUploadHandler.PURPOSE,
                    item.getUploadId(), targetId);
            String type = item.getMaterialType() == null ? null : item.getMaterialType().getValue();
            materials.add(materialUploads.createMaterial(session, upload, type, uploadedBy));
        }
        materialUploads.published(session, materials, uploadedBy);

        log.info("Associated {} materials with session: {}", materials.size(), sessionSlug);
        return materials.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * Get all materials for a session
     *
     * @param sessionSlug The session slug
     * @return List of materials ordered by creation time
     * @throws SessionNotFoundException if session not found
     */
    public List<SessionMaterialResponse> getMaterialsBySession(String sessionSlug) {
        log.info("Fetching materials for session: {}", sessionSlug);

        Session session = sessionRepository.findBySessionSlug(sessionSlug)
                .orElseThrow(() -> new SessionNotFoundException(sessionSlug));

        List<SessionMaterial> materials = sessionMaterialsRepository
                .findBySession_IdOrderByCreatedAtAsc(session.getId());

        return materials.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * Generate presigned download URL for a material
     * Story 5.9: Session Materials Upload
     *
     * Uses injected S3Presigner which is configured for:
     * - Local: MinIO at localhost:8450
     * - Staging/Prod: AWS S3
     *
     * @param materialId The material ID
     * @return Presigned download URL valid for 1 hour
     * @throws MaterialNotFoundException if material not found
     */
    public String generateDownloadUrl(UUID materialId) {
        log.info("Generating download URL for material: {}", materialId);

        SessionMaterial material = sessionMaterialsRepository.findById(materialId)
                .orElseThrow(() -> new MaterialNotFoundException(materialId.toString()));

        // Generate presigned URL valid for 1 hour using injected presigner
        try {
            // Story 17.11d: the download carries the original file name (Content-Disposition);
            // a row without one falls back to the key's name.
            String url = S3Links.downloadUrl(s3Presigner, bucketName, material.getS3Key(), Duration.ofHours(1),
                    downloadName(material));
            // #1187: never log the link itself, it is a bearer credential for an hour
            log.info("Generated download URL for material {} (key {})", materialId, material.getS3Key());
            return url;
        } catch (Exception e) {
            log.error("Failed to generate download URL for material {}", materialId, e);
            throw new RuntimeException("Failed to generate download URL", e);
        }
    }

    /** The name a download is saved under: the original file name, else the last segment of the key. */
    static String downloadName(SessionMaterial material) {
        if (material.getFileName() != null && !material.getFileName().isBlank()) {
            return material.getFileName();
        }
        String key = material.getS3Key();
        return key.substring(key.lastIndexOf('/') + 1);
    }

    /**
     * Delete a material and clean up S3
     *
     * @param sessionSlug The session slug
     * @param materialId  The material ID
     * @param username    Username requesting deletion (for RBAC)
     * @throws MaterialNotFoundException if material not found
     * @throws IllegalArgumentException  if material doesn't belong to session
     */
    public void deleteMaterial(String eventCode, String sessionSlug, UUID materialId, String username) {
        log.info("Deleting material {} from session: {}", materialId, sessionSlug);

        SessionMaterial material = sessionMaterialsRepository.findById(materialId)
                .orElseThrow(() -> new MaterialNotFoundException(materialId.toString()));

        // Validate material belongs to this session of this event (Story 17.11d: the event too)
        if (!material.getSession().getSessionSlug().equals(sessionSlug)
                || (eventCode != null && !eventCode.equals(material.getSession().getEventCode()))) {
            throw new IllegalArgumentException("Material does not belong to the specified session");
        }

        // Delete from S3
        deleteS3Object(material.getS3Key());

        // Delete from database (trigger will update sessions.materials_count automatically)
        sessionMaterialsRepository.delete(material);
        // The upload row of a material added on the upload contract (Story 17.11d): ASSOCIATED rows
        // never expire, so it would stay forever. Materials added before 17.11d have none.
        uploadRepository.findByUploadId(material.getUploadId())
                .filter(u -> SessionMaterialUploadHandler.PURPOSE.equals(u.getPurpose()))
                .ifPresent(uploadRepository::delete);

        log.info("Deleted material {} from session: {}", materialId, sessionSlug);
    }

    /**
     * Upload material from URL (for batch import)
     * Used by session batch import to fetch PDFs from CDN and associate with sessions
     *
     * @param sessionSlug  The session slug
     * @param url          URL to fetch material from (CDN URL)
     * @param filename     Original filename
     * @param materialType Material type (PRESENTATION, DOCUMENT, VIDEO, OTHER)
     * @param uploadedBy   Username of uploader
     * @return Created material response
     * @throws SessionNotFoundException if session not found
     */
    // new URL(String) is deprecated (not for removal — stays in Java 21 / SB4) but is
    // used here deliberately: it parses URLs with spaces/special chars that the strict
    // URI(String) constructor rejects, so we can re-encode them component-by-component.
    @SuppressWarnings("deprecation")
    public SessionMaterialResponse uploadMaterialFromUrl(
            String sessionSlug,
            String url,
            String filename,
            String materialType,
            String uploadedBy) {

        log.info("Uploading material from URL for session {}: {}", sessionSlug, filename);

        // Validate session exists
        Session session = sessionRepository.findBySessionSlug(sessionSlug)
                .orElseThrow(() -> new SessionNotFoundException(sessionSlug));

        try {
            // Fetch file from URL with extended timeout for large PDFs
            log.debug("Fetching material from URL: {} (filename: {})", url, filename);

            // URL-encode the URL to handle spaces and special characters
            java.net.URI uri;
            try {
                uri = new java.net.URI(url);
            } catch (java.net.URISyntaxException e) {
                // URL contains invalid characters (e.g., spaces) - encode it
                try {
                    java.net.URL urlObj = new java.net.URL(url);
                    uri = new java.net.URI(
                            urlObj.getProtocol(),
                            urlObj.getUserInfo(),
                            urlObj.getHost(),
                            urlObj.getPort(),
                            urlObj.getPath(),
                            urlObj.getQuery(),
                            urlObj.getRef()
                    );
                } catch (Exception encodingException) {
                    String errorMsg = String.format(
                            "Invalid URL format (session: %s, filename: %s, url: %s): %s",
                            sessionSlug, filename, url, encodingException.getMessage());
                    log.error(errorMsg, encodingException);
                    throw new RuntimeException(errorMsg, encodingException);
                }
            }

            java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                    .connectTimeout(java.time.Duration.ofSeconds(15))
                    .followRedirects(java.net.http.HttpClient.Redirect.NORMAL)
                    .build();

            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                    .uri(uri)
                    .timeout(java.time.Duration.ofSeconds(120)) // Increased to 2 minutes for large files
                    .GET()
                    .build();

            log.debug("Downloading material from CDN...");
            long downloadStart = System.currentTimeMillis();

            java.net.http.HttpResponse<byte[]> response = client.send(request,
                    java.net.http.HttpResponse.BodyHandlers.ofByteArray());

            long downloadTime = System.currentTimeMillis() - downloadStart;

            if (response.statusCode() != 200) {
                String errorMsg = String.format("Failed to fetch file from URL (HTTP %d): %s",
                        response.statusCode(), url);
                log.error(errorMsg);
                throw new RuntimeException(errorMsg);
            }

            byte[] fileData = response.body();
            long fileSize = fileData.length;

            log.info("Downloaded material from CDN: {} bytes in {}ms (filename: {})",
                    fileSize, downloadTime, filename);

            // Extract file extension
            String fileExtension = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();

            // Determine MIME type
            String mimeType = determineMimeType(fileExtension);

            // Generate unique upload ID
            String uploadId = UUID.randomUUID().toString();

            // Generate final S3 key
            String finalS3Key = finalS3Key(session.getEventCode(), session.getSessionSlug(), uploadId, fileExtension);

            // Upload directly to final S3 location
            uploadToS3(finalS3Key, fileData, mimeType);

            // Build CloudFront URL
            String cloudFrontUrl = CloudFrontUrlBuilder.buildUrl(cloudFrontDomain, bucketName, finalS3Key);

            // Create SessionMaterial entity
            SessionMaterial material = SessionMaterial.builder()
                    .session(session)
                    .uploadId(uploadId)
                    .s3Key(finalS3Key)
                    .cloudFrontUrl(cloudFrontUrl)
                    .fileName(filename)
                    .fileExtension(fileExtension)
                    .fileSize(fileSize)
                    .mimeType(mimeType)
                    .materialType(materialType)
                    .uploadedBy(uploadedBy)
                    .contentExtracted(false)
                    .extractionStatus("PENDING")
                    .build();

            // Save material
            SessionMaterial savedMaterial = sessionMaterialsRepository.save(material);

            // Emit domain event
            SessionMaterialsUploadedEvent event = new SessionMaterialsUploadedEvent(
                    sessionSlug,
                    session.getEventCode(),
                    List.of(uploadId),
                    List.of(materialType),
                    uploadedBy
            );
            domainEventPublisher.publish(event);

            log.info("Uploaded material from URL for session {}: {} (uploadId: {})",
                    sessionSlug, filename, uploadId);

            return toResponse(savedMaterial);

        } catch (java.net.http.HttpTimeoutException e) {
            String errorMsg = String.format(
                    "Timeout downloading material from CDN (session: %s, filename: %s, url: %s)",
                    sessionSlug, filename, url);
            log.error(errorMsg, e);
            throw new RuntimeException(errorMsg, e);
        } catch (java.io.IOException e) {
            String errorMsg = String.format(
                    "Network error downloading material (session: %s, filename: %s, url: %s): %s",
                    sessionSlug, filename, url, e.getMessage());
            log.error(errorMsg, e);
            throw new RuntimeException(errorMsg, e);
        } catch (Exception e) {
            String errorMsg = String.format(
                    "Failed to upload material from URL (session: %s, filename: %s, url: %s): %s",
                    sessionSlug, filename, url, e.getMessage());
            log.error(errorMsg, e);
            throw new RuntimeException(errorMsg, e);
        }
    }

    /**
     * Determine MIME type from file extension
     */
    private String determineMimeType(String fileExtension) {
        return switch (fileExtension.toLowerCase()) {
            case "pdf" -> "application/pdf";
            case "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            case "ppt" -> "application/vnd.ms-powerpoint";
            case "doc" -> "application/msword";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "mp4" -> "video/mp4";
            case "mov" -> "video/quicktime";
            case "avi" -> "video/x-msvideo";
            default -> "application/octet-stream";
        };
    }

    /**
     * Upload byte array to S3
     */
    private void uploadToS3(String s3Key, byte[] data, String mimeType) {
        try {
            software.amazon.awssdk.services.s3.model.PutObjectRequest putRequest =
                    software.amazon.awssdk.services.s3.model.PutObjectRequest.builder()
                            .bucket(bucketName)
                            .key(s3Key)
                            .contentType(mimeType)
                            .contentLength((long) data.length)
                            .build();

            s3Client.putObject(putRequest,
                    software.amazon.awssdk.core.sync.RequestBody.fromBytes(data));

            log.debug("Uploaded {} bytes to S3: {}", data.length, s3Key);
        } catch (Exception e) {
            log.error("Failed to upload to S3: {}", s3Key, e);
            throw new RuntimeException("Failed to upload to S3", e);
        }
    }

    /**
     * The final key of a session material, without the year (Story 17.11d, as 11a):
     * {@code materials/events/{eventCode}/sessions/{sessionSlug}/file-{uploadId}.{ext}}. Materials stored
     * before keep their key.
     */
    static String finalS3Key(String eventCode, String sessionSlug, String uploadId, String fileExtension) {
        return String.format("materials/events/%s/sessions/%s/file-%s.%s",
                eventCode, sessionSlug, uploadId, fileExtension);
    }

    /**
     * Delete S3 object
     */
    private void deleteS3Object(String s3Key) {
        try {
            DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(s3Key)
                    .build();

            s3Client.deleteObject(deleteRequest);
            log.debug("Deleted S3 object: {}", s3Key);
        } catch (Exception e) {
            log.error("Failed to delete S3 object: {}", s3Key, e);
            // Don't throw - allow database deletion to proceed
        }
    }

    /**
     * Convert SessionMaterial entity to response DTO
     */
    private SessionMaterialResponse toResponse(SessionMaterial material) {
        return SessionMaterialResponse.builder()
                .id(material.getId())
                .uploadId(material.getUploadId())
                .s3Key(material.getS3Key())
                .cloudFrontUrl(material.getCloudFrontUrl())
                .fileName(material.getFileName())
                .fileExtension(material.getFileExtension())
                .fileSize(material.getFileSize())
                .mimeType(material.getMimeType())
                .materialType(material.getMaterialType())
                .uploadedBy(material.getUploadedBy())
                .createdAt(material.getCreatedAt() != null
                        ? material.getCreatedAt().atOffset(ZoneOffset.UTC) : null)
                .updatedAt(material.getUpdatedAt() != null
                        ? material.getUpdatedAt().atOffset(ZoneOffset.UTC) : null)
                .contentExtracted(material.getContentExtracted())
                .extractionStatus(material.getExtractionStatus())
                .build();
    }
}
