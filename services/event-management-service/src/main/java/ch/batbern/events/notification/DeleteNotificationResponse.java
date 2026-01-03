package ch.batbern.events.notification;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response for delete operations
 * Story BAT-7: Matches frontend API contract
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeleteNotificationResponse {
    private boolean success;
}
