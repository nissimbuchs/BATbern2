package ch.batbern.events.dto;

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
    /** Story 11.B.3 AC6 — derived flag, sessionId-based fallback (see SpeakerPoolEntry for strict definition). */
    private Boolean isSlotAssigned;
    /** Story 11.B.3 AC6 — derived flag: currentStatus == QUALITY_REVIEWED AND isSlotAssigned. */
    private Boolean isPublishable;
}
