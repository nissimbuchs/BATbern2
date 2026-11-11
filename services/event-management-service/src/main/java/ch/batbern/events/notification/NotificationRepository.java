package ch.batbern.events.notification;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Repository for Notification entity
 * Story BAT-7: Notifications API Consolidation
 *
 * Provides:
 * - Basic CRUD operations
 * - Pagination support
 * - Filtering by username and status
 * - Unread count queries
 */
@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    /**
     * Find all notifications for a specific user with pagination
     */
    Page<Notification> findByRecipientUsername(String recipientUsername, Pageable pageable);

    /**
     * Find notifications by user and status with pagination
     */
    Page<Notification> findByRecipientUsernameAndStatus(String recipientUsername, String status, Pageable pageable);

    /**
     * Count unread notifications for a user
     */
    long countByRecipientUsernameAndStatus(String recipientUsername, String status);

    /**
     * One notification, only if it belongs to the recipient (Story 4.10b): a caller never reaches
     * another user's notification by id.
     */
    java.util.Optional<Notification> findByIdAndRecipientUsername(UUID id, String recipientUsername);

    /**
     * The recipient's notifications among the given ids; ids of other users are left out.
     */
    java.util.List<Notification> findAllByIdInAndRecipientUsername(
            java.util.Collection<UUID> ids, String recipientUsername);

    /**
     * Find notifications by status created before a certain time (for cleanup jobs)
     */
    java.util.List<Notification> findByStatusAndCreatedAtBefore(String status, java.time.Instant createdAt);

    /**
     * Bulk-dismiss all unread notifications for an event being archived.
     * Story 10.18: Event Archival Task &amp; Notification Cleanup (AC4).
     *
     * @param eventCode the event code (e.g. "BATbern42")
     * @return number of notifications updated
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Notification n SET n.status = 'READ' WHERE n.eventCode = :eventCode "
            + "AND n.status != 'READ'")
    int dismissNotificationsForEvent(@Param("eventCode") String eventCode);

    /**
     * Delete every notification of an event. Called when the event is deleted: notifications
     * reference the event by code without a foreign key, so nothing cascades (#1068).
     */
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM Notification n WHERE n.eventCode = :eventCode")
    int deleteByEventCode(@Param("eventCode") String eventCode);
}
