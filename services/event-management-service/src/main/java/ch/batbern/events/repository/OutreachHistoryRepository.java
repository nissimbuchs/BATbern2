package ch.batbern.events.repository;

import ch.batbern.events.domain.OutreachHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for OutreachHistory entity.
 * Story 5.3: Speaker Outreach Tracking
 *
 * Provides data access for speaker outreach contact history.
 * Supports querying outreach attempts by speaker and chronological ordering.
 */
@Repository
public interface OutreachHistoryRepository extends JpaRepository<OutreachHistory, UUID> {

    /**
     * Find all outreach attempts for a specific speaker.
     *
     * @param speakerPoolId UUID of the speaker in the speaker pool
     * @return List of outreach history records
     */
    List<OutreachHistory> findBySpeakerPoolId(UUID speakerPoolId);

    /**
     * Find all outreach attempts for a specific speaker, ordered by contact date (most recent first).
     *
     * @param speakerPoolId UUID of the speaker in the speaker pool
     * @return List of outreach history records ordered by contact_date DESC
     */
    List<OutreachHistory> findBySpeakerPoolIdOrderByContactDateDesc(UUID speakerPoolId);

    /**
     * Find all outreach attempts by a specific organizer.
     * Useful for organizer activity reports.
     *
     * @param organizerUsername Username of the organizer
     * @return List of outreach history records
     */
    List<OutreachHistory> findByOrganizerUsername(String organizerUsername);
}
