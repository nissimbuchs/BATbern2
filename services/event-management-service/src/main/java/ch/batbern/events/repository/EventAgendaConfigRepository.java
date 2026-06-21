package ch.batbern.events.repository;

import ch.batbern.events.entity.EventAgendaConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for the per-event agenda config override (Story 15.2).
 * One row per event (unique event_id); absent → caller falls back to the shared template.
 */
@Repository
public interface EventAgendaConfigRepository extends JpaRepository<EventAgendaConfig, UUID> {

    /**
     * Find the per-event override for an event, if one has been created (copy-on-edit).
     *
     * @param eventId within-service UUID of the event
     * @return the override row if present
     */
    Optional<EventAgendaConfig> findByEventId(UUID eventId);
}
