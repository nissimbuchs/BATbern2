package ch.batbern.events.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * A single Q&A post — a question (top-level) or an answer ({@code parentPostId} set) — within a
 * {@link SessionQnaWindow} (Story 7.5).
 *
 * <p>Always attributed to a logged-in {@code postedByUsername} (ADR-003 meaningful id). Organizer
 * takedown is a soft-delete: {@code removedAt} is set and the post renders as a "removed by
 * organizer" tombstone, preserving thread coherence in the frozen archive (Resolved Decision #3).
 */
@Entity
@Table(name = "session_qna_post")
@Data
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionQnaPost {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(columnDefinition = "UUID")
    @EqualsAndHashCode.Include
    private UUID id;

    @Column(name = "window_id", nullable = false, columnDefinition = "UUID")
    private UUID windowId;

    /** Self-reference for one-level threading; {@code null} for a top-level question. */
    @Column(name = "parent_post_id", columnDefinition = "UUID")
    private UUID parentPostId;

    @Column(name = "posted_by_username", nullable = false, length = 100)
    private String postedByUsername;

    @Column(name = "body", nullable = false, columnDefinition = "TEXT")
    private String body;

    @Column(name = "removed_at")
    private Instant removedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public boolean isRemoved() {
        return removedAt != null;
    }
}
