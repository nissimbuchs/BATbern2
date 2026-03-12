package ch.batbern.partners.service;

import ch.batbern.partners.domain.PartnerMeetingRsvp;
import ch.batbern.partners.domain.RsvpStatus;
import ch.batbern.partners.exception.PartnerNotFoundException;
import ch.batbern.partners.repository.PartnerMeetingRepository;
import ch.batbern.partners.repository.PartnerMeetingRsvpRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for recording and querying partner meeting RSVP responses — Story 10.27 (AC6, AC7).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PartnerMeetingRsvpService {

    private final PartnerMeetingRsvpRepository rsvpRepository;
    private final PartnerMeetingRepository meetingRepository;

    /**
     * Upsert an RSVP record for a partner meeting attendee (AC6).
     *
     * If the attendee has already responded, their existing record is updated (TENTATIVE → ACCEPTED, etc.).
     * If not, a new record is created.
     *
     * @param meetingId      partner meeting UUID
     * @param attendeeEmail  respondent email
     * @param status         parsed RSVP status
     * @return saved or updated RSVP record
     * @throws PartnerNotFoundException if meetingId does not exist
     */
    @Transactional
    public PartnerMeetingRsvp upsertRsvp(UUID meetingId, String attendeeEmail, RsvpStatus status) {
        if (!meetingRepository.existsById(meetingId)) {
            throw new PartnerNotFoundException("Partner meeting not found: " + meetingId);
        }

        Optional<PartnerMeetingRsvp> existing =
                rsvpRepository.findByMeetingIdAndAttendeeEmail(meetingId, attendeeEmail);

        PartnerMeetingRsvp rsvp;
        if (existing.isPresent()) {
            rsvp = existing.get();
            rsvp.setStatus(status);
            rsvp.setRespondedAt(Instant.now());
            log.debug("Updated RSVP for meeting={}, email prefix={}, status={}",
                    meetingId, emailPrefix(attendeeEmail), status);
        } else {
            rsvp = PartnerMeetingRsvp.builder()
                    .meetingId(meetingId)
                    .attendeeEmail(attendeeEmail)
                    .status(status)
                    .respondedAt(Instant.now())
                    .build();
            log.debug("Created RSVP for meeting={}, email prefix={}, status={}",
                    meetingId, emailPrefix(attendeeEmail), status);
        }

        return rsvpRepository.save(rsvp);
    }

    /**
     * Retrieve all RSVP records for a meeting (AC7).
     * Returns an empty list if no responses have been received yet.
     */
    @Transactional(readOnly = true)
    public List<PartnerMeetingRsvp> getRsvps(UUID meetingId) {
        return rsvpRepository.findByMeetingId(meetingId);
    }

    private String emailPrefix(String email) {
        return email != null ? email.substring(0, Math.min(5, email.length())) : "?";
    }
}
