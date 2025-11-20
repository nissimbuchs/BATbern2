package ch.batbern.migration.service;

import ch.batbern.migration.model.target.EntityIdMapping;
import ch.batbern.migration.repository.EntityIdMappingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Entity ID Mapping Service
 *
 * Manages mapping between legacy IDs and new UUIDs for foreign key resolution.
 * Supports idempotent migration (check existence before create).
 *
 * Story: 3.2.1 - Migration Tool Implementation
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EntityIdMappingService {

    private final EntityIdMappingRepository repository;

    /**
     * Store mapping from legacy ID to new UUID
     * Used by writers after successful entity creation
     *
     * @param entityType Entity type (Company, User, Event, Session, Speaker)
     * @param legacyId Original ID from legacy system
     * @param newId New UUID from target system
     */
    @Transactional
    public void storeMapping(String entityType, String legacyId, UUID newId) {
        // Check if mapping already exists (idempotency)
        if (repository.existsByEntityTypeAndLegacyId(entityType, legacyId)) {
            log.warn("Mapping already exists for {} with legacy ID: {}. Skipping.", entityType, legacyId);
            return;
        }

        EntityIdMapping mapping = new EntityIdMapping(entityType, legacyId, newId);
        repository.save(mapping);
        log.debug("Stored mapping: {} {} → UUID {}", entityType, legacyId, newId);
    }

    /**
     * Lookup new UUID by legacy ID
     * Used by processors for foreign key resolution
     *
     * @param entityType Entity type to lookup
     * @param legacyId Legacy ID to find
     * @return New UUID
     * @throws IllegalStateException if mapping not found
     */
    @Transactional(readOnly = true)
    public UUID getNewId(String entityType, String legacyId) {
        return repository.findByEntityTypeAndLegacyId(entityType, legacyId)
            .map(EntityIdMapping::getNewId)
            .orElseThrow(() -> new IllegalStateException(
                String.format("No mapping found for %s with legacy ID: %s", entityType, legacyId)
            ));
    }

    /**
     * Check if entity has already been migrated
     * Used for idempotency in readers/processors
     */
    @Transactional(readOnly = true)
    public boolean isMigrated(String entityType, String legacyId) {
        return repository.existsByEntityTypeAndLegacyId(entityType, legacyId);
    }

    /**
     * Get count of migrated entities by type
     * Used for migration validation
     */
    @Transactional(readOnly = true)
    public long getCount(String entityType) {
        return repository.countByEntityType(entityType);
    }
}
