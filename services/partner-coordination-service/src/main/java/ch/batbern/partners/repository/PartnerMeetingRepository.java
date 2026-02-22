package ch.batbern.partners.repository;

import ch.batbern.partners.domain.PartnerMeeting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for PartnerMeeting entities — Story 8.3.
 */
@Repository
public interface PartnerMeetingRepository extends JpaRepository<PartnerMeeting, UUID> {

    /**
     * All meetings sorted by meeting date descending (AC5 — most recent first).
     */
    List<PartnerMeeting> findAllByOrderByMeetingDateDesc();

    /**
     * Meetings for a specific event (used by usePartnerMeetings hook — Story 8.0 integration).
     */
    List<PartnerMeeting> findByEventCodeOrderByMeetingDateDesc(String eventCode);
}
