package ch.batbern.companyuser.repository;

import ch.batbern.companyuser.domain.User;
import ch.batbern.companyuser.domain.UserAdditionalEmail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Story 10.32: repository for {@link UserAdditionalEmail}.
 */
@Repository
public interface UserAdditionalEmailRepository extends JpaRepository<UserAdditionalEmail, UUID> {

    /**
     * Case-insensitive global uniqueness check across the additional-emails
     * table. Use together with {@link UserRepository#existsByEmailIgnoreCase}
     * for the full "primary OR additional" duplicate guard.
     */
    boolean existsByEmailIgnoreCase(String email);

    /**
     * Look up a specific additional email on a specific user (used by the
     * delete endpoint).
     */
    Optional<UserAdditionalEmail> findByUserAndEmailIgnoreCase(User user, String email);

    /**
     * Count additional emails for a given user — used by the cap check.
     */
    long countByUser(User user);

    /**
     * Look up a VERIFIED additional email (Story A {@code verified_at}) case-insensitively,
     * eagerly fetching the owning user. Used by the JIT duplicate guard
     * ({@code JITUserProvisioningInterceptor}): a federated sign-in whose email is a user's
     * verified additional email should have been linked by the PreSignUp Lambda — if it
     * reaches JIT unresolved, we must NOT create a duplicate user. The {@code JOIN FETCH}
     * avoids a LazyInitializationException when the caller reads {@code getUser()} outside
     * the persistence context.
     */
    @Query("SELECT ae FROM UserAdditionalEmail ae JOIN FETCH ae.user "
            + "WHERE LOWER(ae.email) = LOWER(:email) AND ae.verifiedAt IS NOT NULL")
    Optional<UserAdditionalEmail> findVerifiedByEmailIgnoreCase(@Param("email") String email);
}
