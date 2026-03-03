package ch.batbern.events.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
public class EventPhoto {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

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
