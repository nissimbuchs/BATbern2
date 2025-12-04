package ch.batbern.events.repository;

import ch.batbern.events.dto.generated.EventType;
import ch.batbern.events.entity.EventTypeConfiguration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for EventTypeConfiguration entity (Story 5.1).
 *
 * Provides CRUD operations for event type configurations.
 * Cached at service layer (Caffeine cache).
 */
@Repository
public interface EventTypeRepository extends JpaRepository<EventTypeConfiguration, UUID> {

    /**
     * Find event type configuration by type enum.
     *
     * @param type EventType enum (FULL_DAY, AFTERNOON, EVENING)
     * @return Optional containing configuration if found
     */
    Optional<EventTypeConfiguration> findByType(EventType type);

    /**
     * Check if event type configuration exists.
     *
     * @param type EventType enum
     * @return true if configuration exists
     */
    boolean existsByType(EventType type);
}
