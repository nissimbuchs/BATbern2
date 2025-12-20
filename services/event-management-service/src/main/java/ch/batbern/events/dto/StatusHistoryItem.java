package ch.batbern.events.dto;

import ch.batbern.shared.types.SpeakerWorkflowState;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
public class StatusHistoryItem {
    private UUID id;
    private SpeakerWorkflowState previousStatus;
    private SpeakerWorkflowState newStatus;
    private String changedByUsername;
    private String changeReason;
    private Instant changedAt;
}
