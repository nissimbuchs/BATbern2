package ch.batbern.events.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for ensuring a Speaker entity exists.
 * Story 6.3: Speaker Account Creation and Linking
 *
 * <p>This endpoint is called by the PostConfirmation Lambda after linking
 * SpeakerPool entries to a newly registered user. It ensures a Speaker entity
 * exists for the user to manage their speaker profile.
 *
 * <p>The operation is idempotent - calling it multiple times for the same
 * username will not create duplicate speakers.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EnsureSpeakerRequest {

    @NotBlank(message = "Username is required")
    private String username; // User's public identifier (ADR-003)
}
