package ch.batbern.events.dto;

import jakarta.validation.constraints.Email;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for partially updating a registration (PATCH)
 * Story 1.15a.1: Events API Consolidation - AC12
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PatchRegistrationRequest {

    private String attendeeName;

    @Email(message = "Valid email is required")
    private String attendeeEmail;

    private String status;

    private String registrationDate; // ISO-8601 format
}
