package ch.batbern.events.forwarding;

import ch.batbern.events.mailcenter.inbound.InboundMailCenter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Processes one mail to the {@code @batbern.ch} aliases (Story 16.2a, extracted from
 * {@link EmailForwardingListener} so the local dev simulator runs the same code): forwarding first,
 * unchanged, then the Mail Center records or counts it. The Mail Center never throws; a forwarding
 * failure propagates as before, so SQS redelivers.
 */
@Component
@RequiredArgsConstructor
public class AliasMailProcessor {

    private final EmailForwardingService forwardingService;
    private final InboundMailCenter mailCenter;

    public EmailForwardingService.Result process(String s3Key, byte[] raw) {
        EmailForwardingService.Result result = forwardingService.forward(s3Key, raw);
        mailCenter.ingestAlias(s3Key, raw, result);
        return result;
    }
}
