package ch.batbern.events.config;

import org.springframework.context.annotation.Configuration;

/**
 * Configuration for inbound email processing (Story 10.17).
 *
 * The {@code aws.inbound-email.enabled} property controls whether
 * the SQS listener is activated. Defaults to false in local/test environments.
 */
@Configuration
public class InboundEmailConfig {
    // SQS queue URL and bucket name are injected via @Value in InboundEmailListenerService.
    // ConditionalOnProperty on InboundEmailListenerService prevents startup
    // in environments where inbound email is not configured.
}
