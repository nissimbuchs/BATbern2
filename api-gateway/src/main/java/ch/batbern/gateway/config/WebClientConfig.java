package ch.batbern.gateway.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * Configuration for HTTP clients used to communicate with downstream microservices.
 */
@Configuration
public class WebClientConfig {

    /**
     * RestTemplate for synchronous HTTP communication with microservices.
     * Configured with timeouts and error handling.
     *
     * Read timeout set to 120 seconds to support:
     * - Session batch import with material downloads from CDN (Story 5.9)
     * - Large file uploads/downloads
     * - Complex database operations
     */
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
            .connectTimeout(Duration.ofSeconds(10))
            .readTimeout(Duration.ofSeconds(120))
            .build();
    }

    /**
     * Dedicated RestTemplate for Cloudflare Turnstile siteverify calls.
     *
     * Uses tight timeouts (3s connect / 5s read) so that a Cloudflare outage
     * causes a fast fail-open instead of blocking servlet threads for the 120s
     * timeout that the shared restTemplate requires for Story 5.9 batch imports.
     * Story 10.31 — Task 3a, AC4.
     */
    @Bean
    @Qualifier("turnstileRestTemplate")
    public RestTemplate turnstileRestTemplate(RestTemplateBuilder builder) {
        return builder
            .connectTimeout(Duration.ofSeconds(3))
            .readTimeout(Duration.ofSeconds(5))
            .build();
    }

    /**
     * Dedicated RestTemplate for the request-time {@code is_active} gate's CUMS
     * user-status lookup (Story 12.2 — {@code GatewayUserStatusClient}).
     *
     * Uses tight timeouts (2s connect / 3s read) — same rationale as
     * {@link #turnstileRestTemplate}: this lookup runs on every authenticated request
     * (cache-miss path), so a slow/unresponsive CUMS must trigger a FAST fail-open
     * instead of pinning a gateway servlet thread for the 120s the shared
     * {@link #restTemplate} allows for Story 5.9 batch imports. Failing open slowly
     * would let a CUMS hiccup exhaust the front-door thread pool — the opposite of
     * the gate's fail-open intent.
     */
    @Bean
    @Qualifier("cumsStatusRestTemplate")
    public RestTemplate cumsStatusRestTemplate(RestTemplateBuilder builder) {
        return builder
            .connectTimeout(Duration.ofSeconds(2))
            .readTimeout(Duration.ofSeconds(3))
            .build();
    }
}
