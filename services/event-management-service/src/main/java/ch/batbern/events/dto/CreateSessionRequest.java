package ch.batbern.events.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for creating a new session
 * Story 1.15a.1: Events API Consolidation - AC10
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateSessionRequest {

    @NotBlank(message = "Title is required")
    private String title;

    private String description;

    @NotBlank(message = "Session type is required")
    private String sessionType; // keynote, presentation, workshop, panel_discussion, networking, break, lunch

    @NotNull(message = "Start time is required")
    private String startTime; // ISO-8601 format

    @NotNull(message = "End time is required")
    private String endTime; // ISO-8601 format

    private String room;

    private Integer capacity;

    private String language;
}
