package ch.batbern.events.notification;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST API for Notifications
 * Story BAT-7: Notifications API Consolidation
 *
 * Endpoints:
 * - GET /api/v1/notifications - List notifications (AC1)
 * - PUT /api/v1/notifications/{id}/read - Mark as read (AC2)
 * - PUT /api/v1/notifications/batch-read - Bulk mark as read (AC2)
 * - GET /api/v1/notifications/history - Delivery history (AC4)
 * - DELETE /api/v1/notifications/{id} - Delete (AC5)
 * - DELETE /api/v1/notifications/batch-delete - Bulk delete (AC5)
 * - GET /api/v1/notifications/count - Get count (AC6)
 *
 * Note: Response structures match frontend API contract
 */
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final NotificationRepository notificationRepository;

    /**
     * AC1: List notifications with filtering and pagination
     * Returns custom response structure matching frontend API contract
     */
    @GetMapping
    public NotificationsResponse listNotifications(
            @RequestParam String username,
            @RequestParam(required = false) String status,
            Pageable pageable
    ) {
        Page<Notification> notifications = status != null
                ? notificationRepository.findByRecipientUsernameAndStatus(username, status.toUpperCase(), pageable)
                : notificationRepository.findByRecipientUsername(username, pageable);

        // Convert Spring Page to custom response structure
        List<NotificationResponse> data = notifications.getContent().stream()
                .map(NotificationResponse::fromEntity)
                .collect(Collectors.toList());

        PaginationMetadata pagination = PaginationMetadata.builder()
                .page(notifications.getNumber() + 1)  // Frontend uses 1-based indexing
                .limit(notifications.getSize())
                .totalItems(notifications.getTotalElements())
                .totalPages(notifications.getTotalPages())
                .build();

        return NotificationsResponse.builder()
                .data(data)
                .pagination(pagination)
                .build();
    }

    /**
     * AC6: Get notification count (unread or total)
     */
    @GetMapping("/count")
    public NotificationCountResponse getUnreadCount(
            @RequestParam String username,
            @RequestParam(defaultValue = "UNREAD") String status
    ) {
        long count = status != null && !status.isEmpty()
                ? notificationRepository.countByRecipientUsernameAndStatus(username, status.toUpperCase())
                : notificationRepository.countByRecipientUsernameAndStatus(username, "UNREAD");

        return NotificationCountResponse.builder().count(count).build();
    }

    /**
     * AC2: Mark single notification as read
     */
    @PutMapping("/{id}/read")
    public MarkAsReadResponse markAsRead(@PathVariable UUID id) {
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Notification not found"));

        Instant now = Instant.now();
        notification.setStatus("READ");
        notification.setReadAt(now);
        notificationRepository.save(notification);

        return MarkAsReadResponse.builder()
                .success(true)
                .markedCount(1)
                .updatedAt(now)
                .build();
    }

    /**
     * AC2: Mark multiple notifications as read (bulk operation)
     * Accepts request body matching frontend API contract
     */
    @PutMapping("/batch-read")
    public MarkAsReadResponse batchMarkAsRead(@RequestBody BatchOperationRequest request) {
        List<Notification> notifications = notificationRepository.findAllById(request.getNotificationIds());

        Instant now = Instant.now();
        notifications.forEach(n -> {
            n.setStatus("READ");
            n.setReadAt(now);
        });

        notificationRepository.saveAll(notifications);

        return MarkAsReadResponse.builder()
                .success(true)
                .markedCount(notifications.size())
                .updatedAt(now)
                .build();
    }

    /**
     * AC4: Get delivery history by channel
     */
    @GetMapping("/history")
    public List<NotificationResponse> getDeliveryHistory(
            @RequestParam String username,
            @RequestParam String channel
    ) {
        List<Notification> history = notificationRepository
                .findByRecipientUsernameAndChannelOrderByCreatedAtDesc(username, channel.toUpperCase());

        return history.stream()
                .map(NotificationResponse::fromEntity)
                .toList();
    }

    /**
     * AC5: Delete single notification
     */
    @DeleteMapping("/{id}")
    public DeleteNotificationResponse deleteNotification(@PathVariable UUID id) {
        if (!notificationRepository.existsById(id)) {
            throw new EntityNotFoundException("Notification not found");
        }

        notificationRepository.deleteById(id);

        return DeleteNotificationResponse.builder()
                .success(true)
                .build();
    }

    /**
     * AC5: Delete multiple notifications (bulk operation)
     * Accepts request body matching frontend API contract
     */
    @DeleteMapping("/batch-delete")
    public DeleteNotificationResponse batchDelete(@RequestBody BatchOperationRequest request) {
        notificationRepository.deleteAllById(request.getNotificationIds());

        return DeleteNotificationResponse.builder()
                .success(true)
                .build();
    }

    /**
     * Exception handler for EntityNotFoundException
     * Returns 404 instead of 500
     */
    @ExceptionHandler(EntityNotFoundException.class)
    @ResponseStatus(org.springframework.http.HttpStatus.NOT_FOUND)
    public void handleEntityNotFound() {
        // Return 404 NOT FOUND
    }
}
