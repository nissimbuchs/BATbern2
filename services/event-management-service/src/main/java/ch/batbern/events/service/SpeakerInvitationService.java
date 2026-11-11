package ch.batbern.events.service;

import ch.batbern.events.domain.OutreachHistory;
import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.events.speakers.dto.generated.SendInvitationRequest;
import ch.batbern.events.speakers.dto.generated.SendInvitationResponse;
import ch.batbern.events.repository.OutreachHistoryRepository;
import ch.batbern.events.security.SecurityContextHelper;
import ch.batbern.events.service.workflow.TransitionPayload;
import ch.batbern.shared.events.SpeakerInvitationSentEvent;
import ch.batbern.shared.types.SpeakerWorkflowState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

/**
 * Service for speaker invitation management.
 * Story 6.1b: Speaker Invitation System
 *
 * Provides the invitation email (AC3). Story 4.10 removed the test-only {@code POST /speakers/invite}
 * (no UI caller): a speaker enters the pool with {@code POST /events/{eventCode}/speakers} and is
 * provisioned at promote, as the organizer UI does.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SpeakerInvitationService {

    private final SecurityContextHelper securityContextHelper;
    private final ApplicationEventPublisher eventPublisher;
    private final OutreachHistoryRepository outreachHistoryRepository;
    private final SpeakerWorkflowService speakerWorkflowService;
    private final PrimarySpeakerResolver primarySpeakerResolver;
    private final SpeakerPoolLookup speakerPoolLookup;

    private static OffsetDateTime toOffset(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }

    /**
     * Send invitation email to a speaker.
     * AC3: Sends personalized email with magic links
     * AC4: Supports i18n (German/English)
     * AC6: Publishes SpeakerInvitationSentEvent
     * Story 6.1c: Accepts email in request for speakers without email in database
     *
     * <p>Story 4.10: the speaker is addressed by its pool entry id ({@code {speakerId}}), loaded
     * scoped to the event; it used to accept a username or a UUID in the same path slot.
     *
     * @param eventCode the event code
     * @param speakerId the speaker pool entry id
     * @param request the send invitation request
     * @return response with updated speaker details
     */
    @Transactional
    public SendInvitationResponse sendInvitation(String eventCode, UUID speakerId, SendInvitationRequest request) {
        log.info("Sending invitation to speaker {} for event {}", speakerId, eventCode);

        // Validate response deadline is in the future. The hand DTO enforced this with
        // @Future; OpenAPI has no future-date keyword, so the generated DTO can't carry it
        // and the check moves here (preserves the deployed 400 on a past deadline).
        if (request.getResponseDeadline() == null
                || !request.getResponseDeadline().isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("Response deadline must be in the future");
        }

        // Validate content deadline if provided
        if (request.getContentDeadline() != null && !deadlinesValid(request)) {
            throw new IllegalArgumentException("Content deadline must be after response deadline");
        }

        // 1-2. The pool entry of this event (ADR-003, Story 4.10): another event's entry is 404,
        //      so no invitation goes out through the wrong event's path.
        SpeakerPool speaker = speakerPoolLookup.inEvent(eventCode, speakerId);

        // 3. Resolve recipient email. Story 11.E.9: the canonical email is on the User
        // (CUMS) record provisioned at CONTACTED → READY. The request.email() override
        // lets the organizer send to a one-shot address (e.g. a forwarder) without
        // changing CUMS; the override does NOT persist anywhere — it flows through the
        // TransitionPayload to the INVITED hook's email service only.
        String resolvedEmail = request.getEmail() != null && !request.getEmail().isBlank()
                ? request.getEmail()
                : primarySpeakerResolver.resolveEmail(speaker).orElse(null);
        if (resolvedEmail == null) {
            throw new IllegalArgumentException("Speaker email is required to send invitation");
        }

        // 4. Pre-mutate invitation params on the speaker (organizer-supplied; not state changes).
        speaker.setResponseDeadline(request.getResponseDeadline());
        speaker.setContentDeadline(request.getContentDeadline());

        // 5. Delegate to SpeakerWorkflowService.transition() — sole writer per ADR-009.
        //    The INVITED side-effect hook generates magic-link tokens, sends the invitation
        //    email, sets invitedAt, and writes the speaker_status_history row.
        //    The slot-capacity gate (READY → INVITED) is enforced inside transition()
        //    and surfaces as SlotCapacityReachedException → HTTP 409 from GlobalExceptionHandler.
        String currentUser = securityContextHelper.getCurrentUsername();
        String auditActor = currentUser != null ? currentUser : "system";
        TransitionPayload payload = TransitionPayload.builder()
                .email(resolvedEmail)
                .reason("Invitation email sent")
                .inviteContext(Map.of("locale", request.getLocale() != null ? request.getLocale() : "de"))
                .build();

        speakerWorkflowService.transition(speaker.getId(), SpeakerWorkflowState.INVITED, auditActor, payload);

        // 5. Reload to pick up invitedAt (set by INVITED hook) for the response DTO.
        SpeakerPool updated = speakerPoolLookup.inEvent(eventCode, speaker.getId());
        // The speaker's username (session_users primary speaker, provisioned at READY).
        PrimarySpeakerResolver.PrimarySpeakerProfile profile =
                primarySpeakerResolver.resolve(updated).orElse(null);
        String username = profile != null ? profile.username() : null;

        // 6. Record outreach history for the automated email invitation (audit, not state).
        OutreachHistory outreach = new OutreachHistory();
        outreach.setSpeakerPoolId(updated.getId());
        outreach.setContactDate(updated.getInvitedAt() != null ? updated.getInvitedAt() : Instant.now());
        outreach.setContactMethod("email");
        outreach.setNotes("Automated invitation email sent via speaker portal");
        outreach.setOrganizerUsername(currentUser != null ? currentUser : "system");
        outreachHistoryRepository.save(outreach);
        log.debug("Created outreach history for invitation to speaker {}", speakerId);

        // 7. Publish domain event (AC6). Story 11.E.9: email comes from the resolved
        // address used above (may be the request override or CUMS-resolved).
        SpeakerInvitationSentEvent sentEvent = new SpeakerInvitationSentEvent(
                updated.getId(),
                eventCode,
                username,
                resolvedEmail,
                currentUser
        );
        eventPublisher.publishEvent(sentEvent);
        log.debug("Published SpeakerInvitationSentEvent for speaker {} in event {}", speakerId, eventCode);

        log.info("Invitation sent to speaker {} for event {}", speakerId, eventCode);

        // Response identity: the current primary speaker (session_users + CUMS). The email may
        // differ from resolvedEmail if the organizer used a one-shot override.
        return new SendInvitationResponse()
                .speakerPoolId(updated.getId())
                .username(username)
                .email(profile != null && profile.email() != null ? profile.email() : resolvedEmail)
                .status(SendInvitationResponse.StatusEnum.fromValue(updated.getStatus().name()))
                .invitedAt(toOffset(updated.getInvitedAt()))
                .responseDeadline(updated.getResponseDeadline())
                .contentDeadline(updated.getContentDeadline());
    }

    /**
     * Content deadline must be after the response deadline when both are present
     * (replaces the hand DTO's areDeadlinesValid() helper after the generated-DTO swap).
     */
    private static boolean deadlinesValid(SendInvitationRequest r) {
        if (r.getContentDeadline() == null) {
            return true;
        }
        return r.getContentDeadline().isAfter(r.getResponseDeadline());
    }
}
