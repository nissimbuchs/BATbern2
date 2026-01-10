package ch.batbern.companyuser.repository;

import ch.batbern.companyuser.domain.Role;
import ch.batbern.companyuser.domain.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

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
     * Check if user exists by username
     * Story 1.16.2: Critical for duplicate username validation
     *
     * @param username User's username
     * @return true if exists, false otherwise
     */
    boolean existsByUsername(String username);

    /**
     * Find user by email address
     * AC4: Email-based user lookup
     *
     * @param email User's email
     * @return Optional user
     */
    Optional<User> findByEmail(String email);

    /**
     * Check if user exists by email
     * AC4: Duplicate email validation
     *
     * @param email User's email
     * @return true if exists, false otherwise
     */
    boolean existsByEmail(String email);

    /**
     * Find user by Cognito user ID
     * AC5: Cognito authentication integration
     *
     * @param cognitoUserId AWS Cognito user identifier
     * @return Optional user
     */
    Optional<User> findByCognitoUserId(String cognitoUserId);

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
     * Find users by name (first or last name, case-insensitive)
     * AC8: User search functionality
     *
     * @param firstName Partial first name
     * @param lastName Partial last name
     * @return List of matching users
     */
    List<User> findByFirstNameContainingIgnoreCaseOrLastNameContainingIgnoreCase(String firstName, String lastName);

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

    /**
     * Performance Optimization: Find all users with roles eagerly fetched
     * Uses JOIN FETCH to avoid N+1 query problem
     *
     * @param pageable Pagination parameters
     * @return Page of users with roles loaded
     */
    @Query("""
        SELECT DISTINCT u FROM User u
        LEFT JOIN FETCH u.roles
        ORDER BY u.lastName ASC, u.firstName ASC
        """)
    Page<User> findAllWithRoles(Pageable pageable);

    /**
     * Performance Optimization: Find users by role with roles eagerly fetched
     * Uses JOIN FETCH to avoid N+1 query problem
     *
     * @param role User role to filter by
     * @param pageable Pagination parameters
     * @return Page of users with the specified role and roles loaded
     */
    @Query("""
        SELECT DISTINCT u FROM User u
        LEFT JOIN FETCH u.roles r
        WHERE :role MEMBER OF u.roles
        ORDER BY u.lastName ASC, u.firstName ASC
        """)
    Page<User> findByRolesContainingWithRoles(@Param("role") Role role, Pageable pageable);

    /**
     * Performance Optimization: Find users by company with roles eagerly fetched
     * Uses JOIN FETCH to avoid N+1 query problem
     *
     * @param companyId Company ID to filter by
     * @param pageable Pagination parameters
     * @return Page of users in the specified company with roles loaded
     */
    @Query("""
        SELECT DISTINCT u FROM User u
        LEFT JOIN FETCH u.roles
        WHERE u.companyId = :companyId
        ORDER BY u.lastName ASC, u.firstName ASC
        """)
    Page<User> findByCompanyIdWithRoles(@Param("companyId") String companyId, Pageable pageable);

    /**
     * Performance Optimization: Find users by role AND company with roles eagerly fetched
     * Uses JOIN FETCH to avoid N+1 query problem
     *
     * @param role User role to filter by
     * @param companyId Company ID to filter by
     * @param pageable Pagination parameters
     * @return Page of users matching both criteria with roles loaded
     */
    @Query("""
        SELECT DISTINCT u FROM User u
        LEFT JOIN FETCH u.roles r
        WHERE :role MEMBER OF u.roles
        AND u.companyId = :companyId
        ORDER BY u.lastName ASC, u.firstName ASC
        """)
    Page<User> findByRoleAndCompanyWithRoles(
            @Param("role") Role role,
            @Param("companyId") String companyId,
            Pageable pageable);
}
