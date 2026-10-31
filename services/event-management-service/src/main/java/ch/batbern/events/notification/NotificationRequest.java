package ch.batbern.events.notification;

import ch.batbern.shared.mail.MailTrigger;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Request DTO for creating notifications
 * Story BAT-7: Notifications API Consolidation
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationRequest {
    private String recipientUsername;
    private String eventCode;
    private String type;
    private String channel;
    private String priority;
    private String subject;
    private String body;
    private Map<String, Object> metadata;
    /** Story 16.1a: the run this notification belongs to; null = a mail on its own. */
    private MailTrigger mailTrigger;
}
