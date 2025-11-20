package ch.batbern.migration.repository;

import ch.batbern.migration.model.target.EntityIdMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Entity ID Mapping Repository
 *
 * Provides fast lookups for legacy ID → UUID mappings during migration.
 * Story: 3.2.1 - Migration Tool Implementation
 */
@Repository
public interface EntityIdMappingRepository extends JpaRepository<EntityIdMapping, Long> {

    /**
     * Find mapping by entity type and legacy ID
     * Used for foreign key resolution (e.g., find Event UUID by BAT number)
     */
    Optional<EntityIdMapping> findByEntityTypeAndLegacyId(String entityType, String legacyId);

    /**
     * Count mappings by entity type
     * Used for migration validation (e.g., verify 65 companies created)
     */
    long countByEntityType(String entityType);

    /**
     * Check if mapping exists
     * Used for idempotency (skip if already migrated)
     */
    boolean existsByEntityTypeAndLegacyId(String entityType, String legacyId);

    /**
     * Find mapping by new UUID
     * Used for rollback procedures
     */
    Optional<EntityIdMapping> findByNewId(UUID newId);
}
