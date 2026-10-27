package ch.batbern.events.forwarding;

import ch.batbern.events.domain.EmailForwardingOutcome;
import ch.batbern.events.repository.EmailForwardingLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Idempotency for forwarding: each S3 object is forwarded at most once, whatever SQS redelivers.
 * Every method commits on its own, so a claim is visible before the first mail goes out.
 */
@Service
@RequiredArgsConstructor
public class EmailForwardingLogService {

    private final EmailForwardingLogRepository repository;

    /** @return true when this caller now owns the object, false when it was claimed before */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean claim(String s3Key) {
        return repository.claim(s3Key) == 1;
    }

    /** Give the object back, so a redelivery can retry. Only before anything was sent. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void release(String s3Key) {
        repository.release(s3Key);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(String s3Key, EmailForwardingOutcome outcome, String aliases,
                         int recipientCount, int failedCount) {
        repository.findByS3Key(s3Key).ifPresent(entry -> {
            entry.setOutcome(outcome);
            entry.setAliases(truncate(aliases));
            entry.setRecipientCount(recipientCount);
            entry.setFailedCount(failedCount);
            entry.setCompletedAt(Instant.now());
        });
    }

    private static String truncate(String value) {
        return value != null && value.length() > 1000 ? value.substring(0, 1000) : value;
    }
}
