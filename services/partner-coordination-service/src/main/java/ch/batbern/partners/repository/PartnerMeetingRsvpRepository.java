package ch.batbern.partners.repository;

import ch.batbern.partners.domain.PartnerMeetingRsvp;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for partner meeting RSVP records — Story 10.27 (AC5).
 */
public interface PartnerMeetingRsvpRepository extends JpaRepository<PartnerMeetingRsvp, UUID> {

    List<PartnerMeetingRsvp> findByMeetingId(UUID meetingId);

    /** Case-insensitive on purpose (#1099): mail clients vary the capitalisation of ATTENDEE. */
    Optional<PartnerMeetingRsvp> findByMeetingIdAndAttendeeEmailIgnoreCase(UUID meetingId, String attendeeEmail);
}
