package ch.batbern.events.repository;

import ch.batbern.events.domain.SessionQnaNotification;
import ch.batbern.events.domain.SessionQnaNotificationId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link SessionQnaNotification} digest state (Story 15.7).
 */
@Repository
public interface SessionQnaNotificationRepository
        extends JpaRepository<SessionQnaNotification, SessionQnaNotificationId> {

    Optional<SessionQnaNotification> findByIdWindowIdAndIdRecipientUsername(
            UUID windowId, String recipientUsername);
}
