package ch.batbern.events.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for registration creation (Story 4.1.5c)
 * <p>
 * Minimal response with no sensitive data.
 * Confirmation token is sent via email (not in response).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateRegistrationResponse {

    /**
     * Success message for the user
     */
    private String message;

    /**
     * Email address where confirmation was sent
     */
    private String email;
}
