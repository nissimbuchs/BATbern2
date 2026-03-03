package ch.batbern.events.repository;

import ch.batbern.events.domain.EventTeaserImage;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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

    /**
     * SELECT ... FOR UPDATE on all images for an event.
     * Used by confirmUpload to serialize concurrent uploads and prevent over-limit races
     * and duplicate displayOrder values (M1 race condition fix).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM EventTeaserImage e WHERE e.eventCode = :eventCode ORDER BY e.displayOrder ASC")
    List<EventTeaserImage> findByEventCodeForUpdate(@Param("eventCode") String eventCode);
}
