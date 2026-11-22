package ch.batbern.agent.config;

import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * The one {@link RestTemplate} of agent-service (story 4): CUMS's two {@code /internal/agent-*}
 * operations and the public API through the gateway. Connect 5 s, read 30 s. No logging
 * interceptor: the bodies carry grant secrets and tokens, and the headers the caller secret.
 */
@Configuration
public class RestClientConfig {

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                .connectTimeout(Duration.ofSeconds(5))
                .readTimeout(Duration.ofSeconds(30))
                .build();
    }
}
