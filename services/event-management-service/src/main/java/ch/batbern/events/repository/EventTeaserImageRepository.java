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

    @Query("SELECT MAX(e.displayOrder) FROM EventTeaserImage e WHERE e.eventCode = :eventCode")
    Optional<Integer> findMaxDisplayOrderByEventCode(@Param("eventCode") String eventCode);
}
