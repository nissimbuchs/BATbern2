package ch.batbern.events.dto;

import ch.batbern.events.domain.TaskTemplate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for task template (Story 5.5 AC19, AC26).
 *
 * Returns task template details including whether it's a default or custom template.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskTemplateResponse {

    private UUID id;
    private String name;
    private String triggerState;
    private String dueDateType;
    private Integer dueDateOffsetDays;
    private Boolean isDefault;
    private String createdByUsername;
    private Instant createdAt;
    private Instant updatedAt;

    /**
     * Convert TaskTemplate entity to response DTO.
     */
    public static TaskTemplateResponse fromEntity(TaskTemplate template) {
        return TaskTemplateResponse.builder()
                .id(template.getId())
                .name(template.getName())
                .triggerState(template.getTriggerState())
                .dueDateType(template.getDueDateType())
                .dueDateOffsetDays(template.getDueDateOffsetDays())
                .isDefault(template.getIsDefault())
                .createdByUsername(template.getCreatedByUsername())
                .createdAt(template.getCreatedAt())
                .updatedAt(template.getUpdatedAt())
                .build();
    }
}
