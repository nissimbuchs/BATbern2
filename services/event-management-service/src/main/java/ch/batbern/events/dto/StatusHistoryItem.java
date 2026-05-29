package ch.batbern.events.dto;

import ch.batbern.shared.types.SpeakerWorkflowState;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
public class StatusHistoryItem {

    /**
     * Discriminates speaker_status_history rows (STATUS_CHANGE) from synthesised
     * session_content_history rows (CONTENT_REJECTED). Content rejection is intentionally
     * not a state transition (ADR-009), so its audit trail lives in session_content_history;
     * we surface it on this feed so the organizer drawer's History tab shows the feedback.
     */
    public enum Kind { STATUS_CHANGE, CONTENT_REJECTED }

    private UUID id;
    private Kind kind = Kind.STATUS_CHANGE;
    private SpeakerWorkflowState previousStatus;
    private SpeakerWorkflowState newStatus;
    private String changedByUsername;
    private String changeReason;
    private Instant changedAt;
}
