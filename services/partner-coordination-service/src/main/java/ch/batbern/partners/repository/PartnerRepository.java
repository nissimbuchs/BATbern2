package ch.batbern.partners.repository;

import ch.batbern.partners.domain.Partner;
import ch.batbern.partners.domain.PartnershipLevel;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Partner entities.
 *
 * Provides standard CRUD operations plus:
 * - findByCompanyName: Lookup by meaningful ID (ADR-003)
 * - the list filters as {@link Specification}s, combined and paged in SQL
 *
 * Database: partners table
 */
@Repository
public interface PartnerRepository extends JpaRepository<Partner, UUID>, JpaSpecificationExecutor<Partner> {

    /**
     * Find partner by company name (meaningful ID per ADR-003).
     *
     * @param companyName Company name (unique identifier)
     * @return Optional containing Partner if found, empty otherwise
     */
    Optional<Partner> findByCompanyName(String companyName);

    /** Partners of one tier; {@code null} is unrestricted. */
    static Specification<Partner> hasLevel(PartnershipLevel level) {
        if (level == null) {
            return Specification.unrestricted();
        }
        return (root, query, cb) -> cb.equal(root.get("partnershipLevel"), level);
    }

    /**
     * Active on {@code today} (start &lt;= today and no end or end &gt;= today), or the opposite for
     * {@code false}; {@code null} is unrestricted.
     */
    static Specification<Partner> isActiveOn(Boolean active, LocalDate today) {
        if (active == null) {
            return Specification.unrestricted();
        }
        Specification<Partner> activeSpec = (root, query, cb) -> cb.and(
                cb.lessThanOrEqualTo(root.get("partnershipStartDate"), today),
                cb.or(cb.isNull(root.get("partnershipEndDate")),
                        cb.greaterThanOrEqualTo(root.get("partnershipEndDate"), today)));
        Specification<Partner> inactiveSpec = (root, query, cb) -> cb.or(
                cb.greaterThan(root.get("partnershipStartDate"), today),
                cb.and(cb.isNotNull(root.get("partnershipEndDate")),
                        cb.lessThan(root.get("partnershipEndDate"), today)));
        return active ? activeSpec : inactiveSpec;
    }

    /**
     * Partners whose company slug is one of {@code companyNames} (resolved by CUMS from
     * the search input); an empty collection matches nothing.
     */
    static Specification<Partner> companyNameIn(Collection<String> companyNames) {
        if (companyNames.isEmpty()) {
            return (root, query, cb) -> cb.disjunction();
        }
        return (root, query, cb) -> root.get("companyName").in(companyNames);
    }
}
