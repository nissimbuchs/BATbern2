package ch.batbern.events.notification;

import ch.batbern.events.notifications.api.generated.NotificationsApi;
import ch.batbern.events.notifications.dto.generated.BatchOperationRequest;
import ch.batbern.events.notifications.dto.generated.DeleteNotificationResponse;
import ch.batbern.events.notifications.dto.generated.MarkAsReadResponse;
import ch.batbern.events.notifications.dto.generated.NotificationCountResponse;
import ch.batbern.events.notifications.dto.generated.NotificationPagination;
import ch.batbern.events.notifications.dto.generated.NotificationResponse;
import ch.batbern.events.notifications.dto.generated.NotificationsResponse;
import ch.batbern.shared.api.PaginationParams;
import ch.batbern.shared.api.PaginationUtils;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST API for Notifications — Story BAT-7.
 *
 * <p>Contract-first: implements the generated {@link NotificationsApi} (spec
 * {@code event-notifications-api.openapi.yml}). Endpoints (under {@code /api/v1}):
 * {@code GET /notifications}, {@code GET /notifications/count}, {@code PUT /notifications/{id}/read},
 * {@code PUT /notifications/batch-read}, {@code GET /notifications/history},
 * {@code DELETE /notifications/{id}}, {@code DELETE /notifications/batch-delete}.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class NotificationController implements NotificationsApi {

    private final NotificationRepository notificationRepository;

    @Override
    public ResponseEntity<NotificationsResponse> listNotifications(
            String username,
            String status,
            Integer page,
            Integer limit
    ) {
        // Parse pagination params (1-based, with defaults and validation)
        PaginationParams paginationParams = PaginationUtils.parseParams(page, limit);
        int pageNum = paginationParams.getPage();
        int pageSize = paginationParams.getLimit();

        // Convert to 0-based for Spring Pageable
        Pageable pageable = PageRequest.of(pageNum - 1, pageSize, Sort.by("createdAt").descending());

        Page<Notification> notifications = status != null
                ? notificationRepository.findByRecipientUsernameAndStatus(username, status.toUpperCase(), pageable)
                : notificationRepository.findByRecipientUsername(username, pageable);

        List<NotificationResponse> data = notifications.getContent().stream()
                .map(NotificationMapper::toResponse)
                .collect(Collectors.toList());

        NotificationPagination pagination = new NotificationPagination()
                .page(pageNum)
                .limit(pageSize)
                .totalItems(notifications.getTotalElements())
                .totalPages(notifications.getTotalPages());

        return ResponseEntity.ok(new NotificationsResponse()
                .data(data)
                .pagination(pagination));
    }

    @Override
    public ResponseEntity<NotificationCountResponse> getUnreadCount(
            String username,
            String status
    ) {
        long count = status != null && !status.isEmpty()
                ? notificationRepository.countByRecipientUsernameAndStatus(username, status.toUpperCase())
                : notificationRepository.countByRecipientUsernameAndStatus(username, "UNREAD");

        return ResponseEntity.ok(new NotificationCountResponse().count(count));
    }

    @Override
    public ResponseEntity<MarkAsReadResponse> markAsRead(UUID id) {
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Notification not found"));

        Instant now = Instant.now();
        notification.setStatus("READ");
        notification.setReadAt(now);
        notificationRepository.save(notification);

        return ResponseEntity.ok(new MarkAsReadResponse()
                .success(true)
                .markedCount(1)
                .updatedAt(now.atOffset(ZoneOffset.UTC)));
    }

    @Override
    public ResponseEntity<MarkAsReadResponse> batchMarkAsRead(BatchOperationRequest request) {
        List<Notification> notifications = notificationRepository.findAllById(request.getNotificationIds());

        Instant now = Instant.now();
        notifications.forEach(n -> {
            n.setStatus("READ");
            n.setReadAt(now);
        });

        notificationRepository.saveAll(notifications);

        return ResponseEntity.ok(new MarkAsReadResponse()
                .success(true)
                .markedCount(notifications.size())
                .updatedAt(now.atOffset(ZoneOffset.UTC)));
    }

    @Override
    public ResponseEntity<List<NotificationResponse>> getDeliveryHistory(
            String username,
            String channel
    ) {
        List<NotificationResponse> history = notificationRepository
                .findByRecipientUsernameAndChannelOrderByCreatedAtDesc(username, channel.toUpperCase())
                .stream()
                .map(NotificationMapper::toResponse)
                .toList();

        return ResponseEntity.ok(history);
    }

    @Override
    public ResponseEntity<DeleteNotificationResponse> deleteNotification(UUID id) {
        if (!notificationRepository.existsById(id)) {
            throw new EntityNotFoundException("Notification not found");
        }

        notificationRepository.deleteById(id);

        return ResponseEntity.ok(new DeleteNotificationResponse().success(true));
    }

    @Override
    public ResponseEntity<DeleteNotificationResponse> batchDelete(BatchOperationRequest request) {
        notificationRepository.deleteAllById(request.getNotificationIds());

        return ResponseEntity.ok(new DeleteNotificationResponse().success(true));
    }

    /**
     * Returns 404 (instead of 500) when a notification is missing.
     */
    @ExceptionHandler(EntityNotFoundException.class)
    @ResponseStatus(org.springframework.http.HttpStatus.NOT_FOUND)
    public void handleEntityNotFound() {
        // Return 404 NOT FOUND
    }
}
