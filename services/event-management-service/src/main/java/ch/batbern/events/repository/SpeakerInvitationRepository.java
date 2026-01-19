package ch.batbern.events.repository;

import ch.batbern.events.domain.InvitationStatus;
import ch.batbern.events.domain.SpeakerInvitation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for SpeakerInvitation entity - Story 6.1.
 *
 * Provides CRUD operations and custom queries for speaker invitations.
 * ADR-003 compliant: queries by username/event_code (meaningful IDs).
 */
@Repository
public interface SpeakerInvitationRepository extends JpaRepository<SpeakerInvitation, UUID> {

    /**
     * Find invitation by unique response token.
     * Used for passwordless speaker response.
     */
    Optional<SpeakerInvitation> findByResponseToken(String responseToken);

    /**
     * Find all invitations for a speaker.
     */
    List<SpeakerInvitation> findByUsername(String username);

    /**
     * Find all invitations for an event.
     */
    List<SpeakerInvitation> findByEventCode(String eventCode);

    /**
     * Find invitations for an event with pagination.
     */
    Page<SpeakerInvitation> findByEventCode(String eventCode, Pageable pageable);

    /**
     * Find invitation for a specific speaker and event.
     */
    Optional<SpeakerInvitation> findByUsernameAndEventCode(String username, String eventCode);

    /**
     * Check if an active invitation exists for speaker and event (by username).
     * An invitation is considered "active" if it's pending response (PENDING/SENT/OPENED)
     * or has been ACCEPTED. DECLINED and EXPIRED invitations allow re-invitation.
     * Note: Uses lowercase values to match database storage (via converters).
     */
    @Query("""
        SELECT COUNT(i) > 0 FROM SpeakerInvitation i
        WHERE i.username = :username
          AND i.eventCode = :eventCode
          AND i.invitationStatus NOT IN ('expired')
          AND NOT (i.invitationStatus = 'responded' AND i.responseType = 'declined')
        """)
    boolean existsActiveInvitation(@Param("username") String username, @Param("eventCode") String eventCode);

    /**
     * Check if an active invitation exists for speaker pool entry and event.
     * Used for invitations to speakers without user accounts.
     * An invitation is considered "active" if it's pending response (PENDING/SENT/OPENED)
     * or has been ACCEPTED. DECLINED and EXPIRED invitations allow re-invitation.
     * Note: Uses lowercase values to match database storage (via converters).
     */
    @Query("""
        SELECT COUNT(i) > 0 FROM SpeakerInvitation i
        WHERE i.speakerPoolId = :speakerPoolId
          AND i.eventCode = :eventCode
          AND i.invitationStatus NOT IN ('expired')
          AND NOT (i.invitationStatus = 'responded' AND i.responseType = 'declined')
        """)
    boolean existsActiveInvitationBySpeakerPoolId(@Param("speakerPoolId") UUID speakerPoolId,
                                                   @Param("eventCode") String eventCode);

    /**
     * Find invitation by speaker pool ID and event code.
     */
    Optional<SpeakerInvitation> findBySpeakerPoolIdAndEventCode(UUID speakerPoolId, String eventCode);

    /**
     * Find invitations by status.
     */
    List<SpeakerInvitation> findByInvitationStatus(InvitationStatus status);

    /**
     * Find pending invitations that have expired.
     * Note: Uses lowercase values to match database storage (via converters).
     */
    @Query("""
        SELECT i FROM SpeakerInvitation i
        WHERE i.invitationStatus NOT IN ('responded', 'expired')
          AND i.expiresAt < :now
        """)
    List<SpeakerInvitation> findExpiredInvitations(@Param("now") Instant now);

    /**
     * Find invitations needing reminder (sent but not responded, approaching deadline).
     * Note: Uses lowercase values to match database storage (via converters).
     */
    @Query("""
        SELECT i FROM SpeakerInvitation i
        WHERE i.invitationStatus = 'sent'
          AND i.expiresAt > :now
          AND i.expiresAt < :reminderThreshold
        """)
    List<SpeakerInvitation> findInvitationsNeedingReminder(
            @Param("now") Instant now,
            @Param("reminderThreshold") Instant reminderThreshold);
}
