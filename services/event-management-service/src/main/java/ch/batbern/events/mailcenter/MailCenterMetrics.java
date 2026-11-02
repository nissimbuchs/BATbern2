package ch.batbern.events.mailcenter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.cloudwatch.model.MetricDatum;
import software.amazon.awssdk.services.cloudwatch.model.PutMetricDataRequest;
import software.amazon.awssdk.services.cloudwatch.model.StandardUnit;

/**
 * Mail Center counters in CloudWatch (namespace {@code BATbern/MailCenter}; the EMS task role may
 * publish only there). Without a {@link CloudWatchClient} bean (local/test) they are only logged.
 */
@Component
@Slf4j
public class MailCenterMetrics {

    static final String NAMESPACE = "BATbern/MailCenter";

    /**
     * Story 16.1b AC3: an SES event whose Message-ID has no recorded recipient (system mail without
     * context, forwarding, partner mail, mail sent before 16.1a).
     */
    static final String DELIVERY_EVENTS_UNMATCHED = "DeliveryEventsUnmatched";

    /** Story 16.2a: storing or counting an inbound mail failed; forwarding was not affected. */
    public static final String INGEST_FAILED = "MailCenterIngestFailed";

    /**
     * Story 16.2a: which AC3 rule threaded an inbound mail. Shows in production which Message-ID
     * match carries the traffic (Dev Notes, "Message-ID trap").
     */
    public static final String THREAD_MATCHED_BY_TOKEN = "ThreadMatchedByToken";
    public static final String THREAD_MATCHED_BY_HEADER = "ThreadMatchedByHeader";
    public static final String THREAD_MATCHED_BY_SES_ID = "ThreadMatchedBySesId";
    public static final String THREAD_MATCHED_BY_ALIAS = "ThreadMatchedByAlias";

    private final ObjectProvider<CloudWatchClient> cloudWatch;

    public MailCenterMetrics(ObjectProvider<CloudWatchClient> cloudWatch) {
        this.cloudWatch = cloudWatch;
    }

    public void increment(String metricName) {
        CloudWatchClient client = cloudWatch.getIfAvailable();
        if (client == null) {
            log.debug("Mail Center metric {} +1 (no CloudWatch client)", metricName);
            return;
        }
        try {
            client.putMetricData(PutMetricDataRequest.builder()
                    .namespace(NAMESPACE)
                    .metricData(MetricDatum.builder()
                            .metricName(metricName)
                            .value(1.0)
                            .unit(StandardUnit.COUNT)
                            .build())
                    .build());
        } catch (RuntimeException e) {
            log.warn("Failed to publish Mail Center metric {}: {}", metricName, e.toString());
        }
    }
}
