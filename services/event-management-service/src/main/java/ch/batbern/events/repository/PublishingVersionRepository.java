package ch.batbern.events.repository;

import ch.batbern.events.domain.PublishingVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Publishing Version entities
 * Story BAT-11 (5.7): Slot Assignment & Progressive Publishing
 * Task 3b: GREEN Phase - Publishing Engine Implementation
 *
 * Provides queries for:
 * - Version history by event
 * - Current version lookup
 * - Version rollback operations
 */
@Repository
public interface PublishingVersionRepository extends JpaRepository<PublishingVersion, UUID> {

    /**
     * Find all versions for an event, ordered by version number descending
     */
    List<PublishingVersion> findByEventIdOrderByVersionNumberDesc(UUID eventId);

    /**
     * Find current version for an event (where isCurrent = true)
     */
    Optional<PublishingVersion> findByEventIdAndIsCurrentTrue(UUID eventId);

    /**
     * Find specific version by event and version number
     */
    Optional<PublishingVersion> findByEventIdAndVersionNumber(UUID eventId, Integer versionNumber);

    /**
     * Get the highest version number for an event (for incrementing)
     */
    @Query("SELECT COALESCE(MAX(v.versionNumber), 0) FROM PublishingVersion v WHERE v.eventId = :eventId")
    Integer findMaxVersionNumberByEventId(@Param("eventId") UUID eventId);

    /**
     * Set isCurrent = false for all versions of an event (before marking a new current)
     */
    @Modifying
    @Query("UPDATE PublishingVersion v SET v.isCurrent = false WHERE v.eventId = :eventId")
    void markAllVersionsAsNotCurrent(@Param("eventId") UUID eventId);

    /**
     * Count total versions for an event
     */
    long countByEventId(UUID eventId);
}
