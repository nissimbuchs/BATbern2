package ch.batbern.companyuser.repository;

import ch.batbern.companyuser.domain.Company;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Native-query repository for the test-fixture cleanup endpoint.
 *
 * <p>Bypasses the regular {@code CompanyRepository} / {@code UserRepository} so that:
 * <ul>
 *   <li>The cleanup-specific {@code @PreAuthorize}-gated controller is the only entry point;
 *       the deletion methods aren't reachable from normal application code paths.</li>
 *   <li>The DELETE statements use {@code LIKE :pattern} for predictable, set-based deletion
 *       with one SQL round-trip per table — JPA cascade isn't relied on for performance.</li>
 *   <li>The {@code logos.associated_entity_id} reference (soft FK by name, not a real
 *       database constraint) is wiped explicitly before its parent.</li>
 * </ul>
 *
 * <p>Cascade behavior on {@code user_profiles} delete (declared in V5 + V16 migrations):
 * <ul>
 *   <li>{@code role_assignments.user_id} → {@code user_profiles.id} ON DELETE CASCADE</li>
 *   <li>{@code user_additional_emails.user_id} → {@code user_profiles.id} ON DELETE CASCADE</li>
 *   <li>{@code watch_pairings.username} → {@code user_profiles.username} (no cascade) —
 *       test users never have watch pairings so this is OK</li>
 * </ul>
 *
 * Bound to {@code Company} as the parameterized type purely so Spring Data picks it up as a
 * repository bean; none of the methods here use the JpaRepository<Company, UUID> interface.
 */
@Repository
public interface TestFixtureCleanupRepository extends JpaRepository<Company, UUID> {

    /**
     * Delete logos whose {@code associated_entity_id} starts with the prefix
     * (i.e. logos linked to test companies/users by ADR-003 soft FK).
     *
     * <p>Anchored on the soft-FK column ONLY. An earlier version also matched
     * {@code s3_key LIKE '%/<prefix>%'} but that pattern was fragile against S3 key
     * layout changes and risked matching unrelated keys where the prefix appeared
     * after any path separator. PENDING / CONFIRMED logos for failed Bruno uploads
     * are swept by the {@code expires_at} lifecycle column, not by this endpoint.
     *
     * @param entityIdPattern {@code LIKE} pattern for the associated_entity_id column
     *                        (e.g. {@code "BRUNOTESTCO%"})
     * @return number of logo rows deleted
     */
    @Modifying
    @Query(
            value = "DELETE FROM logos WHERE associated_entity_id LIKE :entityIdPattern",
            nativeQuery = true
    )
    int deleteLogosByAssociatedEntityIdLike(@Param("entityIdPattern") String entityIdPattern);

    /**
     * Delete companies whose {@code name} starts with the prefix.
     * Caller must have already wiped soft-FK references in {@code logos.associated_entity_id}
     * (handled by {@link #deleteLogosByAssociatedEntityIdLike(String)}).
     *
     * @param namePattern {@code LIKE} pattern for the name column
     * @return number of company rows deleted
     */
    @Modifying
    @Query(
            value = "DELETE FROM companies WHERE name LIKE :namePattern",
            nativeQuery = true
    )
    int deleteCompaniesByNameLike(@Param("namePattern") String namePattern);

    /**
     * Delete users whose {@code username} starts with the prefix.
     * Relies on ON DELETE CASCADE for {@code role_assignments} + {@code user_additional_emails}.
     *
     * @param usernamePattern {@code LIKE} pattern for the username column
     * @return number of user_profile rows deleted (excludes cascade-deleted dependents)
     */
    @Modifying
    @Query(
            value = "DELETE FROM user_profiles WHERE username LIKE :usernamePattern",
            nativeQuery = true
    )
    int deleteUserProfilesByUsernameLike(@Param("usernamePattern") String usernamePattern);

    /**
     * Delete additional-email rows whose {@code email} starts with the prefix.
     *
     * <p>Plan §F4: targets the {@code bruno-test-…@e2e.batbern.invalid} canonical
     * and the legacy {@code bruno-additional-NNN@example.com} prefix. Bypasses the
     * user-delete cascade so the {@code 00-pretest-cleanup} hook can sweep stale
     * additional emails without removing the test users that own them — useful
     * when the leakage is on a real-named auth user (e.g. batbern.organizer)
     * rather than a {@code bruno.test.*} disposable.
     *
     * <p>Match is case-insensitive ({@code LOWER(email) LIKE LOWER(:pattern)})
     * for defensive normalization.
     *
     * @param emailPattern {@code LIKE} pattern for the email column
     * @return number of additional-email rows deleted
     */
    @Modifying
    @Query(
            value = "DELETE FROM user_additional_emails WHERE LOWER(email) LIKE LOWER(:emailPattern)",
            nativeQuery = true
    )
    int deleteAdditionalEmailsByEmailLike(@Param("emailPattern") String emailPattern);
}
