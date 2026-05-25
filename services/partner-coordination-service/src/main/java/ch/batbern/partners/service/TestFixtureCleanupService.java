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
import java.util.Map;
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
     * Entity types this service can clean, with their bound prefix-validation regex.
     *
     * <p>{@code PARTNERS} accepts only the literal prefix {@code brtest} — anchored regex
     * rejects {@code BAT}, {@code brt}, wildcards, and SQL-injection-shaped values at the
     * service layer before any DELETE runs.
     */
    public enum CleanupEntityType {
        PARTNERS(Pattern.compile("^brtest$"));

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
                        "Unknown entityType: '" + value + "'. Allowed: partners"
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
            case PARTNERS:
                int partners = repository.deletePartnersByCompanyNameLike(likePattern);
                counts.put("partners", partners);
                // FK ON DELETE CASCADE handles partner_meeting_attendance + partner_notes
                // automatically. topic_votes / topic_suggestions are NOT linked by FK after
                // the V4 rebuild (they reference company_name as a string per ADR-003) and
                // are NOT cleaned up here — Bruno tests that create those rows must clean
                // them up explicitly, or scripts/db/bruno-staging-pre-cleanup.sql handles
                // them in one-shot for legacy junk. Counts are omitted rather than
                // -1-sentinelled to keep the API shape clean.
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
