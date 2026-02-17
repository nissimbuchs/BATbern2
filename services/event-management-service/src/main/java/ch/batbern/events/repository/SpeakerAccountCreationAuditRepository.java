package ch.batbern.events.repository;

import ch.batbern.events.domain.SpeakerAccountCreationAudit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Story 9.2 AC4: Repository for speaker account creation audit records.
 */
public interface SpeakerAccountCreationAuditRepository extends JpaRepository<SpeakerAccountCreationAudit, UUID> {

    List<SpeakerAccountCreationAudit> findBySpeakerPoolId(UUID speakerPoolId);

    boolean existsBySpeakerPoolIdAndAction(UUID speakerPoolId, SpeakerAccountCreationAudit.Action action);
}
