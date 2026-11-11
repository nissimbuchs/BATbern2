package ch.batbern.companyuser.service;

import ch.batbern.companyuser.client.MailCenterRequestContract;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.UUID;

/**
 * Tells the Mail Center request contract that a merge or logo request was decided, once the
 * decision is committed. A failure does not undo the decision: it is logged as an ERROR and counted
 * ({@value #FAILURES}). The conversation then stays OPEN until an organizer closes it; deciding in the
 * Mail Center closes it there at once anyway.
 */
@Component
@Slf4j
public class MailCenterRequestDecisions {

    static final String FAILURES = "mailcenter.request_contract.failures";

    private final MailCenterRequestContract contract;
    private final Counter failures;

    public MailCenterRequestDecisions(MailCenterRequestContract contract, ObjectProvider<MeterRegistry> meterRegistry) {
        this.contract = contract;
        this.failures = Counter.builder(FAILURES)
                .description("Decisions of merge or logo requests the Mail Center did not take")
                .register(meterRegistry.getIfAvailable(() -> Metrics.globalRegistry));
    }

    /** After the current transaction commits (at once without one), calls the contract's decision. */
    public void afterCommit(MailCenterRequestContract.Kind kind, UUID requestId, String status, String decidedBy,
            Instant decidedAt) {
        Runnable call = () -> decided(kind, requestId, new MailCenterRequestContract.Decided(kind, status, decidedBy,
                decidedAt));
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    call.run();
                }
            });
        } else {
            call.run();
        }
    }

    private void decided(MailCenterRequestContract.Kind kind, UUID requestId,
            MailCenterRequestContract.Decided decision) {
        try {
            contract.decided(requestId, decision);
        } catch (RuntimeException e) {
            failures.increment();
            log.error("MAIL_CENTER_REQUEST decision not delivered kind={} id={} status={} cause={}", kind, requestId,
                    decision.status(), e.getMessage());
        }
    }
}
