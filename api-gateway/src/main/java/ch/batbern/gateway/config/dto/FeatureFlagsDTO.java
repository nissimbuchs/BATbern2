package ch.batbern.gateway.config.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Feature flags for controlling frontend functionality
 * Enables/disables features based on environment
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeatureFlagsDTO {

    /**
     * Enable/disable notifications
     */
    private boolean notifications;

    /**
     * Enable/disable analytics (typically disabled in development)
     */
    private boolean analytics;

    /**
     * Enable/disable PWA features (typically disabled in development)
     */
    private boolean pwa;
}
