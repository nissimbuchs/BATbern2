package ch.batbern.events.service;

import ch.batbern.events.dto.TestFixtureCleanupRequest;
import ch.batbern.events.dto.TestFixtureCleanupResponse;
import ch.batbern.events.repository.TestFixtureCleanupRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Bruno test-fixture cleanup logic for EMS.
 *
 * <p>Removes rows in {@code events}, {@code sessions}, and {@code topics} that match
 * canonical Bruno test-data prefixes (see {@code bruno-tests/README.md}). The set of allowed
 * prefixes per entity type is locked in this class — request bodies cannot supply arbitrary
 * patterns, only one of the literal prefixes we recognize.
 *
 * <p>Belt-and-suspenders gating:
 * <ol>
 *   <li>Controller-layer {@code @PreAuthorize("hasRole('ORGANIZER')")} requires the caller's
 *       JWT to carry the organizer role.</li>
 *   <li>Service-layer regex validation in {@link #cleanup(TestFixtureCleanupRequest)} rejects
 *       prefixes that don't match the bound pattern for the requested entity type.</li>
 *   <li>The DELETE statements use parameterized {@code LIKE :pattern} (set-based) — the prefix
 *       is concatenated with {@code "%"} but cannot inject SQL because Spring Data binds it
 *       as a parameter, not by string concat into the query.</li>
 * </ol>
 *
 * <p>Bypasses the event workflow state machine intentionally: a Bruno test event may be in any
 * workflow state at cleanup time, including states that block deletion via normal service-layer
 * code paths. Native {@code DELETE FROM events WHERE event_code LIKE ...} skirts those guards;
 * cascade FKs handle dependents.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TestFixtureCleanupService {

    /**
     * Entity types this service can clean, with their bound prefix-validation regex.
     *
     * <p>The validation regex is anchored to the FULL prefix — a request with
     * {@code prefix=BAT} for entityType=events is rejected because {@code BAT} doesn't match
     * {@code ^BRUNO-TEST-$}. The deletion then runs {@code LIKE 'BRUNO-TEST-%'}.
     *
     * <p>To add a new canonical prefix later (e.g., for E2E Playwright tests), widen the
     * validation regex here — do NOT accept new prefixes via the request body.
     */
    public enum CleanupEntityType {
        EVENTS(Pattern.compile("^BRUNO-TEST-$")),
        SESSIONS(Pattern.compile("^bruno-test-session-$")),
        TOPICS(Pattern.compile("^bruno-test-topic-$")),
        /**
         * Force-deletes test events by RESERVED EVENT-NUMBER RANGE rather than by
         * {@code event_code} prefix. This is the reliable discriminator for events whose
         * {@code event_code} is SERVER-GENERATED ({@code BATbern{event_number}}) and therefore
         * cannot be reached by the {@link #EVENTS} {@code BRUNO-TEST-%} prefix sweep — i.e.
         * essentially every event a fixture creates via {@code POST /events}, since the code
         * is derived from the supplied {@code event_number}, not a canonical prefix.
         *
         * <p>Convention (issue: event-fixture leak, 2026-06-01): every test fixture creates
         * events with {@code event_number} in the range {@code [10000, 99999]} (random). Real
         * BATbern events are numbered sequentially and sit far below this (currently ≤ 60; the
         * conference will never reach 10 000 editions), so {@code event_number >= 10000} is an
         * unambiguous, collision-proof test marker.
         *
         * <p>The {@code prefix} request field carries the literal threshold sentinel
         * {@code "10000"}, validated by the bound regex {@code ^10000$} — the request body
         * CANNOT supply a lower (more dangerous) threshold; only the one constant we recognize.
         * The actual deletion uses {@link #TEST_EVENT_NUMBER_THRESHOLD}.
         *
         * <p>Like {@link #EVENTS}, the delete is a native {@code DELETE FROM events WHERE
         * event_number >= …} that BYPASSES the workflow-state machine AND the real-attendee
         * delete-guard ({@code EventController} returns 409 when an event has a non-auto
         * registration). That guard is exactly why these events leak through the normal delete
         * path: a registration test stamps a genuine anonymous attendee on the fixture event, so
         * the per-test {@code DELETE /events/{code}} teardown gets 409 and silently skips it.
         * The repository-level force delete is the only safe teardown for such events.
         */
        EVENTS_BY_NUMBER(Pattern.compile("^10000$")),
        /**
         * Sweeps test-generated {@code notifications} — the in-app/email notification rows that
         * entity and state changes (workflow transitions, publishes, registrations) create as
         * side effects. They carry no FK to events (ADR-003 soft string ref), so the EVENTS
         * cascade never reaches them; left uncleaned, BRUNO-TEST notifications land in REAL
         * organizers' in-app notification lists on the production account.
         *
         * <p>The {@code prefix} field carries the literal sentinel {@code BRUNO-TEST-}
         * (validated by {@code ^BRUNO-TEST-$}); the actual delete is a composite predicate
         * (see {@code TestFixtureCleanupRepository#deleteTestNotifications}) that also reaches
         * server-coded {@code BATbern{N}} (N &gt;= {@link #TEST_EVENT_NUMBER_THRESHOLD}),
         * {@code bruno.test.*} recipient rows, AND rows carrying a {@code BRUNO-TEST-} marker in
         * their {@code subject}/{@code body} (which catches test notifications with a NULL
         * {@code event_code}) — none of which the bare event_code sentinel can reach.
         */
        NOTIFICATIONS(Pattern.compile("^BRUNO-TEST-$"));

        private final Pattern allowedPrefix;

        CleanupEntityType(Pattern allowedPrefix) {
            this.allowedPrefix = allowedPrefix;
        }

        public boolean validates(String prefix) {
            return prefix != null && allowedPrefix.matcher(prefix).matches();
        }

        public String regexDescription() {
            return allowedPrefix.pattern();
        }

        static CleanupEntityType fromString(String value) {
            if (value == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "entityType is required");
            }
            try {
                return CleanupEntityType.valueOf(value.toUpperCase());
            } catch (IllegalArgumentException ex) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Unknown entityType: '" + value
                                + "'. Allowed: events, sessions, topics, events_by_number, notifications"
                );
            }
        }
    }

    /**
     * Event numbers at or above this value are RESERVED for test fixtures (issue:
     * event-fixture leak, 2026-06-01). Real BATbern events are numbered sequentially and will
     * never approach this, so {@code event_number >= 10000} is an unambiguous test marker that
     * the {@link CleanupEntityType#EVENTS_BY_NUMBER} sweep force-deletes. Kept in lock-step
     * with the fixtures' generation range ({@code 10000 + random(90000)}) and the
     * {@code SWEEP_TARGETS} entry in {@code web-frontend/e2e/helpers/test-fixtures-cleanup.ts}.
     */
    public static final int TEST_EVENT_NUMBER_THRESHOLD = 10000;

    private final TestFixtureCleanupRepository repository;

    /**
     * Execute cleanup for one entity type.
     *
     * @throws ResponseStatusException 400 on unknown entityType or non-validating prefix.
     */
    @Transactional
    public TestFixtureCleanupResponse cleanup(TestFixtureCleanupRequest request) {
        CleanupEntityType entityType = CleanupEntityType.fromString(request.getEntityType());

        if (!entityType.validates(request.getPrefix())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Prefix '" + request.getPrefix() + "' does not match the allowed pattern "
                            + entityType.regexDescription() + " for entityType " + request.getEntityType()
            );
        }

        Map<String, Integer> counts = new LinkedHashMap<>();
        String likePattern = request.getPrefix() + "%";
        // Audit descriptor for the structured log below. Defaults to the bound prefix
        // (mirrors how PCS logs `prefix=`); the NOTIFICATIONS case overrides it with the
        // FULL composite criteria so the audit line records exactly what was matched.
        String auditTarget = "prefix=" + request.getPrefix();

        // Cascade-deleted tables (via FK ON DELETE CASCADE) are documented in the
        // TestFixtureCleanupResponse Javadoc and the per-case comments below. Their counts
        // are intentionally NOT emitted in the response — the previous -1 sentinel leaked
        // implementation detail through the API. The Bruno tests assert only on the
        // top-level deletion counts (events / sessions / topics), so omitting the cascade
        // keys is a no-op for callers.
        switch (entityType) {
            case EVENTS:
                int events = repository.deleteEventsByEventCodeLike(likePattern);
                counts.put("events", events);
                // FK ON DELETE CASCADE removes: event_tasks, speaker_pool (+ its dependents),
                // event_photos, event_teaser_images, registrations, sessions (+ session_users,
                // session_materials), speaker_status_history, speaker_reminder_log.
                break;
            case SESSIONS:
                int sessions = repository.deleteSessionsBySessionSlugLike(likePattern);
                counts.put("sessions", sessions);
                // Cascades: session_users, session_materials.
                break;
            case TOPICS:
                int topics = repository.deleteTopicsByTopicCodeLike(likePattern);
                counts.put("topics", topics);
                // Cascades: topic_usage_history.
                break;
            case EVENTS_BY_NUMBER:
                // Force-delete by reserved event-number range — reaches server-coded
                // (BATbern{N}) test events the prefix sweep can't, and bypasses the
                // real-attendee 409 guard that makes registration-fixture events
                // undeletable via the normal path. Same cascade chain as EVENTS.
                int eventsByNumber =
                        repository.deleteEventsByEventNumberGte(TEST_EVENT_NUMBER_THRESHOLD);
                counts.put("events", eventsByNumber);
                break;
            case NOTIFICATIONS:
                // Composite sweep: BRUNO-TEST-% event_code OR reserved-range BATbern{N}
                // (N >= threshold) OR bruno.test.% recipient OR a BRUNO-TEST- marker anywhere
                // in subject/body. No FK cascade — notifications are standalone (ADR-003 soft
                // event_code ref). The bare event_code sentinel can't reach server-coded
                // BATbern{N} / bruno.test.* rows, nor test notifications that carry a NULL
                // event_code but stamp the run's BRUNO-TEST- marker into their rendered text —
                // hence the composite predicate.
                String markerPattern = "%" + request.getPrefix() + "%";
                int notifications = repository.deleteTestNotifications(
                        likePattern, TEST_EVENT_NUMBER_THRESHOLD, "bruno.test.%",
                        markerPattern, markerPattern);
                counts.put("notifications", notifications);
                // Record the full composite criteria in the audit line (not just the prefix),
                // so an operator reading the log sees every discriminator that could have
                // matched a deleted row.
                auditTarget = "criteria=[event_code LIKE " + likePattern
                        + " OR BATbern{N>=" + TEST_EVENT_NUMBER_THRESHOLD + "}"
                        + " OR recipient_username LIKE bruno.test.%"
                        + " OR subject/body LIKE " + markerPattern + "]";
                break;
            default:
                throw new IllegalStateException("Unhandled entity type: " + entityType);
        }

        String caller = SecurityContextHolder.getContext().getAuthentication() != null
                ? SecurityContextHolder.getContext().getAuthentication().getName()
                : "unknown";
        log.warn(
                "Test fixture cleanup executed: caller={} entityType={} {} counts={}",
                caller, entityType, auditTarget, counts
        );

        return TestFixtureCleanupResponse.builder()
                .deletionCounts(counts)
                .executedAt(Instant.now())
                .entityType(request.getEntityType())
                .prefix(request.getPrefix())
                .build();
    }
}
