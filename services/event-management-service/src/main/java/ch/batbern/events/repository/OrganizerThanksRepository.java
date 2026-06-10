package ch.batbern.events.repository;

import ch.batbern.events.domain.OrganizerThanks;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link OrganizerThanks} (Story 7.4).
 */
@Repository
public interface OrganizerThanksRepository extends JpaRepository<OrganizerThanks, UUID> {

    /** Logged-in dedupe lookup (AC4): the single existing row for this attendee + event, if any. */
    Optional<OrganizerThanks> findByEventIdAndThankedByUsername(UUID eventId, String thankedByUsername);

    /** Public aggregate count (AC6) — logged-in rows + anonymous claps. */
    long countByEventId(UUID eventId);

    /** Organizer-only notes view (AC6), newest first. */
    List<OrganizerThanks> findByEventIdOrderByCreatedAtDesc(UUID eventId);
}
