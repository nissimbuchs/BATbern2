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
 * <p>
 * Story 1.15a.1: Events API Consolidation - AC12
 * Story 1.16.2: Uses attendeeUsername (firstname.lastname) instead of UUID
 * Story 2.2a: Anonymous Event Registration (ADR-005)
 * <p>
 * For anonymous registration, provide firstName, lastName, and email.
 * The username will be generated automatically by the User Management Service.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateRegistrationRequest {

    @NotBlank(message = "Attendee first name is required")
    private String attendeeFirstName;

    @NotBlank(message = "Attendee last name is required")
    private String attendeeLastName;

    @NotBlank(message = "Attendee email is required")
    @Email(message = "Valid email is required")
    private String attendeeEmail;

    @NotBlank(message = "Status is required")
    private String status;

    @NotNull(message = "Registration date is required")
    private String registrationDate; // ISO-8601 format
}
