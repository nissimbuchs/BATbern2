package ch.batbern.events.notification;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response wrapper for notification list API
 * Story BAT-7: Matches frontend API contract
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationsResponse {
    private List<NotificationResponse> data;
    private PaginationMetadata pagination;
}
