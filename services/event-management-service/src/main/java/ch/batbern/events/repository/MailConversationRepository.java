package ch.batbern.events.repository;

import ch.batbern.events.domain.MailConversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MailConversationRepository extends JpaRepository<MailConversation, UUID> {

    Optional<MailConversation> findByConversationKey(String conversationKey);

    Optional<MailConversation> findByReplyToken(String replyToken);

    /**
     * Retention (Story 16.2a AC14): delete conversations whose last activity and event date are both
     * before {@code cutoff}; without an event, or whose event no longer exists, by last activity
     * alone. Their messages go with them (ON DELETE CASCADE).
     */
    @Modifying
    @Query(value = "DELETE FROM mail_conversation c WHERE c.last_message_at < :cutoff AND NOT EXISTS ("
            + "SELECT 1 FROM events e WHERE e.event_code = c.event_code AND e.event_date >= :cutoff)",
            nativeQuery = true)
    int deleteExpired(@Param("cutoff") Instant cutoff);
}
