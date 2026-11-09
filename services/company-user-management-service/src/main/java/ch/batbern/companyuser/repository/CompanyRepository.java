package ch.batbern.companyuser.repository;

import ch.batbern.companyuser.domain.Company;
import ch.batbern.companyuser.specification.CompanySpecification;
import ch.batbern.shared.api.DeterministicPaging;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
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
     * Autocomplete search over the slug ({@code name}) and the display name, ranked and limited in
     * the DATABASE.
     *
     * <p>Users type the display name ("Infowell GmbH") while {@code name} holds the ADR-003 slug
     * ("infowellgmbh"), so both columns are searched. Every word must occur in one of them, folded
     * by {@code batbern_fold}. The former JPQL had no ORDER BY and the service cut it in Java, so
     * the exact company "Bundes" sat at position 12 of 13 and never reached the 10-row public
     * picker; now an exact match ranks first, then a prefix, then the rest, by display name and slug.
     *
     * @param query Search input; blank matches every company (ordered by display name)
     * @param limit Maximum number of rows, applied by the database
     * @return Matching companies, most relevant first
     */
    default List<Company> searchRanked(String query, int limit) {
        return findAll(CompanySpecification.matchesSearchRanked(query),
                DeterministicPaging.orderedByQuery(0, limit)).getContent();
    }

    /**
     * Check if company exists by name
     * AC3: Duplicate name validation
     *
     * @param name Company name
     * @return true if exists, false otherwise
     */
    boolean existsByName(String name);

    /**
     * Whether a company with this display name exists, ignoring case: the duplicate check of
     * {@code POST /companies}, whose key is derived from the name (owner 2026-11-09).
     */
    boolean existsByDisplayNameIgnoreCase(String displayName);

    /**
     * Find companies by verified status
     * AC6: Company verification filtering
     *
     * @param isVerified Verified status flag
     * @return List of companies matching verified status
     */
    List<Company> findByIsVerified(boolean isVerified);
}
