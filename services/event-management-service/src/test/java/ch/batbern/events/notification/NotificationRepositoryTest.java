package ch.batbern.events.notification;

import ch.batbern.events.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for NotificationRepository
 * Story BAT-7: Notifications API Consolidation
 *
 * RED PHASE (TDD): These tests will FAIL until NotificationRepository is implemented.
 *
 * Uses real PostgreSQL (Testcontainers) to verify:
 * - Query methods work correctly
 * - Indexes are being used
 * - ADR-003 compliance (meaningful IDs work)
 */
@Transactional
class NotificationRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private NotificationRepository notificationRepository;

    private String testUsername;
    private String testEventCode;

    @BeforeEach
    void setUp() {
        notificationRepository.deleteAll();
        testUsername = "john.doe";
        testEventCode = "BATbern123";
    }

    /**
     * Should find notifications by recipient username
     */
    @Test
    void should_findNotifications_when_queryingByRecipientUsername() {
        // Given
        Notification notification = Notification.builder()
                .recipientUsername(testUsername)
                .eventCode(testEventCode)
                .notificationType("EVENT_PUBLISHED")
                .channel("EMAIL")
                .subject("Test")
                .body("Test body")
                .status("PENDING")
                .build();

        notificationRepository.save(notification);

        Pageable pageable = PageRequest.of(0, 20);

        // When
        Page<Notification> result = notificationRepository.findByRecipientUsername(testUsername, pageable);

        // Then
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getRecipientUsername()).isEqualTo(testUsername);
    }

    /**
     * Should filter by username and status
     */
    @Test
    void should_filterByStatus_when_queryingByRecipientUsernameAndStatus() {
        // Given
        Notification sent = Notification.builder()
                .recipientUsername(testUsername)
                .notificationType("TEST")
                .channel("EMAIL")
                .subject("Sent")
                .body("Body")
                .status("SENT")
                .build();

        Notification pending = Notification.builder()
                .recipientUsername(testUsername)
                .notificationType("TEST")
                .channel("EMAIL")
                .subject("Pending")
                .body("Body")
                .status("PENDING")
                .build();

        notificationRepository.saveAll(List.of(sent, pending));

        Pageable pageable = PageRequest.of(0, 20);

        // When
        Page<Notification> result = notificationRepository.findByRecipientUsernameAndStatus(
                testUsername, "SENT", pageable
        );

        // Then
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getStatus()).isEqualTo("SENT");
    }

    /**
     * Should count notifications by status
     */
    @Test
    void should_countNotifications_when_queryingByRecipientUsernameAndStatus() {
        // Given
        notificationRepository.saveAll(List.of(
                createNotification(testUsername, "PENDING"),
                createNotification(testUsername, "PENDING"),
                createNotification(testUsername, "SENT")
        ));

        // When
        long count = notificationRepository.countByRecipientUsernameAndStatus(testUsername, "PENDING");

        // Then
        assertThat(count).isEqualTo(2);
    }

    /**
     * Should find delivery history by channel
     */
    @Test
    void should_findDeliveryHistory_when_queryingByUsernameAndChannel() {
        // Given
        Notification email = createNotificationWithChannel(testUsername, "EMAIL");
        Notification sms = createNotificationWithChannel(testUsername, "SMS");

        notificationRepository.saveAll(List.of(email, sms));

        // When
        List<Notification> result = notificationRepository
                .findByRecipientUsernameAndChannelOrderByCreatedAtDesc(testUsername, "EMAIL");

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getChannel()).isEqualTo("EMAIL");
    }

    /**
     * Should find failed deliveries before timestamp
     */
    @Test
    void should_findFailedDeliveries_when_queryingByStatusAndCreatedAtBefore() {
        // Given
        Instant oneDayAgo = Instant.now().minusSeconds(86400);

        Notification oldFailed = Notification.builder()
                .recipientUsername(testUsername)
                .notificationType("TEST")
                .channel("EMAIL")
                .subject("Old")
                .body("Body")
                .status("FAILED")
                .createdAt(oneDayAgo)
                .updatedAt(oneDayAgo)
                .build();

        notificationRepository.save(oldFailed);

        // When
        List<Notification> result = notificationRepository.findByStatusAndCreatedAtBefore(
                "FAILED",
                Instant.now()
        );

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isEqualTo("FAILED");
    }

    // Helper methods

    private Notification createNotification(String username, String status) {
        return Notification.builder()
                .recipientUsername(username)
                .notificationType("TEST")
                .channel("EMAIL")
                .subject("Test")
                .body("Body")
                .status(status)
                .build();
    }

    private Notification createNotificationWithChannel(String username, String channel) {
        return Notification.builder()
                .recipientUsername(username)
                .notificationType("TEST")
                .channel(channel)
                .subject("Test")
                .body("Body")
                .status("SENT")
                .build();
    }
}
