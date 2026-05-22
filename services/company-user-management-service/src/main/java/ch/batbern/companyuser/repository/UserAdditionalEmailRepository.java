package ch.batbern.companyuser.repository;

import ch.batbern.companyuser.domain.User;
import ch.batbern.companyuser.domain.UserAdditionalEmail;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
