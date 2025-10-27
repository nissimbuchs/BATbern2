package ch.batbern.companyuser.repository;

import ch.batbern.companyuser.domain.Logo;
import ch.batbern.companyuser.domain.LogoStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Logo aggregate root
 * Story 1.16.3: Generic File Upload Service
 * ADR-002: Generic File Upload Service Architecture
 *
 * Provides queries for logo lifecycle management and cleanup operations
 */
@Repository
public interface LogoRepository extends JpaRepository<Logo, UUID> {

    /**
     * Find logo by upload ID
     * Used for confirmation and association operations
     *
     * @param uploadId Public upload identifier
     * @return Optional logo
     */
    Optional<Logo> findByUploadId(String uploadId);

    /**
     * Find logos by status and expiration time (for cleanup job)
     * Finds logos that are in a given status and expired before the specified time
     *
     * @param status     Logo status (PENDING or CONFIRMED)
     * @param expiresAt Expiration threshold
     * @return List of expired logos to be cleaned up
     */
    List<Logo> findByStatusAndExpiresAtBefore(LogoStatus status, Instant expiresAt);

    /**
     * Find logos associated with a specific entity
     * Used for entity-specific queries (e.g., all logos for a company)
     *
     * @param entityType Type of entity (COMPANY, USER, EVENT, etc.)
     * @param entityId   Entity identifier
     * @return List of associated logos
     */
    List<Logo> findByAssociatedEntityTypeAndAssociatedEntityId(String entityType, String entityId);

    /**
     * Find logos by status
     * Used for monitoring and analytics
     *
     * @param status Logo status
     * @return List of logos with the specified status
     */
    List<Logo> findByStatus(LogoStatus status);

    /**
     * Count logos by status
     * Used for monitoring and metrics
     *
     * @param status Logo status
     * @return Count of logos with the specified status
     */
    long countByStatus(LogoStatus status);

    /**
     * Check if upload ID exists
     * Used for duplicate upload prevention
     *
     * @param uploadId Upload identifier
     * @return true if exists, false otherwise
     */
    boolean existsByUploadId(String uploadId);

    /**
     * Delete logos by status and expiration time
     * Used by cleanup job for batch deletion
     *
     * @param status    Logo status
     * @param expiresAt Expiration threshold
     * @return Number of deleted logos
     */
    long deleteByStatusAndExpiresAtBefore(LogoStatus status, Instant expiresAt);
}
