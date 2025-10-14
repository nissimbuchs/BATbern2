package ch.batbern.events.repository;

import ch.batbern.events.domain.Registration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for Registration entities
 * Story 1.15a.1: Events API Consolidation - AC11-12
 */
@Repository
public interface RegistrationRepository extends JpaRepository<Registration, UUID>, JpaSpecificationExecutor<Registration> {

    /**
     * Find all registrations for a specific event
     */
    List<Registration> findByEventId(UUID eventId);

    /**
     * Find all registrations for a specific event and status
     */
    List<Registration> findByEventIdAndStatus(UUID eventId, String status);

    /**
     * Delete all registrations for a specific event
     */
    void deleteByEventId(UUID eventId);
}
