package ch.batbern.companyuser.repository;

import ch.batbern.companyuser.domain.User;
import ch.batbern.companyuser.specification.UserSpecification;
import ch.batbern.shared.types.Role;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for User aggregate root
 * Story 1.14-2: User Management Service Foundation
 * Story 1.16.2: Username-based lookups (CRITICAL - public API ID)
 * AC1: User entity persistence
 * AC3: Username-based queries (primary lookup method)
 * AC4: Email-based queries
 * AC5: Cognito integration queries
 * AC6: Company affiliation queries
 * AC7: Role-based queries
 */
@Repository
public interface UserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {

    /**
     * Find user by username (Story 1.16.2: PRIMARY lookup method)
     * Username is the public API identifier
     *
     * @param username User's username (e.g., "john.doe")
     * @return Optional user
     */
    Optional<User> findByUsername(String username);

    /**
     * Story 10.32 (P0-1 fix) — find user by username with a pessimistic
     * write lock on the {@code user_profiles} row. Used by
     * {@code addAdditionalEmail} to serialise concurrent POSTs against the
     * per-user 5-email cap. The default READ_COMMITTED isolation lets two
     * concurrent transactions both see {@code countByUser=4}, both pass the
     * gate, and both INSERT; pessimistic lock on the parent row forces one
     * to wait until the other commits, at which point its re-read of the
     * count sees the updated value and rejects the 6th entry.
     *
     * @param username User's username
     * @return Optional user (locked FOR UPDATE if present)
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.username = :username")
    Optional<User> findByUsernameForUpdate(@Param("username") String username);

    /**
     * Check if user exists by username
     * Story 1.16.2: Critical for duplicate username validation
     *
     * @param username User's username
     * @return true if exists, false otherwise
     */
    boolean existsByUsername(String username);

    /**
     * Find user by email address (case-insensitive).
     * Story 11.C.2 review: provisionUserWithRole + getOrCreateUser idempotency requires
     * "Jane@x.com" and "jane@x.com" to resolve to the same row.
     *
     * @param email User's email (any case)
     * @return Optional user
     */
    Optional<User> findByEmailIgnoreCase(String email);

    /**
     * Check if user exists by email (case-insensitive).
     * Story 11.C.2 review: idempotency parity with findByEmailIgnoreCase.
     *
     * @param email User's email (any case)
     * @return true if exists, false otherwise
     */
    boolean existsByEmailIgnoreCase(String email);

    /**
     * Find user by Cognito user ID
     * AC5: Cognito authentication integration
     *
     * @param cognitoUserId AWS Cognito user identifier
     * @return Optional user
     */
    Optional<User> findByCognitoUserId(String cognitoUserId);

    /**
     * Story 12.12 review (finding #5) — atomically claim the one-time federated avatar
     * import attempt. The previous load-check-save sequence let two concurrent first
     * federated requests both pass the NULL guards and double-dispatch the Google fetch;
     * this conditional UPDATE is a compare-and-set the database serialises: exactly one
     * caller sees affected-rows == 1.
     *
     * <p>{@code clearAutomatically} so entities already in the persistence context don't
     * shadow the bulk update on a subsequent read in the same session/transaction.
     *
     * @return 1 if this caller won the claim, 0 if it was already claimed (or a picture
     *         already exists)
     */
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("UPDATE User u SET u.pictureImportAttemptedAt = :attemptedAt "
            + "WHERE u.id = :id AND u.pictureImportAttemptedAt IS NULL AND u.profilePictureUrl IS NULL")
    int claimPictureImportAttempt(@Param("id") UUID id, @Param("attemptedAt") Instant attemptedAt);

    /**
     * Story 12.12 review (finding #1) — release a claimed avatar-import attempt after a
     * TRANSIENT failure (upstream 5xx/429, network timeout, executor rejection) so a later
     * federated request retries. Terminal outcomes (success, 3xx/4xx, non-image, oversize,
     * SSRF-rejected claim) never release: one terminal attempt per user, ever.
     * The {@code profilePictureUrl IS NULL} condition keeps the never-clobber invariant —
     * a picture that appeared meanwhile stays untouched and the claim stays burned.
     *
     * @return 1 if the claim was released, 0 otherwise
     */
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("UPDATE User u SET u.pictureImportAttemptedAt = NULL "
            + "WHERE u.id = :id AND u.profilePictureUrl IS NULL")
    int releasePictureImportAttempt(@Param("id") UUID id);

    /**
     * Find users by company ID (Story 1.16.2: company name, not UUID)
     * AC6: Company affiliation queries
     *
     * @param companyId Company name (e.g., "GoogleZH", "MicrosoftBE")
     * @return List of users in the company
     */
    List<User> findByCompanyId(String companyId);

    /**
     * Find users by role
     * AC7: Role-based user queries
     *
     * @param role User role
     * @return List of users with the specified role
     */
    @Query("SELECT u FROM User u JOIN u.roles r WHERE r = :role")
    List<User> findByRolesContaining(@Param("role") Role role);

    /**
     * Autocomplete search (the user picker): every word of {@code query} in username, first or last
     * name, primary or additional e-mail or company id, folded by {@code batbern_fold}, with an
     * optional role, ranked and limited in the DATABASE (earlier fix 2026-09-09).
     *
     * <p>History: the derived query this replaced had no ORDER BY and was cut in Java at 20, so a
     * user ranked 29th of 32 namesakes could never be picked; the role filter ran in Java after
     * that cut; full names did not match. The 2026-09-09 JPQL fixed those, but matched "first last"
     * only, ignored additional e-mails and folded nothing ("Mueller" missed "Müller").
     *
     * <p>Ranking: {@link UserSpecification#matchesSearchRanked}: exact full name, exact field,
     * prefix, other; last name, first name and the unique username break ties, so the order is
     * total and the cached autocomplete never wobbles.
     *
     * @param query Search input; blank matches everybody (ordered by name)
     * @param role Role the user must hold, or {@code null}
     * @param pageable Page; use {@code DeterministicPaging.orderedByQuery}, the order comes from the
     *     specification
     * @return Matching users, most relevant first
     */
    default List<User> searchRanked(String query, Role role, Pageable pageable) {
        return findAll(Specification.allOf(
                UserSpecification.matchesSearchRanked(query),
                UserSpecification.hasRole(role)), pageable).getContent();
    }

    /**
     * Find users by active status
     * AC9: Active/inactive user filtering
     *
     * @param isActive Active status flag
     * @return List of users matching active status
     */
    List<User> findByIsActive(boolean isActive);

    /**
     * Find users by exact first and last name match
     * Story 3.2: Batch import user matching by name when unambiguous
     *
     * Used for historical data import where email may not match but name does.
     * Returns all users with matching firstName AND lastName (exact match, case-insensitive).
     *
     * @param firstName User's first name (exact match)
     * @param lastName User's last name (exact match)
     * @return List of users matching both first and last name (empty if no match)
     */
    List<User> findByFirstNameIgnoreCaseAndLastNameIgnoreCase(String firstName, String lastName);
}
