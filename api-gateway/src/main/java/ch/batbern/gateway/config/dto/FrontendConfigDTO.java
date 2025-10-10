package ch.batbern.gateway.config.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Frontend runtime configuration DTO
 * Provides environment-specific configuration to the React frontend at runtime
 * This enables "build once, deploy everywhere" by loading config from backend
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FrontendConfigDTO {

    /**
     * Current environment: development, staging, or production
     */
    private String environment;

    /**
     * API base URL for backend communication
     */
    private String apiBaseUrl;

    /**
     * AWS Cognito configuration for authentication
     */
    private CognitoConfigDTO cognito;

    /**
     * Feature flags for conditional functionality
     */
    private FeatureFlagsDTO features;
}
