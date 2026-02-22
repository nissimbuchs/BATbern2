package ch.batbern.partners.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Toggle vote cast by a partner company on a topic suggestion — Story 8.2.
 * Composite PK: (topic_id, company_name) — one vote per company per topic.
 * Toggle ON = row INSERT; Toggle OFF = row DELETE.
 * Schema: topic_votes (V4 migration).
 */
@Entity
@Table(name = "topic_votes")
@IdClass(TopicVoteId.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TopicVote {

    @Id
    @Column(name = "topic_id", nullable = false)
    private UUID topicId;

    /** ADR-003: meaningful company identifier. */
    @Id
    @Column(name = "company_name", nullable = false, length = 255)
    private String companyName;

    @CreationTimestamp
    @Column(name = "voted_at", nullable = false, updatable = false)
    private Instant votedAt;
}
