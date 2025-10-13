package ch.batbern.events.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for creating a new registration
 * Story 1.15a.1: Events API Consolidation - AC12
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateRegistrationRequest {

    @NotBlank(message = "Attendee ID is required")
    private String attendeeId;

    @NotBlank(message = "Attendee name is required")
    private String attendeeName;

    @NotBlank(message = "Attendee email is required")
    @Email(message = "Valid email is required")
    private String attendeeEmail;

    @NotBlank(message = "Status is required")
    private String status;

    @NotNull(message = "Registration date is required")
    private String registrationDate; // ISO-8601 format
}
