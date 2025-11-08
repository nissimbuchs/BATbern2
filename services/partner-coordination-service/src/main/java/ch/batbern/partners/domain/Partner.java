package ch.batbern.partners.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Partner entity representing a partner company relationship.
 *
 * ADR-003 Compliance:
 * - Stores companyName (meaningful ID), NOT companyId UUID
 * - Company data retrieved via HTTP from Company Service
 * - NO foreign key to companies table (microservices boundary)
 *
 * Database: partners table (via Flyway V2__create_partner_coordination_schema.sql)
 */
@Entity
@Table(name = "partners")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Partner {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    /**
     * Company name (meaningful ID per ADR-003).
     * References Company Service via HTTP (NOT database FK).
     */
    @Column(name = "company_name", nullable = false, unique = true, length = 12)
    private String companyName;

    @Enumerated(EnumType.STRING)
    @Column(name = "partnership_level", nullable = false, length = 50)
    private PartnershipLevel partnershipLevel;

    @Column(name = "partnership_start_date", nullable = false)
    private LocalDate partnershipStartDate;

    @Column(name = "partnership_end_date")
    private LocalDate partnershipEndDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Calculate if partnership is currently active.
     *
     * A partnership is active if:
     * - Current date is on or after partnership start date
     * - AND (no end date OR current date is before end date)
     *
     * @return true if partnership is active, false otherwise
     */
    public boolean isActive() {
        LocalDate now = LocalDate.now();

        // Partnership hasn't started yet
        if (partnershipStartDate.isAfter(now)) {
            return false;
        }

        // Partnership has started, check end date
        if (partnershipEndDate == null) {
            // Open-ended partnership
            return true;
        }

        // Partnership has end date, check if it has passed
        return !partnershipEndDate.isBefore(now);
    }

    /**
     * Validate that partnership level is provided.
     *
     * @throws IllegalArgumentException if partnership level is null
     */
    public void validatePartnershipLevel() {
        if (partnershipLevel == null) {
            throw new IllegalArgumentException("Partnership level is required");
        }
    }

    /**
     * Validate partnership dates.
     *
     * @throws IllegalArgumentException if end date is before start date
     */
    public void validateDates() {
        if (partnershipEndDate != null && partnershipEndDate.isBefore(partnershipStartDate)) {
            throw new IllegalArgumentException(
                    "Partnership end date cannot be before start date"
            );
        }
    }

    /**
     * Validate company name (ADR-003 requirement).
     *
     * @throws IllegalArgumentException if company name is null or exceeds max length
     */
    public void validateCompanyName() {
        if (companyName == null || companyName.trim().isEmpty()) {
            throw new IllegalArgumentException("Company name is required");
        }
        if (companyName.length() > 12) {
            throw new IllegalArgumentException(
                    "Company name must not exceed 12 characters"
            );
        }
    }

    /**
     * Pre-persist lifecycle hook.
     * Sets timestamps and validates business rules.
     */
    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
        validateEntity();
    }

    /**
     * Pre-update lifecycle hook.
     * Updates timestamp and validates business rules.
     */
    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
        validateEntity();
    }

    /**
     * Validate all business rules.
     */
    private void validateEntity() {
        validateCompanyName();
        validatePartnershipLevel();
        validateDates();
    }
}
