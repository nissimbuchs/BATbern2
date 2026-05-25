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
        TOPICS(Pattern.compile("^bruno-test-topic-$"));

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
                        "Unknown entityType: '" + value + "'. Allowed: events, sessions, topics"
                );
            }
        }
    }

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

        switch (entityType) {
            case EVENTS:
                int events = repository.deleteEventsByEventCodeLike(likePattern);
                counts.put("events", events);
                // FK ON DELETE CASCADE removes: event_tasks, speaker_pool (+ its dependents),
                // event_photos, event_teaser_images, registrations, sessions (+ session_users,
                // session_materials), speaker_status_history, speaker_reminder_log.
                counts.put("event_tasks_cascade", -1);
                counts.put("speaker_pool_cascade", -1);
                counts.put("registrations_cascade", -1);
                counts.put("sessions_cascade", -1);
                break;
            case SESSIONS:
                int sessions = repository.deleteSessionsBySessionSlugLike(likePattern);
                counts.put("sessions", sessions);
                counts.put("session_users_cascade", -1);
                counts.put("session_materials_cascade", -1);
                break;
            case TOPICS:
                int topics = repository.deleteTopicsByTopicCodeLike(likePattern);
                counts.put("topics", topics);
                counts.put("topic_usage_history_cascade", -1);
                break;
            default:
                throw new IllegalStateException("Unhandled entity type: " + entityType);
        }

        String caller = SecurityContextHolder.getContext().getAuthentication() != null
                ? SecurityContextHolder.getContext().getAuthentication().getName()
                : "unknown";
        log.warn(
                "Test fixture cleanup executed: caller={} entityType={} prefix={} counts={}",
                caller, entityType, request.getPrefix(), counts
        );

        return TestFixtureCleanupResponse.builder()
                .deletionCounts(counts)
                .executedAt(Instant.now())
                .entityType(request.getEntityType())
                .prefix(request.getPrefix())
                .build();
    }
}
