package ch.batbern.events.repository;

import ch.batbern.events.domain.Speaker;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Speaker entity - Story 6.0.
 *
 * Provides CRUD operations and custom queries for global speaker profiles.
 * Extends JpaSpecificationExecutor for advanced filtering support (AC4).
 */
@Repository
public interface SpeakerRepository extends JpaRepository<Speaker, UUID>, JpaSpecificationExecutor<Speaker> {

    /**
     * Find speaker by username (ADR-003: meaningful identifier).
     *
     * @param username the username to search for
     * @return Optional containing the speaker if found
     */
    Optional<Speaker> findByUsername(String username);

    /**
     * Check if a speaker exists with the given username.
     *
     * @param username the username to check
     * @return true if speaker exists, false otherwise
     */
    boolean existsByUsername(String username);

    /**
     * Find speaker by username, excluding soft-deleted records.
     *
     * @param username the username to search for
     * @return Optional containing the active speaker if found
     */
    Optional<Speaker> findByUsernameAndDeletedAtIsNull(String username);
}
