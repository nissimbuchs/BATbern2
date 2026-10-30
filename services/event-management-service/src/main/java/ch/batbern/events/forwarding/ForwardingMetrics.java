package ch.batbern.events.forwarding;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.cloudwatch.model.MetricDatum;
import software.amazon.awssdk.services.cloudwatch.model.PutMetricDataRequest;
import software.amazon.awssdk.services.cloudwatch.model.StandardUnit;

/**
 * Counters in the CloudWatch namespace the forwarder Lambda used, so the existing
 * {@code batbern-{env}-emails-rejected} alarm and any dashboards keep working.
 * Without a {@link CloudWatchClient} bean (local/test) the counters are only logged.
 */
@Component
@Slf4j
public class ForwardingMetrics {

    static final String NAMESPACE = "BATbern/EmailForwarder";

    static final String FORWARDED = "EmailsForwarded";
    static final String REJECTED = "EmailsRejected";
    static final String UNRESOLVED = "EmailsUnresolved";
    static final String FAILED = "EmailsFailed";
    static final String CALENDAR_REPLIES_DROPPED = "CalendarRepliesDropped";
    static final String AUTO_REPLIES_DROPPED = "AutoRepliesDropped";
    static final String NO_REPLY_DROPPED = "NoReplyDropped";
    static final String LOOPS_DROPPED = "LoopsDropped";
    static final String DUPLICATES_SKIPPED = "DuplicatesSkipped";
    /** Story 16.0: unrecognized replies@ mail handed to the organizers (kept apart from alias forwards). */
    static final String REPLIES_FORWARDED = "RepliesForwarded";
    static final String BOUNCES_DROPPED = "BouncesDropped";
    /** Story 16.0 Part B: a calendar "Declined" to calendar@ that led to the deregistration-link mail. */
    static final String CALENDAR_DECLINE_LINK_SENT = "CalendarDeclineLinkSent";
    /** A decline that matched no single registration (AC7). */
    static final String CALENDAR_DECLINE_UNMATCHED = "CalendarDeclineUnmatched";
    /** A matched decline that sent nothing: link sent within 7 days, or already cancelled. */
    static final String CALENDAR_DECLINE_SKIPPED = "CalendarDeclineSkipped";

    private final ObjectProvider<CloudWatchClient> cloudWatch;

    public ForwardingMetrics(ObjectProvider<CloudWatchClient> cloudWatch) {
        this.cloudWatch = cloudWatch;
    }

    public void increment(String metricName) {
        CloudWatchClient client = cloudWatch.getIfAvailable();
        if (client == null) {
            log.debug("Forwarding metric {} +1 (no CloudWatch client)", metricName);
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
            log.warn("Failed to publish forwarding metric {}: {}", metricName, e.toString());
        }
    }
}
