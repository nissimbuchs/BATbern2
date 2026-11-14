package ch.batbern.events.repository;

import ch.batbern.events.domain.EventTeaserImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for EventTeaserImage entities.
 * <p>
 * Story 10.22: Event Teaser Images
 */
@Repository
public interface EventTeaserImageRepository extends JpaRepository<EventTeaserImage, UUID> {

    List<EventTeaserImage> findByEventCodeOrderByDisplayOrderAsc(String eventCode);

    Optional<EventTeaserImage> findByIdAndEventCode(UUID id, String eventCode);

    long countByEventCode(String eventCode);

    // ── Global teaser images (event_code IS NULL) ────────────────────────────

    List<EventTeaserImage> findByEventCodeIsNullOrderByDisplayOrderAsc();

    Optional<EventTeaserImage> findByIdAndEventCodeIsNull(UUID id);

    long countByEventCodeIsNull();

    /**
     * Serializes adding teaser images to one scope (an event code, or {@code _global}) until the
     * transaction ends (Story 17.11c). A row lock cannot: an empty scope has no rows to lock, and
     * rows inserted by a concurrent transaction are not in the locked set. Returns 1.
     */
    @Query(value = "SELECT 1 FROM (SELECT pg_advisory_xact_lock("
            + "hashtextextended('event_teaser_images:' || :scope, 0))) l", nativeQuery = true)
    int lockScope(@Param("scope") String scope);
}
