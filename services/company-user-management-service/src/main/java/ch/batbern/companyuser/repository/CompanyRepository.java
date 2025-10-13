package ch.batbern.companyuser.repository;

import ch.batbern.companyuser.domain.Company;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Company aggregate root
 * AC1: Company entity persistence
 * AC2: Custom query methods for company search
 */
@Repository
public interface CompanyRepository extends JpaRepository<Company, UUID> {

    /**
     * Find company by exact name
     * AC3: Enforce name uniqueness validation
     *
     * @param name Company name
     * @return Optional company
     */
    Optional<Company> findByName(String name);

    /**
     * Find companies by name containing (case-insensitive)
     * AC5: Company search functionality with autocomplete
     *
     * @param name Partial company name
     * @return List of matching companies
     */
    List<Company> findByNameContainingIgnoreCase(String name);

    /**
     * Find company by Swiss UID
     * AC1: Swiss UID validation and company lookup
     *
     * @param swissUID Swiss company UID
     * @return Optional company
     */
    Optional<Company> findBySwissUID(String swissUID);

    /**
     * Check if company exists by name
     * AC3: Duplicate name validation
     *
     * @param name Company name
     * @return true if exists, false otherwise
     */
    boolean existsByName(String name);

    /**
     * Find companies by verified status
     * AC6: Company verification filtering
     *
     * @param isVerified Verified status flag
     * @return List of companies matching verified status
     */
    List<Company> findByIsVerified(boolean isVerified);
}
