package ch.batbern.events.watch;

import ch.batbern.events.watch.domain.SpeakerArrival;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for speaker arrival tracking.
 * W2.4: FR38, FR39 — idempotent arrival persistence.
 */
@Repository
public interface SpeakerArrivalRepository extends JpaRepository<SpeakerArrival, UUID> {

    List<SpeakerArrival> findByEventCode(String eventCode);

    boolean existsByEventCodeAndSpeakerUsername(String eventCode, String speakerUsername);

    long countByEventCode(String eventCode);
}
