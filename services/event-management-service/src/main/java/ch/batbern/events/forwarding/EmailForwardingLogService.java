package ch.batbern.events.forwarding;

import ch.batbern.events.domain.EmailForwardingOutcome;
import ch.batbern.events.repository.EmailForwardingLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
public class EmailForwardingLogService {

    /**
     * A claim older than this is presumed dead: the SQS visibility timeout (300 s) has passed, so
     * the redelivery that is asking cannot overlap a still-running owner.
     *
     * <p>Coupled to {@code visibilityTimeout} of the forwarding queue in
     * {@code infrastructure/lib/stacks/inbound-email-stack.ts}: it must stay above that value.
     * Raising the timeout there means raising this too.
     */
    static final int STALE_CLAIM_SECONDS = 360;

    private final EmailForwardingLogRepository repository;

    /**
     * @return true when this caller now owns the object: a fresh claim, or a takeover of a stale
     *         claim whose owner died before sending anything. False when it is being or was handled.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean claim(String s3Key) {
        if (repository.claim(s3Key) == 1) {
            return true;
        }
        if (repository.reclaimStale(s3Key, STALE_CLAIM_SECONDS) == 1) {
            log.warn("Forwarding: reclaimed {} from an owner that died before sending", s3Key);
            return true;
        }
        return false;
    }

    /** Point of no return: from here on the mail is never retried, only reported if it stalls. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markSending(String s3Key) {
        repository.markSending(s3Key);
    }

    /** A claim that started sending and never completed: some recipients may be missing the mail. */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public boolean isStuckAfterSending(String s3Key) {
        return repository.isStuckAfterSending(s3Key, STALE_CLAIM_SECONDS);
    }

    /** Give the object back, so a redelivery can retry. Only before anything was sent. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void release(String s3Key) {
        repository.release(s3Key);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(String s3Key, EmailForwardingOutcome outcome, String aliases,
                         int recipientCount, int failedCount) {
        repository.findByS3Key(s3Key).ifPresentOrElse(entry -> {
            entry.setOutcome(outcome);
            entry.setAliases(truncate(aliases));
            entry.setRecipientCount(recipientCount);
            entry.setFailedCount(failedCount);
            entry.setCompletedAt(Instant.now());
        }, () -> log.warn("Forwarding: no log row for {} when recording {}", s3Key, outcome));
    }

    /** Retention (#1138). @return number of rows deleted */
    @Transactional
    public int purgeOlderThanDays(int days) {
        return repository.deleteOlderThanDays(days);
    }

    private static String truncate(String value) {
        return value != null && value.length() > 1000 ? value.substring(0, 1000) : value;
    }
}
