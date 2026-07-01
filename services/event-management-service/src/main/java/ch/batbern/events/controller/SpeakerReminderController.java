package ch.batbern.events.controller;

import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.events.exception.EventNotFoundException;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.SpeakerPoolRepository;
import ch.batbern.events.security.SecurityContextHelper;
import ch.batbern.events.service.SpeakerReminderService;
import ch.batbern.events.service.SpeakerReminderService.ManualReminderResult;
import ch.batbern.events.service.SpeakerReminderService.RemindersDisabledException;
import ch.batbern.events.service.SpeakerReminderService.InvalidSpeakerStateException;
import ch.batbern.events.speakers.api.generated.SpeakerRemindersApi;
import ch.batbern.events.speakers.dto.generated.RemindersDisabledResponse;
import ch.batbern.events.speakers.dto.generated.SendReminderRequest;
import ch.batbern.events.speakers.dto.generated.SendReminderResponse;
import ch.batbern.events.speakers.dto.generated.UpdateRemindersDisabledRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * REST Controller for speaker deadline reminder management.
 * Story 6.5: Automated Deadline Reminders (AC6, AC8). All endpoints require ORGANIZER role.
 *
 * <p>API-consolidation Phase 7: implements the generated {@link SpeakerRemindersApi}.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
public class SpeakerReminderController implements SpeakerRemindersApi {

    private final SpeakerReminderService speakerReminderService;
    private final SpeakerPoolRepository speakerPoolRepository;
    private final EventRepository eventRepository;
    private final SecurityContextHelper securityContextHelper;

    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<SendReminderResponse> sendSpeakerReminder(
            String eventCode,
            UUID speakerPoolId,
            SendReminderRequest request) {

        String organizerUsername = securityContextHelper.getCurrentUsername();
        log.info("Manual reminder requested by {} for speaker {} on event {}",
                organizerUsername, speakerPoolId, eventCode);

        Event event = eventRepository.findByEventCode(eventCode)
                .orElseThrow(() -> new EventNotFoundException("Event not found: " + eventCode));

        SpeakerPool speaker = speakerPoolRepository.findById(speakerPoolId)
                .orElseThrow(() -> new IllegalArgumentException("Speaker pool entry not found: " + speakerPoolId));

        if (!speaker.getEventId().equals(event.getId())) {
            throw new IllegalArgumentException("Speaker does not belong to event: " + eventCode);
        }

        if (request.getReminderType() == null || request.getReminderType().isBlank()) {
            throw new IllegalArgumentException("reminderType is required (RESPONSE or CONTENT)");
        }

        String reminderType = request.getReminderType().toUpperCase();
        if (!"RESPONSE".equals(reminderType) && !"CONTENT".equals(reminderType)) {
            throw new IllegalArgumentException(
                    "Invalid reminderType: " + reminderType + ". Must be RESPONSE or CONTENT");
        }

        try {
            ManualReminderResult result = speakerReminderService.sendManualReminder(
                    speakerPoolId, reminderType, request.getTier(), organizerUsername);

            return ResponseEntity.ok(SendReminderResponse.builder()
                    .message("Reminder sent successfully")
                    .tier(result.tier())
                    .emailAddress(result.emailAddress())
                    .build());
        } catch (RemindersDisabledException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(SendReminderResponse.builder()
                            .error("REMINDERS_DISABLED").message(e.getMessage()).build());
        } catch (InvalidSpeakerStateException e) {
            return ResponseEntity.badRequest()
                    .body(SendReminderResponse.builder()
                            .error("INVALID_STATE").message(e.getMessage()).build());
        }
    }

    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    @Transactional
    public ResponseEntity<RemindersDisabledResponse> updateRemindersDisabled(
            String eventCode,
            UUID speakerPoolId,
            UpdateRemindersDisabledRequest request) {

        log.info("Updating reminders disabled for speaker {} on event {}", speakerPoolId, eventCode);

        Event event = eventRepository.findByEventCode(eventCode)
                .orElseThrow(() -> new EventNotFoundException("Event not found: " + eventCode));

        SpeakerPool speaker = speakerPoolRepository.findById(speakerPoolId)
                .orElseThrow(() -> new IllegalArgumentException("Speaker pool entry not found: " + speakerPoolId));

        if (!speaker.getEventId().equals(event.getId())) {
            throw new IllegalArgumentException("Speaker does not belong to event: " + eventCode);
        }

        Boolean remindersDisabled = request.getRemindersDisabled();
        if (remindersDisabled == null) {
            throw new IllegalArgumentException("remindersDisabled field is required");
        }

        speaker.setRemindersDisabled(remindersDisabled);
        speakerPoolRepository.save(speaker);

        return ResponseEntity.ok(RemindersDisabledResponse.builder()
                .speakerPoolId(speakerPoolId.toString())
                .remindersDisabled(remindersDisabled)
                .build());
    }
}
