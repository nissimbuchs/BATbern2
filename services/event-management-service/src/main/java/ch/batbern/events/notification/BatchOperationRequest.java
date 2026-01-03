package ch.batbern.events.notification;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/**
 * Request body for batch operations (mark as read, delete)
 * Story BAT-7: Matches frontend API contract
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BatchOperationRequest {
    private List<UUID> notificationIds;
}
