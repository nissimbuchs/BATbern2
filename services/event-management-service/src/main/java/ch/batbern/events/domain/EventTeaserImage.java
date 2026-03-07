package ch.batbern.events.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * EventTeaserImage entity — a single teaser image for the moderator presentation page.
 * <p>
 * Story 10.22: Event Teaser Images
 * Images are uploaded to S3; this entity holds the S3 key, CloudFront display URL,
 * and insertion-order displayOrder.
 */
@Entity
@Table(name = "event_teaser_images")
@Getter
@Setter
@NoArgsConstructor
public class EventTeaserImage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "event_code", nullable = false)
    private String eventCode;

    @Column(name = "s3_key", nullable = false, columnDefinition = "TEXT")
    private String s3Key;

    @Column(name = "image_url", nullable = false, columnDefinition = "TEXT")
    private String imageUrl;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "presentation_position", nullable = false, length = 30)
    private String presentationPosition = "AFTER_TOPIC_REVEAL";

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();
}
