package ch.batbern.events.repository;

import ch.batbern.events.domain.Event;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Native-query repository for the EMS test-fixture cleanup endpoint.
 *
 * <p>Bypasses the regular {@code EventRepository} / {@code SessionRepository} / {@code TopicRepository}
 * so that:
 * <ul>
 *   <li>The cleanup-specific {@code @PreAuthorize}-gated controller is the only entry point;
 *       these deletion methods aren't reachable from normal application code paths.</li>
 *   <li>The DELETE statements use {@code LIKE :pattern} for predictable, set-based deletion
 *       with one SQL round-trip per table — no per-row entity hydration or workflow-state
 *       guard checks (events normally enforce workflow-state transitions on delete).</li>
 *   <li>Cascade-delete via FK constraints (ON DELETE CASCADE on event_tasks.event_id,
 *       speaker_pool.event_id, registrations.event_id, sessions.event_id, etc.) handles
 *       all dependent rows in one statement.</li>
 * </ul>
 *
 * <p>Cascade chains exercised by these deletes (declared across V2, V14, V16, V22, V23,
 * V28, V29, V41, V43, V53, V54, V79, V80):
 * <ul>
 *   <li>DELETE events → cascades to event_tasks, speaker_pool (and its dependents:
 *       speaker_outreach_history, speaker_status_history, speaker_invitation_tokens,
 *       speaker_content_history, speaker_reminder_log), event_photos, event_teaser_images,
 *       registrations, sessions (and via sessions: session_users, session_materials)</li>
 *   <li>DELETE sessions → cascades to session_users, session_materials, session-speaker
 *       links</li>
 *   <li>DELETE topics → cascades to topic_usage_history. (events.topic_code is a soft
 *       string reference since V27 — no FK, no constraint violation if a topic with
 *       referenced topic_code is deleted; that case shouldn't arise in Bruno cleanup
 *       since both prefixes match the same Bruno run.)</li>
 * </ul>
 *
 * <p>Bound to {@code Event} as the parameterized type purely so Spring Data picks it up as a
 * repository bean; none of the methods here use the JpaRepository<Event, UUID> interface.
 */
@Repository
public interface TestFixtureCleanupRepository extends JpaRepository<Event, UUID> {

    /**
     * Delete events whose {@code event_code} starts with the prefix.
     * Cascades through event_tasks, speaker_pool, registrations, sessions, event_photos,
     * event_teaser_images via FK ON DELETE CASCADE.
     *
     * @param eventCodePattern {@code LIKE} pattern for the event_code column
     * @return number of event rows deleted (cascade dependents not counted)
     */
    @Modifying
    @Query(
            value = "DELETE FROM events WHERE event_code LIKE :eventCodePattern",
            nativeQuery = true
    )
    int deleteEventsByEventCodeLike(@Param("eventCodePattern") String eventCodePattern);

    /**
     * Delete sessions whose {@code session_slug} starts with the prefix.
     * Cascades through session_users, session_materials via FK ON DELETE CASCADE.
     *
     * @param sessionSlugPattern {@code LIKE} pattern for the session_slug column
     * @return number of session rows deleted (cascade dependents not counted)
     */
    @Modifying
    @Query(
            value = "DELETE FROM sessions WHERE session_slug LIKE :sessionSlugPattern",
            nativeQuery = true
    )
    int deleteSessionsBySessionSlugLike(@Param("sessionSlugPattern") String sessionSlugPattern);

    /**
     * Delete topics whose {@code topic_code} starts with the prefix.
     * Cascades through topic_usage_history via FK ON DELETE CASCADE.
     *
     * @param topicCodePattern {@code LIKE} pattern for the topic_code column
     * @return number of topic rows deleted (cascade dependents not counted)
     */
    @Modifying
    @Query(
            value = "DELETE FROM topics WHERE topic_code LIKE :topicCodePattern",
            nativeQuery = true
    )
    int deleteTopicsByTopicCodeLike(@Param("topicCodePattern") String topicCodePattern);
}
