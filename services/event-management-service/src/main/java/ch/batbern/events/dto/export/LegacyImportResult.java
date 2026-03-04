package ch.batbern.events.dto.export;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Result of a legacy BAT JSON import operation.
 * Story 10.20: AC3
 */
@Data
@Builder
public class LegacyImportResult {

    private ImportCounts imported;
    private List<String> skipped;
    private List<String> errors;

    @Data
    @Builder
    public static class ImportCounts {
        private int events;
        private int sessions;
        private int speakers;
        private int companies;
        private int attendees;
    }
}
