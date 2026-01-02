package ch.batbern.events.notification;

import ch.batbern.events.client.UserApiClient;
import ch.batbern.events.domain.Event;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.shared.service.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for NotificationService
 * Story BAT-7: Notifications API Consolidation
 *
 * RED PHASE (TDD): These tests will FAIL until NotificationService is implemented.
 *
 * Tests cover:
 * - Email notification creation and sending
 * - User preference checking
 * - Hybrid storage strategy (email creates rows, in-app dynamic queries)
 * - AWS SES integration
 * - Error handling
 */
@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private EmailService emailService;

    @Mock
    private UserApiClient userApiClient;

    @Mock
    private EventRepository eventRepository;

    @Mock
    private ch.batbern.events.repository.RegistrationRepository registrationRepository;

    @Mock
    private org.springframework.messaging.simp.SimpMessagingTemplate messagingTemplate;

    private NotificationService notificationService;

    private String testUsername;
    private String testEventCode;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(
                notificationRepository,
                emailService,
                userApiClient,
                eventRepository,
                registrationRepository,
                messagingTemplate
        );

        testUsername = "john.doe";
        testEventCode = "BATbern123";
    }

    /**
     * Email notifications should create audit trail record
     */
    @Test
    void should_createNotificationRecord_when_sendingEmail() {
        // Given
        NotificationRequest request = NotificationRequest.builder()
                .recipientUsername(testUsername)
                .eventCode(testEventCode)
                .type("EVENT_PUBLISHED")
                .channel("EMAIL")
                .subject("Test Subject")
                .body("Test Body")
                .build();

        UserPreferences prefs = new UserPreferences();
        prefs.setEmailNotificationsEnabled(true);

        when(userApiClient.getPreferences(testUsername)).thenReturn(prefs);
        when(userApiClient.getEmailByUsername(testUsername)).thenReturn("john.doe@example.com");
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> {
            Notification n = invocation.getArgument(0);
            n.setId(UUID.randomUUID());
            return n;
        });
        doNothing().when(emailService).sendHtmlEmail(anyString(), anyString(), anyString());

        // When
        notificationService.createAndSendEmailNotification(request);

        // Then
        verify(notificationRepository, times(2)).save(any(Notification.class));
        verify(emailService).sendHtmlEmail(eq("john.doe@example.com"), eq("Test Subject"), anyString());
    }

    /**
     * Should skip notification when user opted out
     */
    @Test
    void should_skipNotification_when_userOptedOut() {
        // Given
        NotificationRequest request = NotificationRequest.builder()
                .recipientUsername(testUsername)
                .type("EVENT_PUBLISHED")
                .channel("EMAIL")
                .build();

        UserPreferences prefs = new UserPreferences();
        prefs.setEmailNotificationsEnabled(false);

        when(userApiClient.getPreferences(testUsername)).thenReturn(prefs);

        // When
        notificationService.createAndSendEmailNotification(request);

        // Then
        verify(notificationRepository, never()).save(any(Notification.class));
        verify(emailService, never()).sendHtmlEmail(anyString(), anyString(), anyString());
    }

    /**
     * Should update status to SENT when email delivery succeeds
     */
    @Test
    void should_updateStatusToSent_when_emailDeliverySucceeds() {
        // Given
        NotificationRequest request = NotificationRequest.builder()
                .recipientUsername(testUsername)
                .type("EVENT_PUBLISHED")
                .channel("EMAIL")
                .subject("Test")
                .body("Test")
                .build();

        UserPreferences prefs = new UserPreferences();
        prefs.setEmailNotificationsEnabled(true);

        AtomicReference<Notification> savedNotification = new AtomicReference<>();

        when(userApiClient.getPreferences(testUsername)).thenReturn(prefs);
        when(userApiClient.getEmailByUsername(testUsername)).thenReturn("john.doe@example.com");
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> {
            Notification n = invocation.getArgument(0);
            if (n.getId() == null) {
                n.setId(UUID.randomUUID());
            }
            savedNotification.set(n);
            return n;
        });
        doNothing().when(emailService).sendHtmlEmail(anyString(), anyString(), anyString());

        // When
        notificationService.createAndSendEmailNotification(request);

        // Then
        assertThat(savedNotification.get().getStatus()).isEqualTo("SENT");
        assertThat(savedNotification.get().getSentAt()).isNotNull();
    }

    /**
     * Should update status to FAILED when email delivery fails
     */
    @Test
    void should_updateStatusToFailed_when_emailDeliveryFails() {
        // Given
        NotificationRequest request = NotificationRequest.builder()
                .recipientUsername(testUsername)
                .type("EVENT_PUBLISHED")
                .channel("EMAIL")
                .subject("Test")
                .body("Test")
                .build();

        UserPreferences prefs = new UserPreferences();
        prefs.setEmailNotificationsEnabled(true);

        AtomicReference<Notification> savedNotification = new AtomicReference<>();

        when(userApiClient.getPreferences(testUsername)).thenReturn(prefs);
        when(userApiClient.getEmailByUsername(testUsername)).thenReturn("john.doe@example.com");
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> {
            Notification n = invocation.getArgument(0);
            if (n.getId() == null) {
                n.setId(UUID.randomUUID());
            }
            savedNotification.set(n);
            return n;
        });
        doThrow(new RuntimeException("AWS SES error")).when(emailService).sendHtmlEmail(anyString(), anyString(), anyString());

        // When
        notificationService.createAndSendEmailNotification(request);

        // Then
        assertThat(savedNotification.get().getStatus()).isEqualTo("FAILED");
        assertThat(savedNotification.get().getFailedAt()).isNotNull();
        assertThat(savedNotification.get().getFailureReason()).contains("AWS SES error");
    }

    /**
     * In-app notifications should query dynamically (no rows created)
     */
    @Test
    void should_queryDynamically_when_gettingInAppNotifications() {
        // Given
        Instant lastLogin = Instant.now().minusSeconds(3600); // 1 hour ago

        Event newEvent = Event.builder()
                .eventCode(testEventCode)
                .title("Test Event")
                .publishedAt(Instant.now().minusSeconds(1800)) // 30 min ago
                .build();

        when(userApiClient.getLastLogin(testUsername)).thenReturn(lastLogin);
        when(eventRepository.findByPublishedAtAfter(lastLogin)).thenReturn(List.of(newEvent));

        // When
        List<InAppNotification> notifications = notificationService.getInAppNotifications(testUsername);

        // Then
        assertThat(notifications).hasSize(1);
        assertThat(notifications.get(0).getEventCode()).isEqualTo(testEventCode);
        assertThat(notifications.get(0).getType()).isEqualTo("EVENT_PUBLISHED");

        // Verify NO database writes occurred
        verify(notificationRepository, never()).save(any(Notification.class));
    }
}
