package ch.batbern.partners.repository;

import ch.batbern.partners.domain.Partner;
import ch.batbern.partners.domain.PartnershipLevel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Partner entities.
 *
 * Provides standard CRUD operations plus custom queries:
 * - findByCompanyName: Lookup by meaningful ID (ADR-003)
 * - findByPartnershipLevel: Filter by partnership tier
 * - findActivePartners / findInactivePartners: Filter by computed active status
 *
 * Database: partners table
 */
@Repository
public interface PartnerRepository extends JpaRepository<Partner, UUID> {

    /**
     * Find partner by company name (meaningful ID per ADR-003).
     *
     * @param companyName Company name (unique identifier)
     * @return Optional containing Partner if found, empty otherwise
     */
    Optional<Partner> findByCompanyName(String companyName);

    /**
     * Find partners by partnership level.
     *
     * @param partnershipLevel Partnership tier (BRONZE, SILVER, GOLD, PLATINUM, STRATEGIC)
     * @return List of partners with specified partnership level
     */
    List<Partner> findByPartnershipLevel(PartnershipLevel partnershipLevel);

    /**
     * Find active partners (partnership start date <= today AND (end date IS NULL OR end date >= today)).
     *
     * @return List of currently active partners
     */
    @Query("SELECT p FROM Partner p WHERE p.partnershipStartDate <= :today "
            + "AND (p.partnershipEndDate IS NULL OR p.partnershipEndDate >= :today)")
    List<Partner> findActivePartners(@Param("today") LocalDate today);

    /**
     * Find inactive partners (partnership start date > today OR end date < today).
     *
     * @return List of inactive partners
     */
    @Query("SELECT p FROM Partner p WHERE p.partnershipStartDate > :today "
            + "OR (p.partnershipEndDate IS NOT NULL AND p.partnershipEndDate < :today)")
    List<Partner> findInactivePartners(@Param("today") LocalDate today);
}
