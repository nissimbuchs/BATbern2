package ch.batbern.events.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.cloudfront.CloudFrontClient;
import software.amazon.awssdk.services.cloudfront.model.CreateInvalidationRequest;
import software.amazon.awssdk.services.cloudfront.model.CreateInvalidationResponse;
import software.amazon.awssdk.services.cloudfront.model.InvalidationBatch;
import software.amazon.awssdk.services.cloudfront.model.Paths;

import java.util.List;
import java.util.UUID;

/**
 * CDN Invalidation Service
 * Story BAT-16 (AC6): CDN Cache Invalidation
 *
 * Handles CloudFront CDN cache invalidation when content is published or updated.
 * Ensures users see the latest published content immediately after publishing.
 *
 * Invalidation paths:
 * - /api/public/events/{eventCode}
 * - /api/public/events/{eventCode}/{phase}
 * - /events/{eventCode}/*
 */
@Service
@Slf4j
public class CdnInvalidationService {

    private final CloudFrontClient cloudFrontClient;

    @Value("${aws.cloudfront.distribution-id:}")
    private String distributionId;

    @Value("${aws.cloudfront.enabled:false}")
    private boolean cdnEnabled;

    public CdnInvalidationService(CloudFrontClient cloudFrontClient) {
        this.cloudFrontClient = cloudFrontClient;
    }

    /**
     * Invalidate CDN cache for a specific event and phase
     *
     * @param eventCode The event code (e.g., "BATbern56")
     * @param phase The publishing phase (e.g., "speakers", "agenda")
     * @return The CloudFront invalidation ID, or "disabled" if CDN is not enabled
     */
    public String invalidateCache(String eventCode, String phase) {
        if (!cdnEnabled) {
            log.debug("CDN invalidation skipped (disabled) for event {} phase {}", eventCode, phase);
            return "disabled";
        }

        if (distributionId == null || distributionId.isEmpty()) {
            log.warn("CDN invalidation skipped (no distribution ID) for event {} phase {}", eventCode, phase);
            return "no-distribution-id";
        }

        try {
            List<String> paths = buildInvalidationPaths(eventCode, phase);

            InvalidationBatch batch = InvalidationBatch.builder()
                    .paths(Paths.builder()
                            .quantity(paths.size())
                            .items(paths)
                            .build())
                    .callerReference(UUID.randomUUID().toString())
                    .build();

            CreateInvalidationRequest request = CreateInvalidationRequest.builder()
                    .distributionId(distributionId)
                    .invalidationBatch(batch)
                    .build();

            CreateInvalidationResponse response = cloudFrontClient.createInvalidation(request);

            String invalidationId = response.invalidation().id();
            log.info("CDN invalidation created: {} for event {} phase {}", invalidationId, eventCode, phase);

            return invalidationId;
        } catch (Exception ex) {
            log.error("CDN invalidation failed for event {} phase {}: {}", eventCode, phase, ex.getMessage(), ex);
            return "error-" + UUID.randomUUID();
        }
    }

    /**
     * Build list of paths to invalidate for an event and phase
     *
     * @param eventCode The event code
     * @param phase The publishing phase
     * @return List of paths to invalidate
     */
    private List<String> buildInvalidationPaths(String eventCode, String phase) {
        return List.of(
                "/api/public/events/" + eventCode,
                "/api/public/events/" + eventCode + "/" + phase,
                "/events/" + eventCode + "/*"
        );
    }

    /**
     * Check if CDN invalidation is enabled
     *
     * @return true if CDN is enabled and configured
     */
    public boolean isEnabled() {
        return cdnEnabled && distributionId != null && !distributionId.isEmpty();
    }
}
