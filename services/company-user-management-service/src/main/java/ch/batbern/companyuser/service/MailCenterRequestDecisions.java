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

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
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
    static final String WITHDRAWN = "WITHDRAWN";
    static final String WITHDRAWN_BY = "system:mail-center-request";

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

    /**
     * Opens the conversation of a request being filed, inside the filing transaction, and arranges its
     * withdrawal should that transaction roll back ({@link #withdrawOnRollback}). A failed open fails
     * the filing ({@link MailCenterRequestContract.MailCenterCallFailedException}). When the open timed
     * out, the implementer may still commit the conversation, so the withdrawal is arranged as well;
     * when it was refused or unreachable, nothing was opened and nothing is withdrawn.
     */
    public void open(MailCenterRequestContract.Opened request) {
        try {
            contract.opened(request);
        } catch (MailCenterRequestContract.MailCenterCallFailedException e) {
            if (timedOut(e)) {
                withdrawOnRollback(request.kind(), request.requestId());
            }
            throw e;
        }
        withdrawOnRollback(request.kind(), request.requestId());
    }

    private static boolean timedOut(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof SocketTimeoutException || t instanceof HttpTimeoutException) {
                return true;
            }
        }
        return false;
    }

    /**
     * Registers, on the filing transaction, the withdrawal of a request whose conversation was just
     * opened: when that transaction rolls back (commit failure, a later failure), no request exists,
     * so the contract is told {@code WITHDRAWN} by {@value #WITHDRAWN_BY} and the conversation is
     * closed. A failed withdrawal is logged as an ERROR and counted like a decision. Without a
     * transaction there is nothing to roll back and nothing is registered.
     */
    void withdrawOnRollback(MailCenterRequestContract.Kind kind, UUID requestId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    log.warn("MAIL_CENTER_REQUEST filing rolled back after the open kind={} id={}: withdrawing",
                            kind, requestId);
                    decided(kind, requestId, new MailCenterRequestContract.Decided(kind, WITHDRAWN, WITHDRAWN_BY,
                            Instant.now()));
                }
            }
        });
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
