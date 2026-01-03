package ch.batbern.events.notification;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Response for mark-as-read operations
 * Story BAT-7: Matches frontend API contract
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarkAsReadResponse {
    private boolean success;
    private int markedCount;
    private Instant updatedAt;
}
