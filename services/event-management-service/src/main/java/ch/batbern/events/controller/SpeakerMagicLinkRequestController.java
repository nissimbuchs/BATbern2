package ch.batbern.events.controller;

import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.events.dto.SpeakerMagicLinkRequestDto;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.SpeakerPoolRepository;
import ch.batbern.events.service.MagicLinkService;
import ch.batbern.events.service.SpeakerInvitationEmailService;
import ch.batbern.shared.types.SpeakerWorkflowState;
import ch.batbern.shared.types.TokenAction;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Controller for magic link re-send requests (Story 9.3 Task 5).
 *
 * Flow:
 * 1. Validate email (AC6 — @Email prevents M4 injection)
 * 2. Look up SpeakerPool with INVITED or ACCEPTED status by email
 * 3. If found: generate new tokens and send invitation email (reuses existing email service)
 * 4. Always return HTTP 200 (no email enumeration — AC3)
 *
 * AC3: Always returns 200 regardless of whether email matches a speaker.
 * AC6: @Email validation prevents email injection.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class SpeakerMagicLinkRequestController {

    private static final Logger LOG = LoggerFactory.getLogger(SpeakerMagicLinkRequestController.class);

    private final SpeakerPoolRepository speakerPoolRepository;
    private final EventRepository eventRepository;
    private final MagicLinkService magicLinkService;
    private final SpeakerInvitationEmailService speakerInvitationEmailService;

    public SpeakerMagicLinkRequestController(
            SpeakerPoolRepository speakerPoolRepository,
            EventRepository eventRepository,
            MagicLinkService magicLinkService,
            SpeakerInvitationEmailService speakerInvitationEmailService) {
        this.speakerPoolRepository = speakerPoolRepository;
        this.eventRepository = eventRepository;
        this.magicLinkService = magicLinkService;
        this.speakerInvitationEmailService = speakerInvitationEmailService;
    }

    /**
     * POST /api/v1/auth/speaker-request-magic-link
     *
     * Accepts an email, looks up an invited/accepted speaker, and sends a new magic link.
     * Always returns 200 — no email enumeration (AC3).
     */
    @PostMapping("/speaker-request-magic-link")
    public ResponseEntity<Void> requestMagicLink(@Valid @RequestBody SpeakerMagicLinkRequestDto request) {
        Optional<SpeakerPool> speakerPoolOpt = speakerPoolRepository
                .findFirstByEmailAndStatusInOrderByCreatedAtDesc(
                        request.email(),
                        List.of(SpeakerWorkflowState.INVITED, SpeakerWorkflowState.ACCEPTED)
                );

        if (speakerPoolOpt.isPresent()) {
            SpeakerPool speakerPool = speakerPoolOpt.get();
            Optional<Event> eventOpt = eventRepository.findById(speakerPool.getEventId());

            if (eventOpt.isPresent()) {
                LOG.info("Magic link re-send requested for speakerPool: {}", speakerPool.getId());
                try {
                    String respondToken = magicLinkService.generateToken(speakerPool.getId(), TokenAction.RESPOND);
                    String dashboardToken = magicLinkService.generateToken(speakerPool.getId(), TokenAction.VIEW);
                    speakerInvitationEmailService.sendInvitationEmail(
                            speakerPool, eventOpt.get(), respondToken, dashboardToken, Locale.GERMAN);
                } catch (Exception e) {
                    // Log but don't expose error — caller always receives 200
                    LOG.error("Failed to send magic link email for speakerPool {}: {}",
                            speakerPool.getId(), e.getMessage());
                }
            } else {
                LOG.warn("Event not found for speakerPool: {}", speakerPool.getId());
            }
        } else {
            // No enumeration — silently skip unknown emails
            LOG.debug("Magic link re-send: no matching speaker found (suppressed for anti-enumeration)");
        }

        return ResponseEntity.ok().build();
    }
}
