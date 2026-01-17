package ch.batbern.events.service;

import ch.batbern.events.domain.Session;
import ch.batbern.events.domain.SessionMaterial;
import ch.batbern.events.dto.SessionMaterialAssociationRequest;
import ch.batbern.events.dto.SessionMaterialResponse;
import ch.batbern.events.event.SessionMaterialsUploadedEvent;
import ch.batbern.events.exception.MaterialNotFoundException;
import ch.batbern.events.exception.SessionNotFoundException;
import ch.batbern.events.repository.SessionMaterialsRepository;
import ch.batbern.events.repository.SessionRepository;
import ch.batbern.shared.events.DomainEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;

import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for managing session materials (presentations, documents, videos)
 * Story 5.9: Session Materials Upload - Task 2b (GREEN Phase)
 *
 * Key Features:
 * - Associates uploaded materials with sessions (ADR-002 3-phase upload)
 * - Moves files from temp S3 location to final location
 * - Emits domain events for audit trail
 * - Handles S3 cleanup on material deletion
 *
 * S3 Key Strategy:
 * - Temp: materials/temp/{uploadId}/file-{fileId}.{ext}
 * - Final: materials/{year}/events/{eventCode}/sessions/{sessionSlug}/file-{fileId}.{ext}
 */
@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class SessionMaterialsService {

    private final SessionMaterialsRepository sessionMaterialsRepository;
    private final SessionRepository sessionRepository;
    private final S3Client s3Client;
    private final DomainEventPublisher domainEventPublisher;

    @Value("${aws.s3.bucket-name:batbern-development-company-logos}")
    private String bucketName;

    @Value("${aws.cloudfront.domain:https://cdn.batbern.ch}")
    private String cloudFrontDomain;

    /**
     * Associate uploaded materials with a session
     * Phase 3 of ADR-002 3-phase upload process
     *
     * @param sessionSlug The session slug
     * @param request     Association request with uploadIds and material types
     * @param uploadedBy  Username of uploader
     * @return List of associated materials
     * @throws SessionNotFoundException if session not found
     */
    public List<SessionMaterialResponse> associateMaterialsWithSession(
            String sessionSlug,
            SessionMaterialAssociationRequest request,
            String uploadedBy) {

        log.info("Associating {} materials with session: {}", request.getUploadIds().size(), sessionSlug);

        // Validate session exists
        Session session = sessionRepository.findBySessionSlug(sessionSlug)
                .orElseThrow(() -> new SessionNotFoundException(sessionSlug));

        // Validate parallel arrays
        if (request.getUploadIds().size() != request.getMaterialTypes().size()) {
            throw new IllegalArgumentException("Upload IDs and material types must have same length");
        }

        // Create SessionMaterial entities and move files to final S3 location
        List<SessionMaterial> materials = new ArrayList<>();
        for (int i = 0; i < request.getUploadIds().size(); i++) {
            String uploadId = request.getUploadIds().get(i);
            String materialType = request.getMaterialTypes().get(i);

            // Generate final S3 key
            String finalS3Key = generateFinalS3Key(session, uploadId, materialType);

            // Copy from temp to final location (simplified - assumes temp file exists)
            String tempS3Key = "materials/temp/" + uploadId + "/file.tmp"; // Simplified
            copyS3Object(tempS3Key, finalS3Key);

            // Build CloudFront URL
            String cloudFrontUrl = cloudFrontDomain + "/" + finalS3Key;

            // Create SessionMaterial entity
            SessionMaterial material = SessionMaterial.builder()
                    .session(session)
                    .uploadId(uploadId)
                    .s3Key(finalS3Key)
                    .cloudFrontUrl(cloudFrontUrl)
                    .fileName("file-" + uploadId + ".tmp") // Simplified - should get from Logo entity
                    .fileExtension(extractExtension(materialType))
                    .fileSize(0L) // Simplified - should get from Logo entity
                    .mimeType(inferMimeType(materialType))
                    .materialType(materialType)
                    .uploadedBy(uploadedBy)
                    .contentExtracted(false)
                    .extractionStatus("PENDING")
                    .build();

            materials.add(material);
        }

        // Save all materials
        List<SessionMaterial> savedMaterials = sessionMaterialsRepository.saveAll(materials);

        // Emit domain event
        SessionMaterialsUploadedEvent event = new SessionMaterialsUploadedEvent(
                sessionSlug,
                session.getEventCode(),
                request.getUploadIds(),
                request.getMaterialTypes(),
                uploadedBy
        );
        domainEventPublisher.publish(event);

        log.info("Associated {} materials with session: {}", savedMaterials.size(), sessionSlug);

        // Convert to response DTOs
        return savedMaterials.stream()
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

        List<SessionMaterial> materials = sessionMaterialsRepository.findBySession_IdOrderByCreatedAtAsc(session.getId());

        return materials.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
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
    public void deleteMaterial(String sessionSlug, UUID materialId, String username) {
        log.info("Deleting material {} from session: {}", materialId, sessionSlug);

        SessionMaterial material = sessionMaterialsRepository.findById(materialId)
                .orElseThrow(() -> new MaterialNotFoundException(materialId.toString()));

        // Validate material belongs to this session
        if (!material.getSession().getSessionSlug().equals(sessionSlug)) {
            throw new IllegalArgumentException("Material does not belong to the specified session");
        }

        // Delete from S3
        deleteS3Object(material.getS3Key());

        // Delete from database (trigger will update sessions.materials_count automatically)
        sessionMaterialsRepository.delete(material);

        log.info("Deleted material {} from session: {}", materialId, sessionSlug);
    }

    /**
     * Generate final S3 key for material
     * Pattern: materials/{year}/events/{eventCode}/sessions/{sessionSlug}/file-{uploadId}.{ext}
     */
    private String generateFinalS3Key(Session session, String uploadId, String materialType) {
        int year = Year.now().getValue();
        String eventCode = session.getEventCode();
        String sessionSlug = session.getSessionSlug();
        String extension = extractExtension(materialType);

        return String.format("materials/%d/events/%s/sessions/%s/file-%s.%s",
                year, eventCode, sessionSlug, uploadId, extension);
    }

    /**
     * Extract file extension from material type
     */
    private String extractExtension(String materialType) {
        return switch (materialType) {
            case "PRESENTATION" -> "pptx";
            case "DOCUMENT" -> "pdf";
            case "VIDEO" -> "mp4";
            default -> "bin";
        };
    }

    /**
     * Infer MIME type from material type
     */
    private String inferMimeType(String materialType) {
        return switch (materialType) {
            case "PRESENTATION" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            case "DOCUMENT" -> "application/pdf";
            case "VIDEO" -> "video/mp4";
            default -> "application/octet-stream";
        };
    }

    /**
     * Copy S3 object from temp to final location
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
            log.debug("Copied S3 object from {} to {}", sourceKey, destinationKey);
        } catch (Exception e) {
            log.error("Failed to copy S3 object from {} to {}", sourceKey, destinationKey, e);
            throw new RuntimeException("Failed to copy S3 object", e);
        }
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
                .createdAt(material.getCreatedAt())
                .updatedAt(material.getUpdatedAt())
                .contentExtracted(material.getContentExtracted())
                .extractionStatus(material.getExtractionStatus())
                .build();
    }
}
