package ch.batbern.shared.repository;

import ch.batbern.shared.domain.UserSyncCompensationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Repository for User Sync Compensation Log
 * <p>
 * Story 1.2.5: User Sync and Reconciliation Implementation
 * AC3, AC8: Saga compensation pattern queries
 */
@Repository
public interface UserSyncCompensationLogRepository extends JpaRepository<UserSyncCompensationLog, UUID> {

    /**
     * Find all compensation logs requiring retry
     * <p>
     * AC4: Reconciliation job retries failed compensations
     *
     * @param maxRetries Maximum retry count
     * @return List of compensation logs needing retry
     */
    @Query("SELECT c FROM UserSyncCompensationLog c " +
            "WHERE c.compensationRequired = true " +
            "AND c.status = 'FAILED' " +
            "AND c.retryCount < :maxRetries " +
            "ORDER BY c.attemptedAt ASC")
    List<UserSyncCompensationLog> findPendingCompensations(int maxRetries);

    /**
     * Find compensation logs by user ID
     *
     * @param userId User ID
     * @return List of compensation logs for user
     */
    List<UserSyncCompensationLog> findByUserId(UUID userId);

    /**
     * Find compensation logs by Cognito ID
     *
     * @param cognitoId Cognito user ID
     * @return List of compensation logs for Cognito user
     */
    List<UserSyncCompensationLog> findByCognitoId(String cognitoId);

    /**
     * Find compensation logs by status
     *
     * @param status Status (PENDING, COMPLETED, FAILED)
     * @return List of compensation logs with status
     */
    List<UserSyncCompensationLog> findByStatus(String status);

    /**
     * Count failed compensations in time period
     *
     * @param since Time threshold
     * @return Count of failures
     */
    @Query("SELECT COUNT(c) FROM UserSyncCompensationLog c " +
            "WHERE c.status = 'FAILED' " +
            "AND c.attemptedAt >= :since")
    long countRecentFailures(Instant since);
}
