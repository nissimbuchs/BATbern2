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

    @Modifying
    @Query("DELETE FROM EmailForwardingLog l WHERE l.s3Key = :s3Key")
    int release(@Param("s3Key") String s3Key);

    Optional<EmailForwardingLog> findByS3Key(String s3Key);
}
