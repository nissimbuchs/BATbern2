package ch.batbern.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Configuration properties for Cloudflare Turnstile bot protection.
 * Story 10.31 — AC1, AC5
 */
@Data
@ConfigurationProperties(prefix = "turnstile")
public class TurnstileProperties {

    /** Master on/off switch. Defaults to false so existing behaviour is preserved (AC5). */
    private boolean enabled;

    /** Cloudflare site key — safe to expose to frontend (AC6). */
    private String siteKey;

    /** Cloudflare secret key — NEVER expose to frontend. */
    private String secretKey;

    /** Cloudflare siteverify endpoint. */
    private String verifyUrl;

    /**
     * List of METHOD:path patterns to protect.
     * Example: POST:/api/v1/newsletter/subscribe, POST:/api/v1/events/{code}/registrations
     * Supports AntPathMatcher wildcards (e.g. events/&#42;/registrations).
     */
    private List<String> protectedEndpoints;
}
