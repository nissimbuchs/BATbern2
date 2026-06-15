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
     * Force-delete events whose {@code event_number} is at or above the reserved test
     * threshold (see {@code TestFixtureCleanupService.TEST_EVENT_NUMBER_THRESHOLD}). Reaches
     * server-coded ({@code BATbern{event_number}}) test events that the {@code event_code}
     * prefix sweep cannot, and — being a native DELETE — bypasses BOTH the workflow-state
     * machine AND the real-attendee delete-guard. Same FK cascade chain as
     * {@link #deleteEventsByEventCodeLike(String)} (event_tasks, speaker_pool + dependents,
     * registrations, sessions + dependents, event_photos, event_teaser_images).
     *
     * @param threshold inclusive lower bound for {@code event_number}
     * @return number of event rows deleted (cascade dependents not counted)
     */
    @Modifying
    @Query(
            value = "DELETE FROM events WHERE event_number >= :threshold",
            nativeQuery = true
    )
    int deleteEventsByEventNumberGte(@Param("threshold") int threshold);

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

    /**
     * Delete test-generated notifications. These are side effects of entity/state changes
     * (workflow transitions, publishes, registrations) and have no FK to events (ADR-003 soft
     * string reference), so they are NOT reached by the events cascade and need their own sweep.
     *
     * <p>A notification is a test artifact when ANY of:
     * <ul>
     *   <li>{@code event_code} starts with the {@code BRUNO-TEST-} sentinel; or</li>
     *   <li>{@code event_code} is a server-generated {@code BATbern{N}} whose number is at or
     *       above the reserved test threshold (real BATbern events sit far below it); or</li>
     *   <li>{@code recipient_username} starts with the {@code bruno.test.} sentinel; or</li>
     *   <li>{@code subject} contains the {@code BRUNO-TEST-} marker; or</li>
     *   <li>{@code body} contains the {@code BRUNO-TEST-} marker.</li>
     * </ul>
     * The subject/body markers catch test notifications that carry a NULL {@code event_code}
     * and a real-looking recipient (e.g. a workflow notification stamped with the run's
     * {@code BRUNO-TEST-X} sentinel in its rendered text) — rows the event_code / recipient
     * discriminators alone would miss.
     *
     * <p>Real organizers' in-app notifications (real event_code below the threshold, or a real
     * recipient with a NULL event_code and no marker in subject/body) are left untouched.
     * PostgreSQL-specific regex/substring is used deliberately — these run against the real
     * PostgreSQL database. The {@code BATbern[0-9]{1,9}} regex is bounded to at most 9 digits so
     * the subsequent {@code CAST(... AS INTEGER)} can never overflow a 32-bit INTEGER on a
     * pathological 10+-digit tail (which would otherwise raise a SQL error and 500 the whole
     * sweep); such a row simply fails the numeric branch and is left untouched.
     *
     * @param eventCodePattern {@code LIKE} pattern for the {@code BRUNO-TEST-} sentinel
     *        (e.g. {@code BRUNO-TEST-%})
     * @param numberThreshold inclusive lower bound for the {@code BATbern{N}} numeric tail
     * @param recipientPattern {@code LIKE} pattern for the {@code bruno.test.} recipient sentinel
     *        (e.g. {@code bruno.test.%})
     * @param subjectPattern {@code LIKE} pattern for the {@code BRUNO-TEST-} subject marker
     *        (e.g. {@code %BRUNO-TEST-%})
     * @param bodyPattern {@code LIKE} pattern for the {@code BRUNO-TEST-} body marker
     *        (e.g. {@code %BRUNO-TEST-%})
     * @return number of notification rows deleted
     */
    /**
     * Delete leftover test task templates by name (Bruno {@code 02-create-task-template.bru}
     * persists a {@code saveAsTemplate} template named "Test Custom Template" that has no
     * per-run teardown, so they accumulate on the shared/prod DB).
     *
     * <p>Guards:
     * <ul>
     *   <li>{@code is_default = false} — a seeded default template can never be deleted (defence
     *       in depth; the test templates are non-default and real defaults don't match the name);</li>
     *   <li>{@code id NOT IN (referenced template_ids)} — never delete a template still referenced
     *       by an {@code event_tasks.template_id} FK (no ON DELETE CASCADE on that column), so the
     *       sweep can never FK-fail and roll back the whole cleanup transaction. The Bruno test
     *       never creates tasks from its template, so its rows are always unreferenced.</li>
     * </ul>
     *
     * @param namePattern {@code LIKE} pattern for the template {@code name} (e.g. {@code Test Custom Template%})
     * @return number of task_template rows deleted
     */
    @Modifying
    @Query(
            value = "DELETE FROM task_templates WHERE name LIKE :namePattern "
                    + "AND is_default = false "
                    + "AND id NOT IN (SELECT template_id FROM event_tasks "
                    + "WHERE template_id IS NOT NULL)",
            nativeQuery = true
    )
    int deleteTaskTemplatesByNameLike(@Param("namePattern") String namePattern);

    @Modifying
    @Query(
            value = "DELETE FROM notifications WHERE event_code LIKE :eventCodePattern "
                    + "OR (event_code ~ '^BATbern[0-9]{1,9}$' "
                    + "AND CAST(SUBSTRING(event_code FROM 8) AS INTEGER) >= :numberThreshold) "
                    + "OR recipient_username LIKE :recipientPattern "
                    + "OR subject LIKE :subjectPattern "
                    + "OR body LIKE :bodyPattern",
            nativeQuery = true
    )
    int deleteTestNotifications(
            @Param("eventCodePattern") String eventCodePattern,
            @Param("numberThreshold") int numberThreshold,
            @Param("recipientPattern") String recipientPattern,
            @Param("subjectPattern") String subjectPattern,
            @Param("bodyPattern") String bodyPattern
    );
}
