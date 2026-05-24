package ch.batbern.companyuser.service;

import ch.batbern.companyuser.dto.TestFixtureCleanupRequest;
import ch.batbern.companyuser.dto.TestFixtureCleanupResponse;
import ch.batbern.companyuser.repository.TestFixtureCleanupRepository;
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
 * Bruno test-fixture cleanup logic for CUMS.
 *
 * <p>Removes rows in {@code companies}, {@code user_profiles}, and {@code logos} that match
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
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TestFixtureCleanupService {

    /**
     * Entity types this service can clean, with their bound prefix-validation regex.
     *
     * <p>The validation regex is anchored to the FULL prefix — a request with
     * {@code prefix=BAT} for entityType=companies is rejected because {@code BAT} doesn't
     * match {@code ^BRUNOTESTCO$}. The deletion then runs {@code LIKE 'BRUNOTESTCO%'}.
     *
     * <p>To add a new canonical prefix later (e.g., for E2E Playwright tests), widen the
     * validation regex here — do NOT accept new prefixes via the request body.
     */
    public enum CleanupEntityType {
        COMPANIES(Pattern.compile("^BRUNOTESTCO$")),
        USERS(Pattern.compile("^bruno\\.test\\.$"));

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
                        "Unknown entityType: '" + value + "'. Allowed: companies, users"
                );
            }
        }
    }

    private final TestFixtureCleanupRepository repository;

    /**
     * Execute cleanup for one entity type.
     *
     * @throws IllegalArgumentException on unknown entityType or non-validating prefix.
     *         Caught by {@code GlobalExceptionHandler} which returns 400.
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
            case COMPANIES:
                // Wipe logos referencing companies-about-to-be-deleted first (soft FK).
                // The %/ prefix in keyPattern matches s3_key paths like logos/temp/{uploadId}/file.png
                // where the upload was for a test company. associated_entity_id is the company name.
                int logosForCompanies = repository.deleteLogosByKeyOrEntityIdLike(
                        "%/" + request.getPrefix() + "%",  // s3_key containing test prefix
                        request.getPrefix() + "%"          // associated_entity_id starting with prefix
                );
                int companies = repository.deleteCompaniesByNameLike(likePattern);
                counts.put("logos", logosForCompanies);
                counts.put("companies", companies);
                break;
            case USERS:
                int users = repository.deleteUserProfilesByUsernameLike(likePattern);
                // role_assignments + user_additional_emails cascade-delete via FK ON DELETE CASCADE
                counts.put("user_profiles", users);
                counts.put("role_assignments_cascade", -1);  // -1 = "cascade, count not measured"
                counts.put("user_additional_emails_cascade", -1);
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
