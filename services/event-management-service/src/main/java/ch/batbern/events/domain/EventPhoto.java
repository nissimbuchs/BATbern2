package ch.batbern.events.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Persistable;

import java.time.Instant;
import java.util.UUID;

/**
 * EventPhoto entity representing photos uploaded for a BATbern event.
 * <p>
 * Story 10.21: Event Photos Gallery
 * Photos are stored in S3; this entity holds the S3 key and CloudFront display URL.
 * sort_order column is reserved for future drag-to-reorder (out of scope per AC12).
 */
@Entity
@Table(name = "event_photos")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventPhoto implements Persistable<UUID> {

    @Id
    private UUID id;

    /**
     * Signals to Spring Data that this is a new entity (INSERT, not merge/UPDATE).
     * Required because the ID is always set externally from Phase 1 photoId.
     * Without this, Spring Data calls merge() on a non-existent row → StaleObjectStateException.
     */
    @Transient
    @Builder.Default
    private boolean newEntity = false;

    @Override
    public boolean isNew() {
        return newEntity;
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        this.newEntity = false;
    }

    @Column(name = "event_code", nullable = false)
    private String eventCode;

    @Column(name = "s3_key", nullable = false)
    private String s3Key;

    @Column(name = "display_url", nullable = false)
    private String displayUrl;

    @Column(name = "filename")
    private String filename;

    @Column(name = "uploaded_at")
    private Instant uploadedAt;

    @Column(name = "uploaded_by")
    private String uploadedBy;

    @Column(name = "sort_order")
    private int sortOrder;
}
