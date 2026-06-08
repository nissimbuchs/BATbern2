package ch.batbern.companyuser.repository;

import ch.batbern.companyuser.domain.Company;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Company aggregate root
 * AC1: Company entity persistence
 * AC2: Custom query methods for company search
 * AC14: JpaSpecificationExecutor for advanced query patterns
 */
@Repository
public interface CompanyRepository extends JpaRepository<Company, UUID>, JpaSpecificationExecutor<Company> {

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
     * Find companies whose meaningful name (slug) OR human-readable display name
     * contains the query, case-insensitively.
     *
     * <p>Autocomplete must match what the user actually typed. Users type the
     * <em>display name</em> ("Infowell GmbH"), but the {@code name} column holds
     * the ADR-003 slug ("infowellgmbh") — so matching {@code name} alone returns
     * nothing for a spaced/cased display name and pushes the user into creating a
     * duplicate company. Matching both columns surfaces the existing company.
     *
     * @param query Partial company name or display name (case-insensitive)
     * @return List of matching companies
     */
    @Query("""
            SELECT c FROM Company c
            WHERE LOWER(c.name) LIKE LOWER(CONCAT('%', :query, '%'))
               OR LOWER(c.displayName) LIKE LOWER(CONCAT('%', :query, '%'))
            """)
    List<Company> searchByNameOrDisplayName(@Param("query") String query);

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
