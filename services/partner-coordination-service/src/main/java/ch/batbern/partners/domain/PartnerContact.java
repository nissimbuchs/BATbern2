package ch.batbern.partners.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Partner Contact entity.
 *
 * Represents a contact person associated with a partner organization.
 * Per ADR-003: stores username (String), NOT userId (UUID).
 * User data is enriched via HTTP calls to User Service.
 */
@Entity
@Table(name = "partner_contacts",
        uniqueConstraints = @UniqueConstraint(columnNames = {"partner_id", "username"}))
@Getter
@Setter
public class PartnerContact {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    /**
     * Partner ID (FK to partners table within this service)
     */
    @Column(name = "partner_id", nullable = false)
    private UUID partnerId;

    /**
     * Username (ADR-003: meaningful ID, NOT userId UUID)
     *
     * This is the identifier used to fetch User data via HTTP call to User Service.
     * NO foreign key to users table (microservices boundary).
     */
    @Column(name = "username", nullable = false, length = 100)
    private String username;

    /**
     * Contact role within the partner organization
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "contact_role", nullable = false, length = 50)
    private ContactRole contactRole;

    /**
     * Whether this is a primary contact for the partner
     *
     * Business rule: At least one primary contact required per partner
     */
    @Column(name = "is_primary", nullable = false)
    private boolean isPrimary;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
