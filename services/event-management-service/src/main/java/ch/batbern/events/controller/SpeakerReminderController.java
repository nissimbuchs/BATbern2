package ch.batbern.events.controller;

import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.events.repository.SpeakerPoolRepository;
import ch.batbern.events.service.SpeakerPoolLookup;
import ch.batbern.events.speakers.api.generated.SpeakerRemindersApi;
import ch.batbern.events.speakers.dto.generated.RemindersDisabledResponse;
import ch.batbern.events.speakers.dto.generated.UpdateRemindersDisabledRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * REST Controller for speaker deadline reminder management.
 * Story 6.5: Automated Deadline Reminders (AC6). All endpoints require ORGANIZER role. A manual
 * reminder goes out through the Mail Center composer, speakers audience (Story 16.8; the
 * single-speaker send-reminder endpoint went with 16.8c).
 *
 * <p>API-consolidation Phase 7: implements the generated {@link SpeakerRemindersApi}.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
public class SpeakerReminderController implements SpeakerRemindersApi {

    private final SpeakerPoolRepository speakerPoolRepository;
    private final SpeakerPoolLookup speakerPoolLookup;

    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    @Transactional
    public ResponseEntity<RemindersDisabledResponse> updateRemindersDisabled(
            String eventCode,
            UUID speakerPoolId,
            UpdateRemindersDisabledRequest request) {

        log.info("Updating reminders disabled for speaker {} on event {}", speakerPoolId, eventCode);

        // Scoped to the event in the path (ADR-003, Story 4.10): another event's entry is 404.
        SpeakerPool speaker = speakerPoolLookup.inEvent(eventCode, speakerPoolId);

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
