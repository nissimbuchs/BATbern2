package ch.batbern.gateway.config.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Turnstile public configuration exposed to the frontend.
 * Only the siteKey is included — secretKey is never exposed (AC6, Security Note).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TurnstileConfigDTO {

    /** Cloudflare Turnstile site key — safe for frontend use. */
    private String siteKey;
}
