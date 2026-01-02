package ch.batbern.events.notification;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Response DTO for notification API
 * Story BAT-7: Notifications API Consolidation
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationResponse {
    private UUID id;
    private String recipientUsername;
    private String eventCode;
    private String notificationType;
    private String channel;
    private String priority;
    private String subject;
    private String body;
    private String status;
    private Instant sentAt;
    private Instant readAt;
    private Instant failedAt;
    private String failureReason;
    private Map<String, Object> metadata;
    private Instant createdAt;
    private Instant updatedAt;

    public static NotificationResponse fromEntity(Notification notification) {
        return NotificationResponse.builder()
                .id(notification.getId())
                .recipientUsername(notification.getRecipientUsername())
                .eventCode(notification.getEventCode())
                .notificationType(notification.getNotificationType())
                .channel(notification.getChannel())
                .priority(notification.getPriority())
                .subject(notification.getSubject())
                .body(notification.getBody())
                .status(notification.getStatus())
                .sentAt(notification.getSentAt())
                .readAt(notification.getReadAt())
                .failedAt(notification.getFailedAt())
                .failureReason(notification.getFailureReason())
                .metadata(notification.getMetadata())
                .createdAt(notification.getCreatedAt())
                .updatedAt(notification.getUpdatedAt())
                .build();
    }
}
