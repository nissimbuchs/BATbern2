package ch.batbern.events.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for authenticated user's newsletter subscription status (Story 10.7 — AC7).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NewsletterSubscriptionStatusResponse {

    private boolean subscribed;

    /** Email of the subscriber record (null if not subscribed). */
    private String email;
}
