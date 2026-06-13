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

    // ==================== Story 7.7: curated featured notes ====================

    /** Organizer feature-toggle lookup (Story 7.7): the note scoped to its event, or empty. */
    Optional<OrganizerThanks> findByIdAndEventId(UUID id, UUID eventId);

    /**
     * Public featured-marquee query (Story 7.7, AC2/AC3): up to {@code limit} RANDOM featured,
     * logged-in thank-yous across ALL events, fully enriched with the author's name + company logo.
     *
     * <p>Same intentional, read-only cross-service join as the Q&A author-portrait query (see
     * {@link ThanksAuthorProjection}). The {@code INNER JOIN user_profiles} drops notes whose author
     * was deleted (AC3) so the random {@code LIMIT} fills with live authors; {@code LEFT JOIN
     * companies} keeps a note even when its company row is missing.
     *
     * <p>Per-company cap (Resolved Decision 2026-06-13): at most ONE card per company per fetch so a
     * single firm can't dominate the rotation. Implemented as {@code DISTINCT ON} a per-company key
     * (the company slug, falling back to the username so company-less authors are NOT collapsed
     * together), picking a random row within each company group, then a random outer {@code LIMIT}.
     * {@code ORDER BY random()} is fine at BATbern scale — the partial index
     * {@code ix_organizer_thanks_featured} keeps the featured candidate set tiny.
     */
    @Query(value = "SELECT * FROM ("
            + "  SELECT DISTINCT ON (COALESCE(up.company_id, ot.thanked_by_username)) "
            + "    ot.note AS note, "
            + "    e.event_code AS eventCode, "
            + "    up.first_name AS firstName, "
            + "    up.last_name AS lastName, "
            + "    up.settings_show_company AS showCompany, "
            + "    COALESCE(c.display_name, c.name, up.company_id) AS companyDisplayName, "
            + "    c.logo_url AS companyLogoUrl "
            + "  FROM organizer_thanks ot "
            + "  JOIN events e ON e.id = ot.event_id "
            + "  JOIN user_profiles up ON up.username = ot.thanked_by_username "
            + "  LEFT JOIN companies c ON c.name = up.company_id "
            + "  WHERE ot.featured_at IS NOT NULL AND ot.thanked_by_username IS NOT NULL "
            + "  ORDER BY COALESCE(up.company_id, ot.thanked_by_username), random()"
            + ") one_per_company "
            + "ORDER BY random() "
            + "LIMIT :limit",
           nativeQuery = true)
    List<FeaturedThanksProjection> findFeaturedRandom(@Param("limit") int limit);

    /**
     * Batch-load thank-you author portraits keyed by username (Story 7.7) for the organizer
     * Appreciation panel. Clone of the Q&A author-portrait join — see {@link ThanksAuthorProjection}.
     */
    @Query(value = "SELECT up.username AS username, "
            + "up.first_name AS firstName, "
            + "up.last_name AS lastName, "
            + "up.settings_show_company AS showCompany, "
            + "COALESCE(c.display_name, c.name, up.company_id) AS companyDisplayName, "
            + "c.logo_url AS companyLogoUrl "
            + "FROM user_profiles up "
            + "LEFT JOIN companies c ON c.name = up.company_id "
            + "WHERE up.username IN :usernames",
           nativeQuery = true)
    List<ThanksAuthorProjection> findThanksAuthorPortraitsByUsernames(
            @Param("usernames") java.util.Collection<String> usernames);
}
