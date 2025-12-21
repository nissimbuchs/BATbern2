package ch.batbern.events.dto;

import ch.batbern.events.domain.EventTask;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for event task (Story 5.5 AC24-25).
 *
 * Returns event task details including status, assignment, and completion tracking.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventTaskResponse {

    private UUID id;
    private UUID eventId;
    private UUID templateId;
    private String taskName;
    private String triggerState;
    private Instant dueDate;
    private String assignedOrganizerUsername;
    private String status;
    private String notes;
    private Instant completedDate;
    private String completedByUsername;
    private Instant createdAt;
    private Instant updatedAt;

    /**
     * Convert EventTask entity to response DTO.
     */
    public static EventTaskResponse fromEntity(EventTask task) {
        return EventTaskResponse.builder()
                .id(task.getId())
                .eventId(task.getEventId())
                .templateId(task.getTemplateId())
                .taskName(task.getTaskName())
                .triggerState(task.getTriggerState())
                .dueDate(task.getDueDate())
                .assignedOrganizerUsername(task.getAssignedOrganizerUsername())
                .status(task.getStatus())
                .notes(task.getNotes())
                .completedDate(task.getCompletedDate())
                .completedByUsername(task.getCompletedByUsername())
                .createdAt(task.getCreatedAt())
                .updatedAt(task.getUpdatedAt())
                .build();
    }
}
