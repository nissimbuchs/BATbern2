package ch.batbern.speakers.dto;

import ch.batbern.shared.types.SpeakerWorkflowState;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
public class SpeakerStatusResponse {
    private UUID speakerId;
    private String eventCode;
    private SpeakerWorkflowState currentStatus;
    private SpeakerWorkflowState previousStatus;
    private String changedByUsername;
    private String changeReason;
    private Instant changedAt;
}
