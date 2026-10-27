package ch.batbern.events.forwarding;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;

@Configuration
@EnableConfigurationProperties(EmailForwardingProperties.class)
public class EmailForwardingConfig {

    /** Only where forwarding runs (staging/production); elsewhere metrics are just logged. */
    @Bean
    @ConditionalOnProperty(name = "aws.inbound-email.forwarding.enabled", havingValue = "true")
    public CloudWatchClient forwardingCloudWatchClient() {
        return CloudWatchClient.builder()
                .region(Region.EU_CENTRAL_1)
                .credentialsProvider(DefaultCredentialsProvider.builder().build())
                .build();
    }
}
