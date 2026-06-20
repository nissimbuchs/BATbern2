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
 * Composite primary key for {@link SessionQnaNotification} (Story 15.7).
 *
 * <p>Maps the {@code (window_id, recipient_username)} primary key from the
 * {@code session_qna_notification} table. {@code recipientUsername} is a cross-service
 * meaningful id (ADR-003 — no UUID FK to the user service).
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class SessionQnaNotificationId implements Serializable {

    @Column(name = "window_id", nullable = false, columnDefinition = "UUID")
    private UUID windowId;

    @Column(name = "recipient_username", nullable = false, length = 100)
    private String recipientUsername;
}
