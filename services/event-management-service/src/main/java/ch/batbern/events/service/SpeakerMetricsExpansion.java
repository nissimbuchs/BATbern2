package ch.batbern.events.service;

import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Session;
import ch.batbern.events.dto.EventResponse;
import ch.batbern.events.repository.SessionRepository;
import ch.batbern.events.repository.SpeakerPoolRepository;
import ch.batbern.events.sessions.dto.generated.SessionMaterialResponse;
import ch.batbern.shared.exception.NotFoundException;
import ch.batbern.shared.types.SpeakerWorkflowState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The speaker and materials metrics of an event read ({@code include=metrics}, single event only),
 * speakers module. Moved from {@code EventController} unchanged (Story BAT-91 Phase 3, Story 5.9).
 */
@Component
@Order(4)
@RequiredArgsConstructor
@Slf4j
public class SpeakerMetricsExpansion implements EventResponseContributor {

    private final SpeakerPoolRepository speakerPoolRepository;
    private final AgendaConfigResolver agendaConfigResolver;
    private final SessionRepository sessionRepository;
    private final SessionService sessionService;

    @Override
    public Set<String> includes() {
        return Set.of("metrics");
    }

    @Override
    public void expand(String include, Event event, EventResponse response, View view) {
        UUID eventId = event.getId();

        // Count speakers along the content lifecycle (ADR-009 §0.1: CONFIRMED + SLOT_ASSIGNED
        // are removed; the derived is_publishable predicate lands in 11.B.3).
        long acceptedCount = speakerPoolRepository.countByEventIdAndStatus(
                eventId, SpeakerWorkflowState.ACCEPTED);
        long contentSubmittedCount = speakerPoolRepository.countByEventIdAndStatus(
                eventId, SpeakerWorkflowState.CONTENT_SUBMITTED);
        long qualityReviewedCount = speakerPoolRepository.countByEventIdAndStatus(
                eventId, SpeakerWorkflowState.QUALITY_REVIEWED);

        // Total committed speakers (ACCEPTED + later content-lifecycle states).
        // NB: persisted via {@code response.setConfirmedSpeakersCount} for wire-format stability
        // — the field name "confirmed" predates ADR-009's removal of CONFIRMED and is preserved
        // here while OpenAPI changes are owned by Story 11.B.3.
        long totalCommittedSpeakers = acceptedCount + contentSubmittedCount + qualityReviewedCount;

        // Speakers with complete info (submitted materials - CONTENT_SUBMITTED or higher)
        long speakersWithCompleteInfo = contentSubmittedCount + qualityReviewedCount;

        // Pending materials = accepted but haven't submitted content yet
        long pendingMaterials = acceptedCount;

        // Max speaker slots from the effective agenda config (per-event override, else template)
        Integer maxSpeakerSlots = null;
        try {
            maxSpeakerSlots = agendaConfigResolver.resolve(event).getMaxSlots();
        } catch (NotFoundException e) {
            log.debug("No agenda config for event {}: {}", event.getEventCode(), e.getMessage());
        }

        // Story 5.9: Calculate session materials metrics
        // Total = speaker sessions with a timeslot assigned (excludes structural: moderation/break/lunch)
        List<Session> allSessions = sessionRepository.findByEventId(eventId);
        long totalSessions = allSessions.stream()
                .filter(session -> session.getStartTime() != null)
                .filter(session -> !Session.isStructuralType(session.getSessionType()))
                .count();
        long sessionsWithMaterials = allSessions.stream()
                .filter(session -> session.getStartTime() != null)
                .filter(session -> !Session.isStructuralType(session.getSessionType()))
                .filter(session -> {
                    // Count sessions with materialsStatus = COMPLETE
                    List<SessionMaterialResponse> materials =
                            sessionService.toSessionResponse(session, event.getEventCode(), false)
                                    .getMaterials();
                    return materials != null && !materials.isEmpty();
                })
                .count();

        // Set metrics on EventResponse
        response.setConfirmedSpeakersCount((int) totalCommittedSpeakers);
        response.setSpeakersWithCompleteInfoCount((int) speakersWithCompleteInfo);
        response.setPendingMaterialsCount((int) pendingMaterials);
        response.setMaxSpeakerSlots(maxSpeakerSlots);
        response.setSessionsWithMaterialsCount((int) sessionsWithMaterials);
        response.setTotalSessionsCount((int) totalSessions);

        log.debug("Event {} metrics - confirmed: {}, complete info: {}, "
                        + "pending materials: {}, max slots: {}, sessions with materials: {}/{}",
                event.getEventCode(), totalCommittedSpeakers, speakersWithCompleteInfo,
                pendingMaterials, maxSpeakerSlots, sessionsWithMaterials, totalSessions);
    }
}
