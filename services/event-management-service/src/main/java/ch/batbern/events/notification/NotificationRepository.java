package ch.batbern.events.notification;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
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
     * Find delivery history for a user by channel
     */
    java.util.List<Notification> findByRecipientUsernameAndChannelOrderByCreatedAtDesc(
            String recipientUsername, String channel);

    /**
     * Find notifications by status created before a certain time (for cleanup jobs)
     */
    java.util.List<Notification> findByStatusAndCreatedAtBefore(String status, java.time.Instant createdAt);
}
