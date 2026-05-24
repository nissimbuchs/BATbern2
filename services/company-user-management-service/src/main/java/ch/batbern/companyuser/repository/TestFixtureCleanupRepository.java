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
     * Delete logos whose {@code s3_key} or {@code associated_entity_id} starts with the prefix.
     * Targets two failure modes:
     * <ol>
     *   <li>Test uploads in PENDING/CONFIRMED with prefix in the filename ({@code s3_key}).</li>
     *   <li>Logos associated with a test company by name (about to be deleted in the same
     *       transaction — soft FK).</li>
     * </ol>
     *
     * @param keyPattern   {@code LIKE} pattern for the s3_key column (e.g., {@code "%/bruno-test-%"})
     * @param entityIdPattern {@code LIKE} pattern for the associated_entity_id column
     * @return number of logo rows deleted
     */
    @Modifying
    @Query(
            value = "DELETE FROM logos WHERE s3_key LIKE :keyPattern OR associated_entity_id LIKE :entityIdPattern",
            nativeQuery = true
    )
    int deleteLogosByKeyOrEntityIdLike(
            @Param("keyPattern") String keyPattern,
            @Param("entityIdPattern") String entityIdPattern
    );

    /**
     * Delete companies whose {@code name} starts with the prefix.
     * Caller must have already wiped soft-FK references in {@code logos.associated_entity_id}
     * (handled by {@link #deleteLogosByKeyOrEntityIdLike(String, String)}).
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
}
