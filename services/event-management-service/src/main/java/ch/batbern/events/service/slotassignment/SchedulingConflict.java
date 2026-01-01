package ch.batbern.events.service.slotassignment;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Represents a scheduling conflict detected during slot assignment
 * Story BAT-11 (5.7): Slot Assignment & Progressive Publishing
 */
@Data
@Builder
public class SchedulingConflict {
    private ConflictType conflictType;
    private ConflictSeverity severity;
    private String message;
    private List<UUID> conflictingSessionIds;
    private String conflictingSessionSlug; // Slug of the conflicting session for error responses
    private Instant conflictStartTime;
    private Instant conflictEndTime;
    private String resolution; // Suggested resolution
}
