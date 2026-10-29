package ch.batbern.events.repository;

import ch.batbern.events.domain.EmailForwardingLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface EmailForwardingLogRepository extends JpaRepository<EmailForwardingLog, UUID> {

    /**
     * Claim an S3 object for forwarding. Returns 1 when this caller owns it, 0 when another
     * delivery of the same SQS message (or a replay) already claimed it.
     */
    @Modifying
    @Query(value = "INSERT INTO email_forwarding_log (s3_key) VALUES (:s3Key) ON CONFLICT (s3_key) DO NOTHING",
            nativeQuery = true)
    int claim(@Param("s3Key") String s3Key);

    /**
     * Take over a claim whose owner died before sending anything. Returns 1 on takeover.
     * A claim younger than {@code staleSeconds} may still be in progress and is left alone.
     */
    @Modifying
    @Query(value = "UPDATE email_forwarding_log SET received_at = now()"
            + " WHERE s3_key = :s3Key AND outcome = 'processing' AND sending_started_at IS NULL"
            + " AND received_at < now() - make_interval(secs => :staleSeconds)",
            nativeQuery = true)
    int reclaimStale(@Param("s3Key") String s3Key, @Param("staleSeconds") int staleSeconds);

    @Modifying
    @Query(value = "UPDATE email_forwarding_log SET sending_started_at = now() WHERE s3_key = :s3Key",
            nativeQuery = true)
    int markSending(@Param("s3Key") String s3Key);

    @Query(value = "SELECT count(*) > 0 FROM email_forwarding_log WHERE s3_key = :s3Key"
            + " AND outcome = 'processing' AND sending_started_at IS NOT NULL"
            + " AND sending_started_at < now() - make_interval(secs => :staleSeconds)",
            nativeQuery = true)
    boolean isStuckAfterSending(@Param("s3Key") String s3Key, @Param("staleSeconds") int staleSeconds);

    @Modifying
    @Query("DELETE FROM EmailForwardingLog l WHERE l.s3Key = :s3Key")
    int release(@Param("s3Key") String s3Key);

    Optional<EmailForwardingLog> findByS3Key(String s3Key);

    /** Retention (#1138): delete rows received more than {@code days} days ago. */
    @Modifying
    @Query(value = "DELETE FROM email_forwarding_log WHERE received_at < now() - make_interval(days => :days)",
            nativeQuery = true)
    int deleteOlderThanDays(@Param("days") int days);
}
