package ch.batbern.events.notification;

import ch.batbern.events.notifications.dto.generated.NotificationResponse;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Maps the {@link Notification} JPA entity to the generated {@link NotificationResponse} wire DTO.
 *
 * <p>Replaces the former hand {@code NotificationResponse.fromEntity} static factory after the
 * contract-first wiring (Story BAT-7 follow-up). {@code Instant} fields are emitted as UTC
 * {@code OffsetDateTime} so the wire stays byte-identical ({@code …Z}). Null fields are preserved
 * (the generated DTO carries no {@code @JsonInclude}, matching the prior hand DTO).
 */
final class NotificationMapper {

    private NotificationMapper() {
    }

    static NotificationResponse toResponse(Notification n) {
        return new NotificationResponse()
                .id(n.getId())
                .recipientUsername(n.getRecipientUsername())
                .eventCode(n.getEventCode())
                .notificationType(n.getNotificationType())
                .channel(n.getChannel())
                .priority(n.getPriority())
                .subject(n.getSubject())
                .body(n.getBody())
                .status(n.getStatus())
                .sentAt(toOffset(n.getSentAt()))
                .readAt(toOffset(n.getReadAt()))
                .failedAt(toOffset(n.getFailedAt()))
                .failureReason(n.getFailureReason())
                .metadata(n.getMetadata())
                .createdAt(toOffset(n.getCreatedAt()))
                .updatedAt(toOffset(n.getUpdatedAt()));
    }

    private static OffsetDateTime toOffset(Instant instant) {
        return instant != null ? instant.atOffset(ZoneOffset.UTC) : null;
    }
}
