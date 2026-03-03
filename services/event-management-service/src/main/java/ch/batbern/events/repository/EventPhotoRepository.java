package ch.batbern.events.repository;

import ch.batbern.events.domain.EventPhoto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for EventPhoto entities.
 * <p>
 * Story 10.21: Event Photos Gallery
 */
@Repository
public interface EventPhotoRepository extends JpaRepository<EventPhoto, UUID> {

    List<EventPhoto> findByEventCodeOrderBySortOrderAscUploadedAtAsc(String eventCode);

    Optional<EventPhoto> findByIdAndEventCode(UUID id, String eventCode);

    List<EventPhoto> findByEventCodeIn(List<String> eventCodes);
}
