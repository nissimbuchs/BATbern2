package ch.batbern.events.config;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Runs an action once the current transaction has committed, or right away when there is none. A
 * rolled-back transaction never runs it. For side effects a rollback cannot take back: a mail, a
 * cache clear that a concurrent read could otherwise undo before the commit.
 *
 * <p>An exception of the action reaches the caller after the commit, as Spring's
 * {@link TransactionSynchronization#afterCommit} contract has it; the data is committed by then.
 */
public final class AfterCommit {

    private AfterCommit() {
    }

    public static void run(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }
}
