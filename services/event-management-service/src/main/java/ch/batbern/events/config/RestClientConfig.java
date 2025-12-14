package ch.batbern.events.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.RestTemplate;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.stream.Collectors;

/**
 * Configuration for REST client used for service-to-service communication.
 *
 * Configures RestTemplate with appropriate timeouts and logging for calls
 * to the User Management Service API.
 */
@Configuration
@Slf4j
public class RestClientConfig {

    /**
     * Create RestTemplate with configured timeouts and logging.
     *
     * Timeouts:
     * - Connect: 5 seconds (fail fast on connection issues)
     * - Read: 10 seconds (reasonable timeout for API responses)
     *
     * @param builder RestTemplateBuilder for configuration
     * @return Configured RestTemplate instance
     */
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                .connectTimeout(Duration.ofSeconds(5))
                .readTimeout(Duration.ofSeconds(10))
                .interceptors(new LoggingInterceptor())
                .build();
    }

    /**
     * Logging interceptor for REST API calls.
     *
     * Logs request/response details for debugging and monitoring.
     */
    @Slf4j
    private static class LoggingInterceptor implements ClientHttpRequestInterceptor {

        @Override
        public ClientHttpResponse intercept(
                HttpRequest request,
                byte[] body,
                ClientHttpRequestExecution execution
        ) throws IOException {

            // Log outgoing request
            log.debug("REST Request: {} {}", request.getMethod(), request.getURI());
            if (log.isTraceEnabled() && body.length > 0) {
                log.trace("Request body: {}", new String(body, StandardCharsets.UTF_8));
            }

            // Execute request and measure time
            long startTime = System.currentTimeMillis();
            ClientHttpResponse response = execution.execute(request, body);
            long duration = System.currentTimeMillis() - startTime;

            // Log response
            log.debug("REST Response: {} {} - Status: {} - Duration: {}ms",
                    request.getMethod(),
                    request.getURI(),
                    response.getStatusCode().value(),
                    duration);

            if (log.isTraceEnabled()) {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(response.getBody(), StandardCharsets.UTF_8))) {
                    String responseBody = reader.lines()
                            .collect(Collectors.joining("\n"));
                    log.trace("Response body: {}", responseBody);
                } catch (IOException e) {
                    log.warn("Failed to read response body for logging", e);
                }
            }

            return response;
        }
    }
}
