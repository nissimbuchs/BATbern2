package ch.batbern.events.scheduled;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * A scheduled job that catches a failure for one item and goes on with the next records it here,
 * never only in a log line (assertion rule "no silent catch in schedulers without a metric").
 *
 * <p>Same pattern as {@code SpringDomainEventPublisher} (#1279): a Micrometer counter, and an
 * ERROR line behind the fixed marker {@value #LOG_MARKER}. No service exports Micrometer to
 * CloudWatch, so the alarm {@code {Service}-Scheduled-Job-Failures} is log based: a metric filter
 * on the marker ({@code infrastructure/lib/constructs/ecs-service-alarms.ts}, which reads the
 * marker from this file in its test, so a rename fails the CDK build instead of silencing it).
 */
@Component
public class ScheduledJobFailures {

    /** First token of the failure line; the CloudWatch metric filter matches it literally. */
    public static final String LOG_MARKER = "SCHEDULED_JOB_FAILED";

    /** Micrometer counter, tagged {@code job}. */
    public static final String METRIC = "batbern.scheduled.job.failures";

    private static final Logger LOGGER = LoggerFactory.getLogger(ScheduledJobFailures.class);

    private final MeterRegistry meterRegistry;

    public ScheduledJobFailures(ObjectProvider<MeterRegistry> meterRegistry) {
        this.meterRegistry = meterRegistry.getIfAvailable(() -> Metrics.globalRegistry);
    }

    /**
     * Record that {@code job} failed for {@code item} and continues with the next one.
     */
    public void record(String job, String item, Exception failure) {
        LOGGER.error(LOG_MARKER + " job={} item={} {}: the job continues with the next item",
                job, item, failure.toString(), failure);
        Counter.builder(METRIC)
                .description("Items a scheduled job could not process and skipped")
                .tag("job", job)
                .register(meterRegistry)
                .increment();
    }
}
