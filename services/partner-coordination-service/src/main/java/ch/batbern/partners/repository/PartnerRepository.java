package ch.batbern.partners.repository;

import ch.batbern.partners.domain.Partner;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Partner entities.
 *
 * Provides standard CRUD operations plus custom queries:
 * - findByCompanyName: Lookup by meaningful ID (ADR-003)
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
}
