package ch.batbern.events.repository;

import ch.batbern.events.domain.SpeakerInvitationToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for SpeakerInvitationToken entity.
 * Story 6.1a: Magic Link Infrastructure
 *
 * Provides data access for magic link token operations:
 * - Token lookup by hash (primary validation path)
 * - Token lookup by speaker pool ID (admin operations)
 * - Expired token cleanup (scheduled job)
 */
@Repository
public interface SpeakerInvitationTokenRepository extends JpaRepository<SpeakerInvitationToken, UUID> {

    /**
     * Find token by its SHA-256 hash.
     * Primary method for token validation - O(1) lookup via index.
     *
     * @param tokenHash the SHA-256 hash of the token
     * @return the token if found, empty otherwise
     */
    Optional<SpeakerInvitationToken> findByTokenHash(String tokenHash);

    /**
     * Find all tokens for a specific speaker pool entry.
     * Used for admin operations (view all tokens for a speaker).
     *
     * @param speakerPoolId the speaker pool ID
     * @return list of tokens for that speaker
     */
    List<SpeakerInvitationToken> findBySpeakerPoolId(UUID speakerPoolId);

    /**
     * Delete expired tokens that are older than the cutoff date.
     * Used by scheduled cleanup job (AC4: tokens older than 90 days).
     *
     * @param cutoffDate delete tokens with expires_at before this date
     * @return number of tokens deleted
     */
    @Modifying
    @Query("DELETE FROM SpeakerInvitationToken t WHERE t.expiresAt < :cutoffDate")
    int deleteExpiredBefore(@Param("cutoffDate") Instant cutoffDate);

    /**
     * Count tokens for a speaker pool (for rate limiting/admin).
     *
     * @param speakerPoolId the speaker pool ID
     * @return count of tokens
     */
    long countBySpeakerPoolId(UUID speakerPoolId);

    /**
     * Check if a token hash already exists (uniqueness check).
     *
     * @param tokenHash the SHA-256 hash to check
     * @return true if hash exists
     */
    boolean existsByTokenHash(String tokenHash);
}
