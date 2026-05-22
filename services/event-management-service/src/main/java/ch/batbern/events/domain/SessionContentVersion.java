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
 * Versioned audit row in {@code session_content_history} (renamed from
 * {@code speaker_content_submissions} in V99). Each row is one submit of title+abstract
 * for a {@link Session}. The latest row mirrors what currently lives on
 * {@code sessions.title}/{@code sessions.description}; older rows preserve prior versions
 * and reviewer feedback.
 *
 * <p>Story 11.E.8 consolidation: the previous {@code ContentSubmission} entity was keyed
 * by {@code speaker_pool_id}, which conflated "who submitted" with "which talk." This
 * version is keyed by {@code session_id} so the audit log composes naturally with
 * multi-speaker sessions and re-promotions. {@code submitted_by_username} carries the
 * actor (speaker on the portal or organizer-on-behalf).
 *
 * <p>Reviewer fields ({@code reviewerFeedback}, {@code reviewedAt}, {@code reviewedBy})
 * are populated by {@link ch.batbern.events.service.QualityReviewService#rejectContent}
 * on the latest history row when content is rejected. {@code QualityReviewService#approveContent}
 * does NOT write to this table — approval is recorded by the workflow state transition
 * to {@code QUALITY_REVIEWED} in {@code speaker_status_history} instead.
 */
@Entity
@Table(name = "session_content_history")
@Data
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"session"})
public class SessionContentVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(columnDefinition = "UUID")
    @EqualsAndHashCode.Include
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private Session session;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "abstract", nullable = false, columnDefinition = "TEXT")
    private String contentAbstract;  // 'abstract' is a reserved keyword in Java

    @Column(name = "abstract_char_count", nullable = false)
    private Integer abstractCharCount;

    @Column(name = "submission_version", nullable = false)
    @Builder.Default
    private Integer submissionVersion = 1;

    /**
     * Story 11.E.8 consolidation: the actor who submitted this version (speaker on the
     * portal or organizer-on-behalf). Lets the audit trail stand on its own without
     * joining back to {@code speaker_status_history}.
     */
    @Column(name = "submitted_by_username", nullable = false, length = 100)
    private String submittedByUsername;

    @Column(name = "reviewer_feedback", columnDefinition = "TEXT")
    private String reviewerFeedback;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "reviewed_by", length = 255)
    private String reviewedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (updatedAt == null) {
            updatedAt = Instant.now();
        }
        if (submittedAt == null) {
            submittedAt = Instant.now();
        }
        if (submissionVersion == null) {
            submissionVersion = 1;
        }
        if (abstractCharCount == null && contentAbstract != null) {
            abstractCharCount = contentAbstract.length();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
        if (contentAbstract != null) {
            abstractCharCount = contentAbstract.length();
        }
    }
}
