package ch.batbern.events.service.slotassignment;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Response DTO for conflict analysis
 * Story BAT-11 (5.7): Slot Assignment & Progressive Publishing
 */
@Data
@Builder
public class ConflictAnalysisResponse {
    private boolean hasConflicts;
    private int conflictCount;
    private List<ConflictDetail> conflicts;

    @Data
    @Builder
    public static class ConflictDetail {
        private String sessionSlug;
        private ConflictType conflictType;
        private ConflictSeverity severity;
        private List<String> affectedSessions;
        private TimeRange timeRange;
        private String resolution;
    }

    @Data
    @Builder
    public static class TimeRange {
        private String start; // ISO-8601 format
        private String end;
    }
}
