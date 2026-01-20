package ch.batbern.events.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for manually linking a SpeakerPool entry to a user account.
 * Story 6.3: Speaker Account Creation and Linking
 *
 * <p>This endpoint allows organizers to manually link a speaker pool entry
 * to a registered user account when automatic linking (via email match
 * during registration) was not possible.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LinkSpeakerPoolRequest {

    @NotBlank(message = "Username is required")
    private String username; // User's public identifier (ADR-003)
}
