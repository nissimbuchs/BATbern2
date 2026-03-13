package ch.batbern.partners.service;

import ch.batbern.partners.client.EventManagementClient;
import ch.batbern.partners.client.UserServiceClient;
import ch.batbern.partners.client.dto.EventSummaryDTO;
import ch.batbern.partners.client.user.dto.UserResponse;
import ch.batbern.partners.domain.PartnerMeeting;
import ch.batbern.partners.dto.CreateMeetingRequest;
import ch.batbern.partners.dto.PartnerMeetingDTO;
import ch.batbern.partners.dto.SendInviteResponse;
import ch.batbern.partners.dto.UpdateMeetingRequest;
import ch.batbern.partners.exception.PartnerNotFoundException;
import ch.batbern.partners.repository.PartnerMeetingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for managing partner meetings — Story 8.3: Partner Meeting Coordination.
 *
 * Handles CRUD operations (AC1, AC2, AC4, AC5) and calendar invite sending (AC3, AC8).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PartnerMeetingService {

    private final PartnerMeetingRepository meetingRepository;
    private final EventManagementClient eventManagementClient;
    private final UserServiceClient userServiceClient;
    private final IcsGeneratorService icsGeneratorService;
    private final PartnerInviteEmailService inviteEmailService;

    /**
     * List all partner meetings sorted by date descending (AC5).
     */
    @Transactional(readOnly = true)
    public List<PartnerMeetingDTO> getMeetings() {
        return meetingRepository.findAllByOrderByMeetingDateDesc()
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * Get a single meeting by ID.
     */
    @Transactional(readOnly = true)
    public PartnerMeetingDTO getMeeting(UUID meetingId) {
        PartnerMeeting meeting = findMeetingById(meetingId);
        return toDTO(meeting);
    }

    /**
     * Create a new partner meeting linked to a BATbern event (AC1).
     *
     * The meeting date is auto-populated from the event-management-service.
     *
     * @param request         meeting creation details
     * @param organizerUsername ADR-003: username of the creating organizer
     */
    @Transactional
    public PartnerMeetingDTO createMeeting(CreateMeetingRequest request, String organizerUsername) {
        log.info("Creating partner meeting: eventCode={}, type={}, organizer={}",
                request.getEventCode(), request.getMeetingType(), organizerUsername);

        // Fetch event details to auto-populate the meeting date (AC1)
        EventSummaryDTO event = eventManagementClient.getEventSummary(request.getEventCode());

        PartnerMeeting meeting = PartnerMeeting.builder()
                .eventCode(request.getEventCode())
                .meetingType(request.getMeetingType())
                .meetingDate(event.eventDate())  // auto-filled from event (AC1)
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .location(request.getLocation())
                .agenda(request.getAgenda())
                .createdBy(organizerUsername)
                .build();

        PartnerMeeting saved = meetingRepository.save(meeting);
        log.info("Partner meeting created: id={}, eventCode={}", saved.getId(), saved.getEventCode());
        return toDTO(saved);
    }

    /**
     * Update agenda and/or notes for a meeting (AC2, AC4).
     * All fields optional — only non-null values are applied.
     */
    @Transactional
    public PartnerMeetingDTO updateMeeting(UUID meetingId, UpdateMeetingRequest request) {
        PartnerMeeting meeting = findMeetingById(meetingId);

        if (request.getAgenda() != null) {
            meeting.setAgenda(request.getAgenda());
        }
        if (request.getNotes() != null) {
            meeting.setNotes(request.getNotes());
        }
        if (request.getLocation() != null) {
            meeting.setLocation(request.getLocation());
        }
        if (request.getStartTime() != null) {
            meeting.setStartTime(request.getStartTime());
        }
        if (request.getEndTime() != null) {
            meeting.setEndTime(request.getEndTime());
        }

        return toDTO(meetingRepository.save(meeting));
    }

    /**
     * Send the calendar invite to all partner contacts (AC3, AC8).
     *
     * 1. Load meeting + fetch BATbern event details
     * 2. Generate ICS file with two VEVENTs
     * 3. Collect all partner contact emails via UserServiceClient
     * 4. Send async via PartnerInviteEmailService
     * 5. Update invite_sent_at timestamp
     *
     * Returns immediately with recipient count — email is dispatched asynchronously.
     */
    @Transactional
    public SendInviteResponse sendInvite(UUID meetingId) {
        PartnerMeeting meeting = findMeetingById(meetingId);

        log.info("Sending partner meeting invite: meetingId={}, eventCode={}", meetingId, meeting.getEventCode());

        // Fetch linked event details for ICS
        EventSummaryDTO event = eventManagementClient.getEventSummary(meeting.getEventCode());

        // Collect all invite recipients BEFORE ICS generation so ATTENDEE lines are included (AC1)
        List<String> emails = collectInviteRecipientEmails();

        // Increment SEQUENCE before ICS generation (RFC 5545: higher SEQUENCE = update)
        meeting.setInviteSequence(meeting.getInviteSequence() + 1);

        // Generate ICS content with recipient emails for ATTENDEE fields (Story 10.27 AC1)
        byte[] icsContent = icsGeneratorService.generate(meeting, event, emails);

        // Dispatch emails asynchronously (AC8 — returns 202 immediately)
        var inviteDetails = new PartnerInviteEmailService.MeetingInviteDetails(
                event.title(),
                meeting.getEventCode(),
                meeting.getMeetingDate(),
                meeting.getStartTime(),
                meeting.getEndTime(),
                meeting.getLocation()
        );
        inviteEmailService.sendCalendarInvites(emails, inviteDetails, icsContent);

        // Persist updated sequence and invite_sent_at together
        meeting.setInviteSentAt(Instant.now());
        meetingRepository.save(meeting);

        log.info("Partner meeting invite queued for {} recipients, meetingId={}", emails.size(), meetingId);

        return SendInviteResponse.builder()
                .message("Calendar invite is being sent to all partner contacts")
                .meetingId(meetingId)
                .recipientCount(emails.size())
                .build();
    }

    /**
     * Delete a partner meeting.
     *
     * If a calendar invite has already been sent (inviteSentAt != null), a METHOD:CANCEL ICS
     * is generated and sent to all partners and organizers so their calendar clients remove
     * the entry automatically. The email is dispatched asynchronously.
     *
     * RSVPs are removed automatically via ON DELETE CASCADE (V9 migration).
     *
     * @param meetingId the meeting to delete
     */
    @Transactional
    public void deleteMeeting(UUID meetingId) {
        PartnerMeeting meeting = findMeetingById(meetingId);
        log.info("Deleting partner meeting: id={}, eventCode={}, inviteSent={}",
                meetingId, meeting.getEventCode(), meeting.getInviteSentAt() != null);

        if (meeting.getInviteSentAt() != null) {
            // Fetch event title for the cancellation email body
            EventSummaryDTO event = eventManagementClient.getEventSummary(meeting.getEventCode());
            List<String> emails = collectInviteRecipientEmails();
            byte[] cancelIcs = icsGeneratorService.generateCancelIcs(meeting);
            inviteEmailService.sendCancellationNotice(
                    emails,
                    event.title(),
                    meeting.getEventCode(),
                    meeting.getMeetingDate(),
                    cancelIcs
            );
            log.info("Cancellation notice queued for {} recipients, meetingId={}", emails.size(), meetingId);
        }

        meetingRepository.delete(meeting);
    }

    /**
     * Collect all unique invite recipient emails: partners + organizers.
     *
     * Partners receive the invite as the primary audience.
     * Organizers are included so they have the calendar entry for coordination.
     * Duplicates (e.g. a user with both roles) are removed.
     *
     * Silently tolerates User Service unavailability per role (logs warning).
     */
    private List<String> collectInviteRecipientEmails() {
        List<String> emails = new ArrayList<>();
        emails.addAll(fetchEmailsByRole("PARTNER"));
        emails.addAll(fetchEmailsByRole("ORGANIZER"));

        List<String> deduplicated = emails.stream()
                .distinct()
                .collect(Collectors.toList());

        log.debug("Collected {} invite recipient emails (partners + organizers)", deduplicated.size());
        return deduplicated;
    }

    private List<String> fetchEmailsByRole(String role) {
        try {
            return userServiceClient.getUsersByRole(role).stream()
                    .filter(u -> u.getEmail() != null && !u.getEmail().isBlank())
                    .map(UserResponse::getEmail)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("Could not fetch {} emails from User Service: {}", role, e.getMessage());
            return new ArrayList<>();
        }
    }

    private PartnerMeeting findMeetingById(UUID meetingId) {
        return meetingRepository.findById(meetingId)
                .orElseThrow(() -> new PartnerNotFoundException(
                        "Partner meeting not found: " + meetingId));
    }

    private PartnerMeetingDTO toDTO(PartnerMeeting m) {
        return PartnerMeetingDTO.builder()
                .id(m.getId())
                .eventCode(m.getEventCode())
                .meetingType(m.getMeetingType())
                .meetingDate(m.getMeetingDate())
                .startTime(m.getStartTime())
                .endTime(m.getEndTime())
                .location(m.getLocation())
                .agenda(m.getAgenda())
                .notes(m.getNotes())
                .inviteSentAt(m.getInviteSentAt())
                .createdBy(m.getCreatedBy())
                .createdAt(m.getCreatedAt())
                .updatedAt(m.getUpdatedAt())
                .build();
    }
}
