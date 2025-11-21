package ch.batbern.companyuser.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.cloudwatch.model.Dimension;
import software.amazon.awssdk.services.cloudwatch.model.MetricDatum;
import software.amazon.awssdk.services.cloudwatch.model.PutMetricDataRequest;
import software.amazon.awssdk.services.cloudwatch.model.StandardUnit;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * User Sync Metrics Service
 * <p>
 * Story 1.2.5: User Sync and Reconciliation Implementation
 * AC5: CloudWatch metrics for sync operations
 * <p>
 * Purpose:
 * - Record sync latency metrics
 * - Record sync failure metrics
 * - Record drift detection metrics
 * - Publish metrics to CloudWatch namespace: BATbern/UserSync
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserSyncMetricsService {

    private final CloudWatchClient cloudWatchClient;

    private static final String NAMESPACE = "BATbern/UserSync";

    /**
     * Record sync latency metric
     * AC5: Performance monitoring
     *
     * @param syncType Sync type (PostConfirmation, PreTokenGeneration, JITProvisioning, RoleSync)
     * @param latencyMs Latency in milliseconds
     */
    public void recordSyncLatency(String syncType, long latencyMs) {
        try {
            MetricDatum metric = MetricDatum.builder()
                    .metricName("SyncLatency")
                    .value((double) latencyMs)
                    .unit(StandardUnit.MILLISECONDS)
                    .timestamp(Instant.now())
                    .dimensions(
                            Dimension.builder()
                                    .name("SyncType")
                                    .value(syncType)
                                    .build()
                    )
                    .build();

            publishMetric(metric);

            log.debug("Sync latency metric recorded",
                    mapOf("syncType", syncType, "latencyMs", latencyMs));

        } catch (Exception e) {
            log.error("Failed to record sync latency metric", e);
        }
    }

    /**
     * Record sync failure metric
     * AC5: Failure rate monitoring
     *
     * @param syncType Sync type
     */
    public void recordSyncFailure(String syncType) {
        try {
            MetricDatum metric = MetricDatum.builder()
                    .metricName("SyncFailures")
                    .value(1.0)
                    .unit(StandardUnit.COUNT)
                    .timestamp(Instant.now())
                    .dimensions(
                            Dimension.builder()
                                    .name("SyncType")
                                    .value(syncType)
                                    .build()
                    )
                    .build();

            publishMetric(metric);

            log.debug("Sync failure metric recorded",
                    mapOf("syncType", syncType));

        } catch (Exception e) {
            log.error("Failed to record sync failure metric", e);
        }
    }

    /**
     * Record drift detected metric
     * AC5: Drift monitoring
     *
     * @param driftCount Number of drift instances detected
     */
    public void recordDriftDetected(int driftCount) {
        try {
            MetricDatum metric = MetricDatum.builder()
                    .metricName("DriftDetected")
                    .value((double) driftCount)
                    .unit(StandardUnit.COUNT)
                    .timestamp(Instant.now())
                    .build();

            publishMetric(metric);

            log.debug("Drift detected metric recorded",
                    mapOf("driftCount", driftCount));

        } catch (Exception e) {
            log.error("Failed to record drift detected metric", e);
        }
    }

    /**
     * Record user created metric
     *
     * @param source Source (POST_CONFIRMATION, JIT_PROVISIONING, RECONCILIATION)
     */
    public void recordUserCreated(String source) {
        try {
            MetricDatum metric = MetricDatum.builder()
                    .metricName("UserCreated")
                    .value(1.0)
                    .unit(StandardUnit.COUNT)
                    .timestamp(Instant.now())
                    .dimensions(
                            Dimension.builder()
                                    .name("Source")
                                    .value(source)
                                    .build()
                    )
                    .build();

            publishMetric(metric);

            log.debug("User created metric recorded",
                    mapOf("source", source));

        } catch (Exception e) {
            log.error("Failed to record user created metric", e);
        }
    }

    /**
     * Record compensation log metric
     *
     * @param operation Operation type (ROLE_SYNC, USER_CREATE, USER_DELETE)
     */
    public void recordCompensationLog(String operation) {
        try {
            MetricDatum metric = MetricDatum.builder()
                    .metricName("CompensationLogsCreated")
                    .value(1.0)
                    .unit(StandardUnit.COUNT)
                    .timestamp(Instant.now())
                    .dimensions(
                            Dimension.builder()
                                    .name("Operation")
                                    .value(operation)
                                    .build()
                    )
                    .build();

            publishMetric(metric);

            log.debug("Compensation log metric recorded",
                    mapOf("operation", operation));

        } catch (Exception e) {
            log.error("Failed to record compensation log metric", e);
        }
    }

    /**
     * Record reconciliation job metrics
     *
     * @param orphanedUsers Number of orphaned users deactivated
     * @param missingUsers Number of missing users created
     * @param roleMismatches Number of role mismatches synced
     * @param compensationRetries Number of compensations retried
     * @param durationMs Job duration in milliseconds
     */
    public void recordReconciliationJob(int orphanedUsers, int missingUsers,
                                         int roleMismatches, int compensationRetries,
                                         long durationMs) {
        try {
            List<MetricDatum> metrics = new ArrayList<>();

            metrics.add(MetricDatum.builder()
                    .metricName("ReconciliationOrphanedUsers")
                    .value((double) orphanedUsers)
                    .unit(StandardUnit.COUNT)
                    .timestamp(Instant.now())
                    .build());

            metrics.add(MetricDatum.builder()
                    .metricName("ReconciliationMissingUsers")
                    .value((double) missingUsers)
                    .unit(StandardUnit.COUNT)
                    .timestamp(Instant.now())
                    .build());

            metrics.add(MetricDatum.builder()
                    .metricName("ReconciliationRoleMismatches")
                    .value((double) roleMismatches)
                    .unit(StandardUnit.COUNT)
                    .timestamp(Instant.now())
                    .build());

            metrics.add(MetricDatum.builder()
                    .metricName("ReconciliationCompensationRetries")
                    .value((double) compensationRetries)
                    .unit(StandardUnit.COUNT)
                    .timestamp(Instant.now())
                    .build());

            metrics.add(MetricDatum.builder()
                    .metricName("ReconciliationDuration")
                    .value((double) durationMs)
                    .unit(StandardUnit.MILLISECONDS)
                    .timestamp(Instant.now())
                    .build());

            publishMetrics(metrics);

            log.info("Reconciliation job metrics recorded",
                    mapOf("orphanedUsers", orphanedUsers,
                            "missingUsers", missingUsers,
                            "roleMismatches", roleMismatches,
                            "compensationRetries", compensationRetries,
                            "durationMs", durationMs));

        } catch (Exception e) {
            log.error("Failed to record reconciliation job metrics", e);
        }
    }

    /**
     * Publish single metric to CloudWatch
     */
    private void publishMetric(MetricDatum metric) {
        PutMetricDataRequest request = PutMetricDataRequest.builder()
                .namespace(NAMESPACE)
                .metricData(metric)
                .build();

        cloudWatchClient.putMetricData(request);
    }

    /**
     * Publish multiple metrics to CloudWatch
     */
    private void publishMetrics(List<MetricDatum> metrics) {
        // CloudWatch allows up to 1000 metrics per request
        // Split into batches if needed
        int batchSize = 1000;
        for (int i = 0; i < metrics.size(); i += batchSize) {
            int endIndex = Math.min(i + batchSize, metrics.size());
            List<MetricDatum> batch = metrics.subList(i, endIndex);

            PutMetricDataRequest request = PutMetricDataRequest.builder()
                    .namespace(NAMESPACE)
                    .metricData(batch)
                    .build();

            cloudWatchClient.putMetricData(request);
        }
    }

    // Map helper
    private <K, V> java.util.Map<K, V> mapOf(Object... entries) {
        java.util.Map<K, V> map = new java.util.HashMap<>();
        for (int i = 0; i < entries.length; i += 2) {
            map.put((K) entries[i], (V) entries[i + 1]);
        }
        return map;
    }
}
