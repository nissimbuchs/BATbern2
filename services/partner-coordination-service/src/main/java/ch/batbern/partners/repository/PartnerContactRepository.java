package ch.batbern.partners.repository;

import ch.batbern.partners.domain.PartnerContact;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
