package ch.batbern.events.repository;

import ch.batbern.events.domain.MailRecipient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface MailRecipientRepository extends JpaRepository<MailRecipient, UUID> {

    List<MailRecipient> findByMailMessageId(UUID mailMessageId);

    boolean existsBySesMessageId(String sesMessageId);

    /**
     * Story 16.2a: the Message-ID header SES sent, for threading replies (AC3). Set once; a later
     * event of the same message carries the same header.
     */
    @Modifying
    @Query("UPDATE MailRecipient r SET r.messageIdHeader = :header"
            + " WHERE r.sesMessageId = :sesMessageId AND r.messageIdHeader IS NULL")
    int recordMessageIdHeader(@Param("sesMessageId") String sesMessageId, @Param("header") String header);

    /** Story 16.1b: one recipient of an SES message, only from the statuses in {@code from}. */
    @Modifying
    @Query("UPDATE MailRecipient r SET r.status = :status, r.failureReason = :reason, r.updatedAt = :now"
            + " WHERE r.sesMessageId = :sesMessageId AND lower(r.address) = lower(:address)"
            + " AND r.status IN :from")
    int updateStatus(@Param("sesMessageId") String sesMessageId, @Param("address") String address,
            @Param("status") MailRecipient.Status status, @Param("reason") String reason,
            @Param("now") Instant now, @Param("from") Collection<MailRecipient.Status> from);

    /** Story 16.1b: every recipient of an SES message (reject), only from the statuses in {@code from}. */
    @Modifying
    @Query("UPDATE MailRecipient r SET r.status = :status, r.failureReason = :reason, r.updatedAt = :now"
            + " WHERE r.sesMessageId = :sesMessageId AND r.status IN :from")
    int updateStatusForMessage(@Param("sesMessageId") String sesMessageId,
            @Param("status") MailRecipient.Status status, @Param("reason") String reason,
            @Param("now") Instant now, @Param("from") Collection<MailRecipient.Status> from);
}
