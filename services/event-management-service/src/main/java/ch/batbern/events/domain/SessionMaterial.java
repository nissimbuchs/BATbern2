package ch.batbern.events.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.Instant;
import java.util.UUID;

/**
 * Session Material entity representing uploaded files (presentations, documents, videos)
 * Story 5.9: Session Materials Upload - Task 1b (GREEN Phase)
 *
 * Matches V41__Add_session_materials.sql migration exactly.
 * Follows ADR-002 Generic File Upload Service pattern.
 */
@Entity
@Table(name = "session_materials")
@Data
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"session"})  // Prevent circular reference
public class SessionMaterial {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(columnDefinition = "UUID")
    @EqualsAndHashCode.Include
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private Session session;

    @Column(name = "upload_id", unique = true, nullable = false, length = 100)
    private String uploadId;  // From Generic Upload Service (ADR-002)

    @Column(name = "s3_key", nullable = false, length = 500)
    private String s3Key;

    @Column(name = "cloudfront_url", nullable = false, length = 1000)
    private String cloudFrontUrl;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "file_extension", nullable = false, length = 10)
    private String fileExtension;

    @Column(name = "file_size", nullable = false)
    private Long fileSize;  // Size in bytes

    @Column(name = "mime_type", nullable = false, length = 100)
    private String mimeType;

    @Column(name = "material_type", nullable = false, length = 50)
    private String materialType;  // PRESENTATION, DOCUMENT, VIDEO, OTHER

    @Column(name = "uploaded_by", nullable = false)
    private String uploadedBy;  // Username who uploaded

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // Content extraction tracking (for future RAG search - Story 5.10)
    @Column(name = "content_extracted", nullable = false)
    private Boolean contentExtracted;

    @Column(name = "extraction_status", nullable = false, length = 20)
    private String extractionStatus;  // PENDING, IN_PROGRESS, COMPLETED, FAILED, NOT_APPLICABLE

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (updatedAt == null) {
            updatedAt = Instant.now();
        }
        if (contentExtracted == null) {
            contentExtracted = false;
        }
        if (extractionStatus == null) {
            extractionStatus = "PENDING";
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
