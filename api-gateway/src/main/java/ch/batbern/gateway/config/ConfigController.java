package ch.batbern.gateway.config;

import ch.batbern.gateway.config.dto.CognitoConfigDTO;
import ch.batbern.gateway.config.dto.FeatureFlagsDTO;
import ch.batbern.gateway.config.dto.FrontendConfigDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Configuration endpoint for frontend runtime configuration
 *
 * This endpoint enables "build once, deploy everywhere" by providing
 * environment-specific configuration to the frontend at runtime instead
 * of baking it into the build.
 *
 * Benefits:
 * - Single production build deployed to all environments
 * - Configuration changes don't require frontend rebuilds
 * - Test exact same artifact that goes to production
 * - Reduced CI/CD complexity
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/config")
public class ConfigController {

    @Value("${app.environment:development}")
    private String environment;

    @Value("${aws.cognito.userPoolId}")
    private String cognitoUserPoolId;

    @Value("${aws.cognito.appClientId}")
    private String cognitoClientId;

    @Value("${aws.cognito.region}")
    private String awsRegion;

    /**
     * Get frontend runtime configuration
     *
     * Returns environment-specific configuration that the frontend
     * loads at startup to determine API endpoints, authentication settings,
     * and feature flags.
     *
     * @return Frontend configuration for current environment
     */
    @GetMapping
    public ResponseEntity<FrontendConfigDTO> getConfig() {
        log.debug("Serving frontend config for environment: {}", environment);

        FrontendConfigDTO config = FrontendConfigDTO.builder()
                .environment(environment)
                .apiBaseUrl(getApiBaseUrl())
                .cognito(CognitoConfigDTO.builder()
                        .userPoolId(cognitoUserPoolId)
                        .clientId(cognitoClientId)
                        .region(awsRegion)
                        .build())
                .features(FeatureFlagsDTO.builder()
                        .notifications(true)
                        .analytics(!"development".equals(environment))
                        .pwa(!"development".equals(environment))
                        .build())
                .build();

        return ResponseEntity.ok(config);
    }

    /**
     * Determine API base URL based on environment
     *
     * Development: Local API Gateway
     * Staging: Staging API Gateway with HTTPS
     * Production: Production API Gateway with HTTPS
     */
    private String getApiBaseUrl() {
        return switch (environment.toLowerCase()) {
            case "development" -> "http://localhost:8080/api/v1";
            case "staging" -> "https://api-staging.batbern.ch/api/v1";
            case "production" -> "https://api.batbern.ch/api/v1";
            default -> {
                log.warn("Unknown environment '{}', defaulting to development", environment);
                yield "http://localhost:8080/api/v1";
            }
        };
    }
}
