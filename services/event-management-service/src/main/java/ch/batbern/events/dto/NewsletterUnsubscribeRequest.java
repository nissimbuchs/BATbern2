package ch.batbern.events.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Request DTO for token-based newsletter unsubscribe (Story 10.7 — AC3).
 */
@Data
public class NewsletterUnsubscribeRequest {

    @NotBlank(message = "Unsubscribe token is required")
    private String token;
}
