package ch.batbern.partners.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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
 * Topic suggestion submitted by a partner company — Story 8.2.
 * Schema: topic_suggestions (V4 migration).
 * Identity: company_name (ADR-003 meaningful ID), no FK to partners table.
 */
@Entity
@Table(name = "topic_suggestions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TopicSuggestion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** ADR-003: meaningful company identifier, no FK. */
    @Column(name = "company_name", nullable = false, length = 255)
    private String companyName;

    /** Username of the partner who submitted this suggestion. */
    @Column(name = "suggested_by", nullable = false, length = 100)
    private String suggestedBy;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    @Builder.Default
    private TopicStatus status = TopicStatus.PROPOSED;

    /** Organizer fills in when selecting a topic (e.g. "BATbern58"). */
    @Column(name = "planned_event", length = 100)
    private String plannedEvent;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
