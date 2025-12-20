package ch.batbern.events.dto;

import ch.batbern.shared.types.SpeakerWorkflowState;
import lombok.Data;

import java.util.Map;

@Data
public class StatusSummaryResponse {
    private String eventCode;
    private Map<SpeakerWorkflowState, Long> statusCounts;
    private long totalSpeakers;
    private long acceptedCount;
    private long declinedCount;
    private long pendingCount;
    private double acceptanceRate;
    private int minSlotsRequired;
    private int maxSlotsAllowed;
    private boolean thresholdMet;
    private boolean overflowDetected;
}
