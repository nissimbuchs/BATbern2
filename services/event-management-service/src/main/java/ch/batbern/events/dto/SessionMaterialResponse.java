package ch.batbern.events.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for session material
 * Story 5.9: Session Materials Upload
 *
 * Used in GET /sessions/{sessionSlug}/materials and embedded in Session responses
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionMaterialResponse {

    private UUID id;
    private String uploadId;
    private String s3Key;
    private String cloudFrontUrl;
    private String fileName;
    private String fileExtension;
    private Long fileSize;
    private String mimeType;
    private String materialType;
    private String uploadedBy;
    private Instant createdAt;
    private Instant updatedAt;

    // Forward compatibility for Story 5.10 (RAG search)
    private Boolean contentExtracted;
    private String extractionStatus;
}
