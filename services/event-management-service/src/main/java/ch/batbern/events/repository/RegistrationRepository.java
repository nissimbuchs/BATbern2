package ch.batbern.events.repository;

import ch.batbern.events.domain.Registration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for Registration entities
 * Story 1.15a.1: Events API Consolidation - AC11-12
 */
@Repository
public interface RegistrationRepository extends JpaRepository<Registration, String>, JpaSpecificationExecutor<Registration> {

    /**
     * Find all registrations for a specific event
     */
    List<Registration> findByEventId(String eventId);

    /**
     * Find all registrations for a specific event and status
     */
    List<Registration> findByEventIdAndStatus(String eventId, String status);

    /**
     * Delete all registrations for a specific event
     */
    void deleteByEventId(String eventId);
}
