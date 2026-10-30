package ch.batbern.events.mailcenter;

import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Story 16.1b: consumes {@code batbern-{env}-mail-delivery-status}, the SQS queue on the
 * transactional SES events topic. Only loaded where the queue exists
 * ({@code aws.ses.delivery-status.enabled=true}, set by the CDK stack); local dev runs without it.
 * A thrown exception leaves the message on the queue, so SQS retries and then moves it to the DLQ.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "aws.ses.delivery-status.enabled", havingValue = "true")
public class MailDeliveryStatusListener {

    private final MailDeliveryStatusService deliveryStatus;

    @SqsListener("${aws.ses.delivery-status.queue-url}")
    public void onMessage(String snsMessageBody) {
        deliveryStatus.process(snsMessageBody);
    }
}
