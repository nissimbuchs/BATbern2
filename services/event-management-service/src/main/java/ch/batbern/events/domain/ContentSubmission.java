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
 * Content Submission entity representing speaker presentation title/abstract submissions.
 * Story 6.3: Speaker Content Self-Submission Portal
 *
 * Matches V53__Add_speaker_content_submissions.sql migration exactly.
 * Supports versioned submissions with revision workflow.
 *
 * Content Status Flow:
 * PENDING → (speaker submits) → SUBMITTED → (organizer reviews) → APPROVED
 *                                      ↓
 *                               REVISION_NEEDED → (speaker fixes) → SUBMITTED
 */
@Entity
@Table(name = "speaker_content_submissions")
@Data
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"speakerPool", "session"})  // Prevent circular reference
public class ContentSubmission {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(columnDefinition = "UUID")
    @EqualsAndHashCode.Include
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "speaker_pool_id", nullable = false)
    private SpeakerPool speakerPool;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id")
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
        // Calculate character count if not set
        if (abstractCharCount == null && contentAbstract != null) {
            abstractCharCount = contentAbstract.length();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
        // Recalculate character count on update
        if (contentAbstract != null) {
            abstractCharCount = contentAbstract.length();
        }
    }

    /**
     * Validates that the abstract does not exceed the maximum character limit.
     *
     * @return true if abstract is within limits
     */
    public boolean isAbstractValid() {
        return contentAbstract == null || contentAbstract.length() <= 1000;
    }

    /**
     * Validates that the title does not exceed the maximum character limit.
     *
     * @return true if title is within limits
     */
    public boolean isTitleValid() {
        return title == null || title.length() <= 200;
    }
}
