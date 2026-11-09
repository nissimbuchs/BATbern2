package ch.batbern.shared.test;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.orm.jpa.EntityManagerFactoryUtils;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.support.AbstractTestExecutionListener;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.test.context.transaction.TransactionalTestExecutionListener;

import java.sql.SQLException;

/**
 * Flushes the test transaction's persistence context right after the test body, before the
 * rollback (17.0b3, #1104).
 *
 * <p>A {@code @Transactional} integration test rolls back at the end. Without a flush, Hibernate
 * never sends the pending inserts and updates, so database constraints, triggers and the
 * flush-time bean validation never run: a test passes for data production refuses. This listener
 * makes every such test send its SQL once, after the test method and before {@code @AfterEach} and
 * the rollback ({@link TransactionalTestExecutionListener} rolls back in {@code afterTestMethod}).
 * A violation fails the test with the database error.
 *
 * <p>Skipped when the test already failed, when no test-managed transaction is active, and when
 * the transaction is already aborted (SQLState {@code 25P02}): a test that provokes a violation on
 * purpose and asserts it leaves PostgreSQL refusing every further statement.
 */
public class FlushBeforeRollback extends AbstractTestExecutionListener {

    /** PostgreSQL: "current transaction is aborted, commands ignored until end of transaction block". */
    static final String TRANSACTION_ABORTED = "25P02";

    @Override
    public int getOrder() {
        return new TransactionalTestExecutionListener().getOrder() + 100;
    }

    @Override
    public void afterTestExecution(TestContext testContext) {
        if (testContext.getTestException() != null || !TestTransaction.isActive()) {
            return;
        }
        for (EntityManagerFactory factory
                : testContext.getApplicationContext().getBeansOfType(EntityManagerFactory.class).values()) {
            EntityManager entityManager = EntityManagerFactoryUtils.getTransactionalEntityManager(factory);
            if (entityManager == null) {
                continue;
            }
            try {
                entityManager.flush();
            } catch (RuntimeException e) {
                if (!isTransactionAborted(e)) {
                    throw e;
                }
            }
        }
    }

    static boolean isTransactionAborted(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql && TRANSACTION_ABORTED.equals(sql.getSQLState())) {
                return true;
            }
            if (cause.getCause() == cause) {
                break;
            }
        }
        return false;
    }
}
