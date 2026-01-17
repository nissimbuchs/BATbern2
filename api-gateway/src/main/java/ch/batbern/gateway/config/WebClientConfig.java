package ch.batbern.gateway.config;

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
}
