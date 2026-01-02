package ch.batbern.events.repository;

import ch.batbern.events.domain.PublishingConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Publishing Configuration entities
 * Story BAT-11 (5.7): Slot Assignment & Progressive Publishing
 * Task 3b: GREEN Phase - Publishing Engine Implementation
 *
 * Provides queries for:
 * - Per-event publishing configuration
 * - Auto-publish scheduling settings
 */
@Repository
public interface PublishingConfigRepository extends JpaRepository<PublishingConfig, UUID> {

    /**
     * Find publishing config for a specific event
     */
    Optional<PublishingConfig> findByEventId(UUID eventId);

    /**
     * Delete publishing config for a specific event
     */
    void deleteByEventId(UUID eventId);

    /**
     * Check if publishing config exists for an event
     */
    boolean existsByEventId(UUID eventId);
}
