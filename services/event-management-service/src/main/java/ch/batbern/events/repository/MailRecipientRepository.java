package ch.batbern.events.repository;

import ch.batbern.events.domain.MailRecipient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MailRecipientRepository extends JpaRepository<MailRecipient, UUID> {

    List<MailRecipient> findByMailMessageId(UUID mailMessageId);
}
