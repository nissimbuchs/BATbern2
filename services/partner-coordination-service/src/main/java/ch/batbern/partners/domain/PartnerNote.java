package ch.batbern.partners.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * PartnerNote entity — organizer-internal notes about a partner company.
 *
 * Story 8.4: Partner Notes (AC1-5)
 *
 * ADR-003 Compliance:
 * - author_username stored as String (not UUID) — consistent with partner_meetings.created_by
 *
 * Database: partner_notes table (Flyway V7)
 * updated_at is managed by DB trigger update_updated_at_column().
 */
@Entity
@Table(name = "partner_notes")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PartnerNote {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "partner_id", nullable = false)
    private UUID partnerId;

    @Column(name = "title", nullable = false, length = 500)
    private String title;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "author_username", nullable = false, length = 100)
    private String authorUsername;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
