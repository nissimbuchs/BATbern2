package ch.batbern.events.forwarding;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code aws.inbound-email.forwarding.*}: alias forwarding inside EMS.
 *
 * @param enabled          starts the SQS listener (staging/production only)
 * @param queueUrl         SQS queue fed by the S3 {@code forwarding/} prefix
 * @param domain           forwarding domain, e.g. {@code batbern.ch}
 * @param sender           verified From address for forwarded copies
 * @param configurationSet SES configuration set for delivery tracking (null → EmailService default)
 * @param sendDelayMs      pause between individual sends (SES rate limit headroom)
 */
@ConfigurationProperties(prefix = "aws.inbound-email.forwarding")
public record EmailForwardingProperties(
        boolean enabled,
        String queueUrl,
        String domain,
        String sender,
        String configurationSet,
        long sendDelayMs) {

    public EmailForwardingProperties {
        if (domain == null || domain.isBlank()) {
            domain = "batbern.ch";
        }
        if (sender == null || sender.isBlank()) {
            sender = "noreply@" + domain;
        }
        if (sendDelayMs < 0) {
            sendDelayMs = 0;
        }
    }
}
