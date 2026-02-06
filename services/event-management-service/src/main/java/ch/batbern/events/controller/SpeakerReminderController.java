package ch.batbern.events.controller;

import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.events.dto.SendReminderRequest;
import ch.batbern.events.exception.EventNotFoundException;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.SpeakerPoolRepository;
import ch.batbern.events.security.SecurityContextHelper;
import ch.batbern.events.service.SpeakerReminderService;
import ch.batbern.events.service.SpeakerReminderService.ManualReminderResult;
import ch.batbern.events.service.SpeakerReminderService.RemindersDisabledException;
import ch.batbern.events.service.SpeakerReminderService.InvalidSpeakerStateException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * REST Controller for speaker deadline reminder management.
 * Story 6.5: Automated Deadline Reminders (AC6, AC8)
 *
 * Provides endpoints for:
 * - Manually triggering a reminder for a specific speaker (AC8)
 * - Toggling reminders disabled flag (AC6)
 *
 * All endpoints require ORGANIZER role (JWT auth, not magic link).
 */
@RestController
@RequestMapping("/api/v1/events/{eventCode}/speaker-pool/{speakerPoolId}")
@RequiredArgsConstructor
@Slf4j
public class SpeakerReminderController {

    private final SpeakerReminderService speakerReminderService;
    private final SpeakerPoolRepository speakerPoolRepository;
    private final EventRepository eventRepository;
    private final SecurityContextHelper securityContextHelper;

    /**
     * Manually trigger a reminder email for a specific speaker (AC8).
     * Bypasses deduplication but respects reminders_disabled flag.
     *
     * @param eventCode the event code
     * @param speakerPoolId the speaker pool entry ID
     * @param request the reminder request (type + optional tier)
     * @return 200 OK with tier used and email address
     */
    @PostMapping("/send-reminder")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<Map<String, String>> sendReminder(
            @PathVariable String eventCode,
            @PathVariable UUID speakerPoolId,
            @RequestBody SendReminderRequest request) {

        String organizerUsername = securityContextHelper.getCurrentUsername();
        log.info("Manual reminder requested by {} for speaker {} on event {}",
                organizerUsername, speakerPoolId, eventCode);

        // Validate event exists and speaker belongs to it
        Event event = eventRepository.findByEventCode(eventCode)
                .orElseThrow(() -> new EventNotFoundException("Event not found: " + eventCode));

        SpeakerPool speaker = speakerPoolRepository.findById(speakerPoolId)
                .orElseThrow(() -> new IllegalArgumentException("Speaker pool entry not found: " + speakerPoolId));

        if (!speaker.getEventId().equals(event.getId())) {
            throw new IllegalArgumentException("Speaker does not belong to event: " + eventCode);
        }

        // Validate reminder type
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

            return ResponseEntity.ok(Map.of(
                    "message", "Reminder sent successfully",
                    "tier", result.tier(),
                    "emailAddress", result.emailAddress()
            ));
        } catch (RemindersDisabledException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "REMINDERS_DISABLED", "message", e.getMessage()));
        } catch (InvalidSpeakerStateException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "INVALID_STATE", "message", e.getMessage()));
        }
    }

    /**
     * Toggle reminders disabled flag for a specific speaker (AC6).
     *
     * @param eventCode the event code
     * @param speakerPoolId the speaker pool entry ID
     * @param request body with remindersDisabled field
     * @return 200 OK with updated flag
     */
    @PatchMapping("/reminders")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<Map<String, Object>> updateRemindersDisabled(
            @PathVariable String eventCode,
            @PathVariable UUID speakerPoolId,
            @RequestBody Map<String, Boolean> request) {

        log.info("Updating reminders disabled for speaker {} on event {}", speakerPoolId, eventCode);

        // Validate event exists
        Event event = eventRepository.findByEventCode(eventCode)
                .orElseThrow(() -> new EventNotFoundException("Event not found: " + eventCode));

        SpeakerPool speaker = speakerPoolRepository.findById(speakerPoolId)
                .orElseThrow(() -> new IllegalArgumentException("Speaker pool entry not found: " + speakerPoolId));

        if (!speaker.getEventId().equals(event.getId())) {
            throw new IllegalArgumentException("Speaker does not belong to event: " + eventCode);
        }

        Boolean remindersDisabled = request.get("remindersDisabled");
        if (remindersDisabled == null) {
            throw new IllegalArgumentException("remindersDisabled field is required");
        }

        speaker.setRemindersDisabled(remindersDisabled);
        speakerPoolRepository.save(speaker);

        return ResponseEntity.ok(Map.of(
                "speakerPoolId", speakerPoolId.toString(),
                "remindersDisabled", remindersDisabled
        ));
    }
}
