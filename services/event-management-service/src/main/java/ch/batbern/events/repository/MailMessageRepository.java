package ch.batbern.events.repository;

import ch.batbern.events.domain.MailMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MailMessageRepository extends JpaRepository<MailMessage, UUID> {

    Optional<MailMessage> findBySendGroupId(String sendGroupId);

    List<MailMessage> findByEventCodeOrderByCreatedAtDesc(String eventCode);

    @Modifying
    @Query(value = "UPDATE mail_message SET recipient_count = recipient_count + :count WHERE id = :id",
            nativeQuery = true)
    int addRecipients(@Param("id") UUID id, @Param("count") int count);

    /**
     * Retention (Story 16.1a AC6): delete mail whose event date and own creation are both before
     * {@code cutoff}; mail without an event, or whose event no longer exists, by creation alone.
     * Recipients go with it (ON DELETE CASCADE).
     */
    @Modifying
    @Query(value = "DELETE FROM mail_message m WHERE m.created_at < :cutoff AND NOT EXISTS ("
            + "SELECT 1 FROM events e WHERE e.event_code = m.event_code AND e.event_date >= :cutoff)",
            nativeQuery = true)
    int deleteExpired(@Param("cutoff") Instant cutoff);
}
