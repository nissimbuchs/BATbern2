package ch.batbern.partners.repository;

import ch.batbern.partners.domain.PartnerContact;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for PartnerContact entities.
 */
@Repository
public interface PartnerContactRepository extends JpaRepository<PartnerContact, UUID> {

    /**
     * Find all contacts for a partner
     */
    List<PartnerContact> findByPartnerId(UUID partnerId);

    /**
     * Find a specific contact by partner and username
     */
    Optional<PartnerContact> findByPartnerIdAndUsername(UUID partnerId, String username);

    /**
     * Check if contact exists for partner
     */
    boolean existsByPartnerIdAndUsername(UUID partnerId, String username);

    /**
     * Count primary contacts for a partner
     */
    long countByPartnerIdAndIsPrimaryTrue(UUID partnerId);

    /**
     * Find all contacts associated with a given username.
     * Story 8.1: Used by PartnerSecurityService to resolve which company a PARTNER user belongs to.
     */
    List<PartnerContact> findByUsername(String username);

    /**
     * Check in a single JOIN query whether a username is a contact of the given company.
     * Story 8.1 (review fix H1): replaces N+1 pattern in PartnerSecurityService.isCurrentUserCompany().
     *
     * @param username    authenticated user's username
     * @param companyName ADR-003 meaningful company identifier
     * @return count of matching contacts (0 = not a contact, >0 = is a contact)
     */
    @Query("SELECT COUNT(c) FROM PartnerContact c, Partner p "
            + "WHERE c.partnerId = p.id AND c.username = :username AND p.companyName = :companyName")
    long countByUsernameAndCompanyName(@Param("username") String username,
                                       @Param("companyName") String companyName);

    /**
     * Resolve a partner user's company name from their username.
     * Story 8.2: used by TopicService to extract companyName from JWT principal.
     *
     * @param username authenticated username (custom:username claim or @WithMockUser name)
     * @return company name (ADR-003 identifier) for the given username, or empty if not a partner contact
     */
    @Query("SELECT p.companyName FROM PartnerContact c, Partner p "
            + "WHERE c.partnerId = p.id AND c.username = :username")
    java.util.Optional<String> findCompanyNameByUsername(@Param("username") String username);
}
