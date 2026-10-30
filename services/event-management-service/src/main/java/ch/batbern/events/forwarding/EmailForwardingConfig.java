package ch.batbern.events.forwarding;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;

@Configuration
@EnableConfigurationProperties(EmailForwardingProperties.class)
public class EmailForwardingConfig {

    /**
     * Only where forwarding or the mail delivery status listener runs (production); elsewhere
     * {@code ForwardingMetrics} and {@code MailCenterMetrics} only log. One client serves both.
     */
    @Bean
    @ConditionalOnExpression("'${aws.inbound-email.forwarding.enabled:false}' == 'true'"
            + " or '${aws.ses.delivery-status.enabled:false}' == 'true'")
    public CloudWatchClient forwardingCloudWatchClient() {
        return CloudWatchClient.builder()
                .region(Region.EU_CENTRAL_1)
                .credentialsProvider(DefaultCredentialsProvider.builder().build())
                .build();
    }
}
