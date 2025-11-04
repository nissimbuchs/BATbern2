package ch.batbern.companyuser.repository;

import ch.batbern.companyuser.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * UserRepository - READ-ONLY access to user_profiles table
 * ADR-004: Services share database and reference user_profiles directly
 *
 * This repository is READ-ONLY for event-management-service.
 * All write operations go through company-user-management-service.
 *
 * Story 1.15a.1b: Used for enriching SessionSpeaker responses with User data
 */
@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    /**
     * Find user by username (public identifier)
     * Used for speaker assignment: POST /sessions/{slug}/speakers
     */
    Optional<User> findByUsername(String username);

    /**
     * Check if user exists by username
     * Used for validation before speaker assignment
     */
    boolean existsByUsername(String username);
}
