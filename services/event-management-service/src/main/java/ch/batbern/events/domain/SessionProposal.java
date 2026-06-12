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
 * Story 7.2 "I Could Speak on That" / ADR-012 — a self-nomination's proposed talk.
 *
 * <p>A self-nomination is an <em>application / pitch</em>, distinct from the speaker (workflow,
 * {@code speaker_pool}) and the session (content, {@code sessions} + {@code session_users} +
 * {@code session_content_history}). The pitch lives here — NEVER as content columns on
 * {@code speaker_pool} (ADR-012; that would reverse the Epic 11.E.8 normalization).
 *
 * <p>This entity holds <strong>no status</strong>: acceptance / rejection is read from the
 * linked {@code speaker_pool} workflow state (single source of workflow truth, ADR-009). At
 * promote-to-READY the proposal seeds the canonical session and then becomes immutable audit.
 */
@Entity
@Table(name = "session_proposals")
@Data
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionProposal {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(columnDefinition = "UUID")
    @EqualsAndHashCode.Include
    private UUID id;

    /** The {@code speaker_pool} row this pitch belongs to (intra-service FK — same DB, ADR-003 ok). */
    @Column(name = "speaker_pool_id", nullable = false, columnDefinition = "UUID")
    private UUID speakerPoolId;

    @Column(name = "event_id", nullable = false, columnDefinition = "UUID")
    private UUID eventId;

    /** JWT username of the self-nominating attendee. Meaningful ID per ADR-003 — never a UUID. */
    @Column(name = "proposed_by_username", nullable = false, length = 100)
    private String proposedByUsername;

    /** Proposed talk title. Length 200 to match {@code session_content_history.title} (lossless seed). */
    @Column(name = "proposed_title", nullable = false, length = 200)
    private String proposedTitle;

    /** Proposed talk abstract, stored raw (no agent pre-screen). */
    @Column(name = "proposed_abstract", nullable = false, columnDefinition = "TEXT")
    private String proposedAbstract;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
