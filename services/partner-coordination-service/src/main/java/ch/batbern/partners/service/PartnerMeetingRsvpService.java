package ch.batbern.partners.service;

import ch.batbern.partners.domain.PartnerMeeting;
import ch.batbern.partners.domain.PartnerMeetingRsvp;
import ch.batbern.partners.domain.RsvpStatus;
import ch.batbern.partners.dto.MeetingRsvpListResponse;
import ch.batbern.partners.dto.RsvpDTO;
import ch.batbern.partners.dto.RsvpSummary;
import ch.batbern.partners.exception.PartnerNotFoundException;
import ch.batbern.partners.repository.PartnerMeetingRepository;
import ch.batbern.partners.repository.PartnerMeetingRsvpRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

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

        try {
            return doUpsert(meetingId, attendeeEmail, status);
        } catch (DataIntegrityViolationException e) {
            // Concurrent insert by a second SQS delivery won the race — retry as update.
            log.debug("Concurrent RSVP insert detected for meeting={}, email prefix={} — retrying as update",
                    meetingId, emailPrefix(attendeeEmail));
            return doUpsert(meetingId, attendeeEmail, status);
        }
    }

    private PartnerMeetingRsvp doUpsert(UUID meetingId, String attendeeEmail, RsvpStatus status) {
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
     * Build the full RSVP list response for a meeting — loads meeting + RSVPs in one transaction (AC7).
     * Centralises meeting existence check and response assembly here; controller stays thin.
     *
     * @throws PartnerNotFoundException if meetingId does not exist
     */
    @Transactional(readOnly = true)
    public MeetingRsvpListResponse getMeetingRsvpResponse(UUID meetingId) {
        PartnerMeeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new PartnerNotFoundException("Partner meeting not found: " + meetingId));

        List<PartnerMeetingRsvp> rsvps = rsvpRepository.findByMeetingId(meetingId);

        List<RsvpDTO> rsvpDTOs = rsvps.stream()
                .map(r -> RsvpDTO.builder()
                        .attendeeEmail(r.getAttendeeEmail())
                        .status(r.getStatus().name())
                        .respondedAt(r.getRespondedAt())
                        .build())
                .collect(Collectors.toList());

        RsvpSummary summary = RsvpSummary.builder()
                .accepted((int) rsvps.stream().filter(r -> r.getStatus() == RsvpStatus.ACCEPTED).count())
                .declined((int) rsvps.stream().filter(r -> r.getStatus() == RsvpStatus.DECLINED).count())
                .tentative((int) rsvps.stream().filter(r -> r.getStatus() == RsvpStatus.TENTATIVE).count())
                .build();

        return MeetingRsvpListResponse.builder()
                .meetingId(meetingId)
                .inviteSentAt(meeting.getInviteSentAt())
                .rsvps(rsvpDTOs)
                .summary(summary)
                .build();
    }

    private String emailPrefix(String email) {
        return email != null ? email.substring(0, Math.min(5, email.length())) : "?";
    }
}
