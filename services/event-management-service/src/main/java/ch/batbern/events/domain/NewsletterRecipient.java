package ch.batbern.events.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Audit record for an individual newsletter recipient (Story 10.7 — AC10).
 *
 * <p>One row per subscriber per send operation.
 * Database table: {@code newsletter_recipients} (created by migration V67).
 */
@Entity
@Table(name = "newsletter_recipients")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NewsletterRecipient {

    @EmbeddedId
    private NewsletterRecipientId id;

    @Column(name = "delivery_status", nullable = false, length = 50)
    @Builder.Default
    private String deliveryStatus = "sent";
}
