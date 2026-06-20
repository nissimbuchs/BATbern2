package ch.batbern.events.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Per-(Q&A window, recipient) digest state for new-question notifications (Story 15.7).
 *
 * <ul>
 *   <li>{@code lastNotifiedAt} — throttle anchor. The {@link ch.batbern.events.service.QnaNotificationService}
 *       flush only re-emails once the recipient's cadence window has elapsed
 *       ({@code live} = 15 min, {@code daily} = 24 h).</li>
 *   <li>{@code notifiedThrough} — high-water mark: the {@code created_at} of the newest post
 *       already covered by a sent digest, so the next email counts only genuinely NEW questions.</li>
 * </ul>
 *
 * Database table: {@code session_qna_notification} (migration V117).
 */
@Entity
@Table(name = "session_qna_notification")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SessionQnaNotification {

    @EmbeddedId
    private SessionQnaNotificationId id;

    @Column(name = "last_notified_at")
    private Instant lastNotifiedAt;

    @Column(name = "notified_through")
    private Instant notifiedThrough;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
