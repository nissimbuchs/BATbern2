package ch.batbern.companyuser.config;

import io.micrometer.cloudwatch2.CloudWatchConfig;
import io.micrometer.cloudwatch2.CloudWatchMeterRegistry;
import io.micrometer.core.aop.CountedAspect;
import io.micrometer.core.aop.TimedAspect;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.cloudwatch.CloudWatchAsyncClient;

import java.time.Duration;
import java.util.Map;

/**
 * Metrics configuration for CloudWatch integration.
 *
 * Configures Micrometer to publish metrics to CloudWatch under the "BATbern" namespace.
 * Enables @Timed and @Counted aspects for automatic method-level metrics.
 */
@Configuration
public class MetricsConfiguration {

    /**
     * Configure CloudWatch Meter Registry for publishing metrics to AWS CloudWatch.
     *
     * Metrics are published to the "BATbern" namespace with a 1-minute step interval.
     * This allows the CloudWatch dashboard to display real-time service metrics.
     */
    @Bean
    public CloudWatchMeterRegistry cloudWatchMeterRegistry() {
        CloudWatchConfig cloudWatchConfig = new CloudWatchConfig() {
            private final Map<String, String> configuration = Map.of(
                "cloudwatch.namespace", "BATbern",
                "cloudwatch.step", Duration.ofMinutes(1).toString()
            );

            @Override
            public String get(String key) {
                return configuration.get(key);
            }
        };

        return new CloudWatchMeterRegistry(
            cloudWatchConfig,
            io.micrometer.core.instrument.Clock.SYSTEM,
            CloudWatchAsyncClient.create()
        );
    }

    /**
     * Enable @Timed annotation support for automatic timing metrics.
     *
     * Methods annotated with @Timed will automatically publish duration metrics.
     */
    @Bean
    public TimedAspect timedAspect(MeterRegistry registry) {
        return new TimedAspect(registry);
    }

    /**
     * Enable @Counted annotation support for automatic counter metrics.
     *
     * Methods annotated with @Counted will automatically publish invocation count metrics.
     */
    @Bean
    public CountedAspect countedAspect(MeterRegistry registry) {
        return new CountedAspect(registry);
    }
}
