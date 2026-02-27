package ch.batbern.companyuser.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * JPA entity for the moderator presentation page settings.
 *
 * Story 10.8a: Moderator Presentation Page — Functional
 *
 * Single-row table pattern: id is always 1.
 * The service uses upsert semantics to ensure exactly one row exists.
 */
@Entity
@Table(name = "presentation_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PresentationSettings {

    /**
     * Always 1 — single-row table pattern.
     */
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private Integer id;

    @Column(name = "about_text", nullable = false, columnDefinition = "TEXT")
    private String aboutText;

    @Column(name = "partner_count", nullable = false)
    private int partnerCount;
}
