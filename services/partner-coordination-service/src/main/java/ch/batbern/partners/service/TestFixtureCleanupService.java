package ch.batbern.partners.service;

import ch.batbern.partners.dto.TestFixtureCleanupRequest;
import ch.batbern.partners.dto.TestFixtureCleanupResponse;
import ch.batbern.partners.repository.TestFixtureCleanupRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Bruno test-fixture cleanup logic for PCS.
 *
 * <p>Removes rows in {@code partners} that match canonical Bruno test-data prefixes
 * (see {@code bruno-tests/README.md}). The set of allowed prefixes is locked in this
 * class — request bodies cannot supply arbitrary patterns, only the literal prefix value
 * we recognize.
 *
 * <p>The partners table has an unusually tight constraint: {@code company_name VARCHAR(12)}
 * (see {@code V2__create_partner_coordination_schema.sql:9}, mismatched with
 * {@code companies.name VARCHAR(255)} — known schema bug, separate follow-up ticket). This
 * forces Bruno to use the short prefix {@code brtest} for partner test fixtures, distinct
 * from the {@code BRUNOTESTCO} / {@code BRUNO-TEST-} prefixes used elsewhere.
 *
 * <p>Belt-and-suspenders gating:
 * <ol>
 *   <li>Controller-layer {@code @PreAuthorize("hasRole('ORGANIZER')")} requires the caller's
 *       JWT to carry the organizer role.</li>
 *   <li>Service-layer regex validation rejects prefixes that don't match the bound pattern.</li>
 *   <li>The DELETE statement uses parameterized {@code LIKE :pattern} — Spring Data binds the
 *       prefix as a parameter, so SQL injection is structurally impossible.</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TestFixtureCleanupService {

    /**
     * Upper bound on the meeting-id allowlist size — blast-radius cap so a malformed or
     * runaway caller can't issue an unbounded {@code DELETE ... WHERE id IN (...)}.
     */
    static final int MAX_MEETING_IDS = 200;

    /**
     * Entity types this service can clean.
     *
     * <p>{@code PARTNERS} is prefix-based: accepts only the literal prefix {@code brtest} —
     * the anchored regex rejects {@code BAT}, {@code brt}, wildcards, and SQL-injection-shaped
     * values at the service layer before any DELETE runs.
     *
     * <p>{@code MEETINGS} is id-allowlist-based ({@code allowedPrefix == null}): {@code
     * partner_meetings} carries no Bruno-identifying column, so it is cleaned by an explicit
     * list of meeting UUIDs rather than a prefix sweep (plan §B2 option 1).
     */
    public enum CleanupEntityType {
        PARTNERS(Pattern.compile("^brtest$")),
        MEETINGS(null),
        /**
         * Prefix-based cleanup of {@code topic_suggestions} by {@code title}. Accepts only the
         * literal prefix {@code Bruno Test Topic} (anchored regex {@code ^Bruno Test Topic$}) —
         * the {@code 10-suggest-topic-as-partner} fixture titles every test topic
         * {@code "Bruno Test Topic - …"}. The DELETE then runs {@code title LIKE 'Bruno Test Topic%'};
         * {@code topic_votes} cascade-delete via the {@code topic_id} FK ON DELETE CASCADE (V4),
         * so no separate vote delete is required.
         */
        TOPICS(Pattern.compile("^Bruno Test Topic$"));

        private final Pattern allowedPrefix;

        CleanupEntityType(Pattern allowedPrefix) {
            this.allowedPrefix = allowedPrefix;
        }

        /** True when this entity type is cleaned by prefix (false → id-allowlist). */
        public boolean usesPrefix() {
            return allowedPrefix != null;
        }

        public boolean validates(String prefix) {
            return allowedPrefix != null && prefix != null && allowedPrefix.matcher(prefix).matches();
        }

        public String regexDescription() {
            return allowedPrefix != null ? allowedPrefix.pattern() : "(n/a — id allowlist)";
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
                        "Unknown entityType: '" + value + "'. Allowed: partners, meetings, topics"
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

        Map<String, Integer> counts = new LinkedHashMap<>();
        String auditTarget;

        switch (entityType) {
            case PARTNERS:
                validatePrefix(entityType, request.getPrefix());
                int partners = repository.deletePartnersByCompanyNameLike(request.getPrefix() + "%");
                counts.put("partners", partners);
                auditTarget = "prefix=" + request.getPrefix();
                // FK ON DELETE CASCADE handles partner_meeting_attendance + partner_notes
                // automatically. topic_votes / topic_suggestions are NOT linked by FK after
                // the V4 rebuild (they reference company_name as a string per ADR-003) and
                // are NOT reached by this partners sweep — they are cleaned by their own
                // entityType=topics case below (prefix on title). Counts are omitted rather
                // than -1-sentinelled to keep the API shape clean.
                break;
            case MEETINGS:
                List<UUID> ids = validateMeetingIds(request.getMeetingIds());
                int meetings = repository.deleteMeetingsByIdIn(ids);
                counts.put("meetings", meetings);
                auditTarget = "meetingIds=" + ids.size();
                // partner_meeting_attendance + partner_meeting_rsvps cascade from
                // partner_meetings(id) via ON DELETE CASCADE (V2:152 + V9:9).
                break;
            case TOPICS:
                validatePrefix(entityType, request.getPrefix());
                int topics = repository.deleteTopicsByTitleLike(request.getPrefix() + "%");
                counts.put("topics", topics);
                auditTarget = "prefix=" + request.getPrefix();
                // topic_votes cascade-delete from topic_suggestions(id) via the topic_id FK
                // ON DELETE CASCADE (V4) — no explicit vote delete needed.
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

    /**
     * Validate a prefix-based cleanup request against the entity's bound regex.
     *
     * @throws ResponseStatusException 400 when the prefix is null, empty, or doesn't match.
     */
    private void validatePrefix(CleanupEntityType entityType, String prefix) {
        if (!entityType.validates(prefix)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Prefix '" + prefix + "' does not match the allowed pattern "
                            + entityType.regexDescription() + " for entityType " + entityType.name().toLowerCase()
            );
        }
    }

    /**
     * Validate the meeting-id allowlist for {@code entityType=meetings}.
     *
     * <p>The list must be present, non-empty, and within {@link #MAX_MEETING_IDS}. Element-level
     * UUID well-formedness is enforced upstream by Jackson deserialization into {@code UUID}.
     *
     * @throws ResponseStatusException 400 when the list is missing, empty, or over the cap.
     */
    private List<UUID> validateMeetingIds(List<UUID> meetingIds) {
        if (meetingIds == null || meetingIds.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "meetingIds must be a non-empty allowlist for entityType meetings"
            );
        }
        if (meetingIds.size() > MAX_MEETING_IDS) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "meetingIds exceeds the maximum of " + MAX_MEETING_IDS + " ids per request"
            );
        }
        return meetingIds;
    }
}
