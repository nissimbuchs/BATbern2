package ch.batbern.partners.repository;

import ch.batbern.partners.domain.PartnerNote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for PartnerNote — Story 8.4.
 */
public interface PartnerNoteRepository extends JpaRepository<PartnerNote, UUID> {

    List<PartnerNote> findByPartnerIdOrderByCreatedAtDesc(UUID partnerId);
}
