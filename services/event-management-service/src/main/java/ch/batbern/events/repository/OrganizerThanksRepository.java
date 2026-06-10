package ch.batbern.events.repository;

import ch.batbern.events.domain.OrganizerThanks;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    /**
     * Race-safe logged-in upsert (AC4): atomically insert a thank-you or update the note if one
     * already exists for (event, username). Uses the partial unique index
     * {@code ux_organizer_thanks_user} as the conflict target — a check-then-insert in the service
     * would otherwise let two concurrent submissions from the same user collide on the index and
     * surface a 500 instead of cleanly deduping. Defaults fill {@code id}/{@code created_at}.
     */
    @Modifying
    @Query(value = """
            INSERT INTO organizer_thanks (event_id, thanked_by_username, note)
            VALUES (:eventId, :username, :note)
            ON CONFLICT (event_id, thanked_by_username) WHERE thanked_by_username IS NOT NULL
            DO UPDATE SET note = EXCLUDED.note
            """, nativeQuery = true)
    void upsertLoggedInThanks(@Param("eventId") UUID eventId,
                             @Param("username") String username,
                             @Param("note") String note);

    /** Public aggregate count (AC6) — logged-in rows + anonymous claps. */
    long countByEventId(UUID eventId);

    /** Organizer-only notes view (AC6), newest first. */
    List<OrganizerThanks> findByEventIdOrderByCreatedAtDesc(UUID eventId);
}
