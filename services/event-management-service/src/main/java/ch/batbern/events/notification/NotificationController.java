package ch.batbern.events.notification;

import ch.batbern.events.notifications.api.generated.NotificationsApi;
import ch.batbern.events.notifications.dto.generated.DeleteNotificationResponse;
import ch.batbern.events.notifications.dto.generated.MarkAsReadResponse;
import ch.batbern.events.notifications.dto.generated.NotificationCountResponse;
import ch.batbern.events.notifications.dto.generated.NotificationPagination;
import ch.batbern.events.notifications.dto.generated.NotificationResponse;
import ch.batbern.events.notifications.dto.generated.NotificationsResponse;
import ch.batbern.events.notifications.dto.generated.UpdateNotificationRequest;
import ch.batbern.events.notifications.dto.generated.UpdateNotificationsRequest;
import ch.batbern.events.security.SecurityContextHelper;
import ch.batbern.shared.api.DeterministicPaging;
import ch.batbern.shared.api.PaginationParams;
import ch.batbern.shared.api.PaginationUtils;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.data.domain.Page;
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
 * {@code GET} and {@code PATCH /notifications}, {@code GET /notifications/count},
 * {@code PATCH} and {@code DELETE /notifications/{id}}.
 *
 * <p>Story 4.10b: every operation works on the caller's own notifications. The recipient is the
 * signed-in user, never a parameter; a notification of another user is loaded scoped to the caller
 * and therefore answers 404 (one) or is ignored (many).
 */
@PreAuthorize("hasRole('ORGANIZER')")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class NotificationController implements NotificationsApi {

    private final NotificationRepository notificationRepository;
    private final SecurityContextHelper securityContextHelper;

    @Override
    public ResponseEntity<NotificationsResponse> listNotifications(
            String status,
            Integer page,
            Integer limit
    ) {
        // Parse pagination params (1-based, with defaults and validation)
        PaginationParams paginationParams = PaginationUtils.parseParams(page, limit);
        int pageNum = paginationParams.getPage();
        int pageSize = paginationParams.getLimit();

        // Convert to 0-based for Spring Pageable
        // id breaks createdAt ties (#1131)
        String username = securityContextHelper.getCurrentUsername();
        Pageable pageable = DeterministicPaging.pageRequest(pageNum - 1, pageSize, Sort.by("createdAt").descending());

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
    public ResponseEntity<NotificationCountResponse> getUnreadCount(String status) {
        String username = securityContextHelper.getCurrentUsername();
        long count = status != null && !status.isEmpty()
                ? notificationRepository.countByRecipientUsernameAndStatus(username, status.toUpperCase())
                : notificationRepository.countByRecipientUsernameAndStatus(username, "UNREAD");

        return ResponseEntity.ok(new NotificationCountResponse().count(count));
    }

    @Override
    public ResponseEntity<MarkAsReadResponse> updateNotification(UUID id, UpdateNotificationRequest request) {
        Notification notification = notificationRepository
                .findByIdAndRecipientUsername(id, securityContextHelper.getCurrentUsername())
                .orElseThrow(() -> new EntityNotFoundException("Notification not found"));

        Instant now = Instant.now();
        markRead(notification, now);
        notificationRepository.save(notification);

        return ResponseEntity.ok(new MarkAsReadResponse()
                .success(true)
                .markedCount(1)
                .updatedAt(now.atOffset(ZoneOffset.UTC)));
    }

    @Override
    public ResponseEntity<MarkAsReadResponse> updateNotifications(UpdateNotificationsRequest request) {
        List<Notification> notifications = notificationRepository.findAllByIdInAndRecipientUsername(
                request.getNotificationIds(), securityContextHelper.getCurrentUsername());

        Instant now = Instant.now();
        notifications.forEach(n -> markRead(n, now));
        notificationRepository.saveAll(notifications);

        return ResponseEntity.ok(new MarkAsReadResponse()
                .success(true)
                .markedCount(notifications.size())
                .updatedAt(now.atOffset(ZoneOffset.UTC)));
    }

    /** The only status change the spec allows today ({@code NotificationStatusChange} = READ). */
    private static void markRead(Notification notification, Instant now) {
        notification.setStatus("READ");
        notification.setReadAt(now);
    }

    @Override
    public ResponseEntity<DeleteNotificationResponse> deleteNotification(UUID id) {
        Notification notification = notificationRepository
                .findByIdAndRecipientUsername(id, securityContextHelper.getCurrentUsername())
                .orElseThrow(() -> new EntityNotFoundException("Notification not found"));

        notificationRepository.delete(notification);

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
