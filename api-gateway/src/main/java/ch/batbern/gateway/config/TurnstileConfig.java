package ch.batbern.gateway.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Enables Cloudflare Turnstile configuration properties binding.
 * Story 10.31 — Task 1.3
 */
@Configuration
@EnableConfigurationProperties(TurnstileProperties.class)
public class TurnstileConfig {
}
