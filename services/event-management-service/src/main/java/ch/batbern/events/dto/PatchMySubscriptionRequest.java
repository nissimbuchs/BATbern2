package ch.batbern.events.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Request DTO for PATCH /api/v1/newsletter/my-subscription (Story 10.7 — AC7).
 */
@Getter
@Setter
@NoArgsConstructor
public class PatchMySubscriptionRequest {

    @NotNull(message = "subscribed must not be null")
    private Boolean subscribed;

    private String language;
}
