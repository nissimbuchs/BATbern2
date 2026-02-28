package ch.batbern.events.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.UUID;

/**
 * Composite primary key for {@link NewsletterRecipient} (Story 10.7).
 *
 * <p>Maps the (send_id, email) primary key from the {@code newsletter_recipients} table.
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class NewsletterRecipientId implements Serializable {

    @Column(name = "send_id", nullable = false, columnDefinition = "UUID")
    private UUID sendId;

    @Column(name = "email", nullable = false, length = 255)
    private String email;
}
