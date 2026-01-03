package ch.batbern.events.service.slotassignment;

import ch.batbern.events.domain.Session;
import lombok.Builder;
import lombok.Data;

/**
 * Result of matching a speaker with a session slot
 * Story BAT-11 (5.7): Slot Assignment & Progressive Publishing
 */
@Data
@Builder
public class SlotMatchResult {
    private Session session;
    private int matchScore; // 0-100 percentage match
    private String matchReason; // Why this slot is a good/poor match
}
