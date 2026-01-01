package ch.batbern.events.repository;

import ch.batbern.events.domain.SpeakerSlotPreference;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for SpeakerSlotPreference entity
 * Story BAT-11 (5.7): Slot Assignment & Progressive Publishing
 */
@Repository
public interface SpeakerSlotPreferenceRepository extends JpaRepository<SpeakerSlotPreference, UUID> {

    /**
     * Find preference for a specific speaker
     */
    Optional<SpeakerSlotPreference> findBySpeakerId(UUID speakerId);

    /**
     * Find preference for speaker in a specific event
     */
    Optional<SpeakerSlotPreference> findBySpeakerIdAndEventId(UUID speakerId, UUID eventId);

    /**
     * Find all preferences for an event
     */
    List<SpeakerSlotPreference> findByEventId(UUID eventId);
}
