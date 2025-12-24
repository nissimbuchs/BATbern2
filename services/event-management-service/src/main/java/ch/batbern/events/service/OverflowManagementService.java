package ch.batbern.events.service;

import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.SpeakerPoolRepository;
import ch.batbern.shared.exception.NotFoundException;
import ch.batbern.shared.types.SpeakerWorkflowState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Overflow Management Service - handles speaker overflow situations.
 * Architecture: 06a-workflow-state-machines.md (lines 629-738)
 *
 * Handles:
 * - Detecting overflow (more accepted speakers than available slots)
 * - Moving excess speakers to OVERFLOW state
 * - Promoting speakers from overflow when slots open up
 * - Overflow voting and selection (future enhancement)
 *
 * Overflow occurs when:
 * - Number of ACCEPTED speakers > event_type.maxSlots
 * - Speakers beyond maxSlots are moved to OVERFLOW state
 * - Overflow speakers can be promoted back to ACCEPTED if slots open
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class OverflowManagementService {

    private final EventRepository eventRepository;
    private final SpeakerPoolRepository speakerPoolRepository;
    private final SpeakerWorkflowService speakerWorkflowService;
    private final EventTypeService eventTypeService;

    /**
     * Detect and handle overflow situation for an event.
     *
     * This method:
     * 1. Counts accepted speakers vs. max slots
     * 2. If overflow detected, moves excess speakers to OVERFLOW state
     * 3. Prioritizes speakers by acceptance order (FIFO)
     *
     * @param eventCode Event code
     * @param organizerUsername Organizer triggering the check
     * @return Number of speakers moved to overflow
     */
    public int detectAndHandleOverflow(String eventCode, String organizerUsername) {
        Event event = eventRepository.findByEventCode(eventCode)
                .orElseThrow(() -> new NotFoundException("Event not found: " + eventCode));

        // Get max slots from event type configuration
        int maxSlots = eventTypeService.getEventType(event.getEventType()).getMaxSlots();

        // Get all accepted speakers (not in OVERFLOW, DECLINED, WITHDREW states)
        List<SpeakerPool> acceptedSpeakers = speakerPoolRepository.findByEventId(event.getId()).stream()
                .filter(speaker -> speaker.getStatus() == SpeakerWorkflowState.ACCEPTED
                        || speaker.getStatus() == SpeakerWorkflowState.CONTENT_SUBMITTED
                        || speaker.getStatus() == SpeakerWorkflowState.QUALITY_REVIEWED
                        || speaker.getStatus() == SpeakerWorkflowState.SLOT_ASSIGNED
                        || speaker.getStatus() == SpeakerWorkflowState.CONFIRMED)
                .sorted(Comparator.comparing(SpeakerPool::getCreatedAt)) // FIFO: earliest accepted first
                .toList();

        int acceptedCount = acceptedSpeakers.size();

        if (acceptedCount <= maxSlots) {
            log.debug("No overflow detected for event {} ({} accepted, {} max slots)",
                    eventCode, acceptedCount, maxSlots);
            return 0;
        }

        // Overflow detected - move excess speakers to overflow
        int overflowCount = acceptedCount - maxSlots;
        List<SpeakerPool> overflowSpeakers = acceptedSpeakers.subList(maxSlots, acceptedCount);

        log.warn("Overflow detected for event {}: {} accepted speakers, {} max slots. Moving {} speakers to overflow.",
                eventCode, acceptedCount, maxSlots, overflowCount);

        for (SpeakerPool speaker : overflowSpeakers) {
            // Only move to overflow if currently in ACCEPTED state (don't demote confirmed speakers)
            if (speaker.getStatus() == SpeakerWorkflowState.ACCEPTED) {
                speakerWorkflowService.updateSpeakerWorkflowState(
                        speaker.getId(),
                        SpeakerWorkflowState.OVERFLOW,
                        organizerUsername
                );
                log.info("Moved speaker {} to OVERFLOW for event {}", speaker.getId(), eventCode);
            }
        }

        // TODO: Publish OverflowDetectedEvent for notification system

        return overflowCount;
    }

    /**
     * Promote a speaker from overflow back to ACCEPTED state.
     *
     * This is triggered when:
     * - A speaker withdraws, opening up a slot
     * - A speaker is declined, opening up a slot
     * - Event max slots are increased
     *
     * @param eventCode Event code
     * @param speakerId Speaker pool ID to promote
     * @param organizerUsername Organizer approving the promotion
     * @throws IllegalStateException if speaker not in OVERFLOW state or no slots available
     */
    public void promoteSpeakerFromOverflow(
            String eventCode,
            UUID speakerId,
            String organizerUsername
    ) {
        Event event = eventRepository.findByEventCode(eventCode)
                .orElseThrow(() -> new NotFoundException("Event not found: " + eventCode));

        SpeakerPool speaker = speakerPoolRepository.findById(speakerId)
                .orElseThrow(() -> new NotFoundException("Speaker not found: " + speakerId));

        // Validate speaker is in OVERFLOW state
        if (speaker.getStatus() != SpeakerWorkflowState.OVERFLOW) {
            throw new IllegalStateException(
                    String.format("Speaker %s is not in OVERFLOW state (current: %s)",
                            speakerId, speaker.getStatus()));
        }

        // Check if slots are available
        int maxSlots = eventTypeService.getEventType(event.getEventType()).getMaxSlots();
        long acceptedCount = countAcceptedSpeakers(event.getId());

        if (acceptedCount >= maxSlots) {
            throw new IllegalStateException(
                    String.format("Cannot promote speaker - all slots full (%d accepted, %d max slots)",
                            acceptedCount, maxSlots));
        }

        // Promote speaker back to ACCEPTED
        speakerWorkflowService.updateSpeakerWorkflowState(
                speakerId,
                SpeakerWorkflowState.ACCEPTED,
                organizerUsername
        );

        log.info("Promoted speaker {} from OVERFLOW to ACCEPTED for event {} by organizer {}",
                speakerId, eventCode, organizerUsername);
    }

    /**
     * Auto-promote the next overflow speaker when a slot opens up.
     *
     * This method automatically promotes the longest-waiting overflow speaker
     * when a slot becomes available (e.g., due to withdrawal or decline).
     *
     * @param eventCode Event code
     * @param organizerUsername Organizer username for audit trail
     * @return ID of promoted speaker, or null if no overflow speakers available
     */
    public UUID autoPromoteNextOverflowSpeaker(String eventCode, String organizerUsername) {
        Event event = eventRepository.findByEventCode(eventCode)
                .orElseThrow(() -> new NotFoundException("Event not found: " + eventCode));

        // Get overflow speakers sorted by created_at (FIFO)
        List<SpeakerPool> overflowSpeakers = speakerPoolRepository.findByEventId(event.getId()).stream()
                .filter(speaker -> speaker.getStatus() == SpeakerWorkflowState.OVERFLOW)
                .sorted(Comparator.comparing(SpeakerPool::getCreatedAt))
                .toList();

        if (overflowSpeakers.isEmpty()) {
            log.debug("No overflow speakers available for auto-promotion in event {}", eventCode);
            return null;
        }

        // Check if slot is available
        int maxSlots = eventTypeService.getEventType(event.getEventType()).getMaxSlots();
        long acceptedCount = countAcceptedSpeakers(event.getId());

        if (acceptedCount >= maxSlots) {
            log.warn("Cannot auto-promote overflow speaker - no slots available ({} accepted, {} max)",
                    acceptedCount, maxSlots);
            return null;
        }

        // Promote first overflow speaker (longest waiting)
        SpeakerPool nextSpeaker = overflowSpeakers.get(0);
        promoteSpeakerFromOverflow(eventCode, nextSpeaker.getId(), organizerUsername);

        return nextSpeaker.getId();
    }

    /**
     * Get all overflow speakers for an event.
     *
     * @param eventCode Event code
     * @return List of speakers in OVERFLOW state
     */
    @Transactional(readOnly = true)
    public List<SpeakerPool> getOverflowSpeakers(String eventCode) {
        Event event = eventRepository.findByEventCode(eventCode)
                .orElseThrow(() -> new NotFoundException("Event not found: " + eventCode));

        return speakerPoolRepository.findByEventId(event.getId()).stream()
                .filter(speaker -> speaker.getStatus() == SpeakerWorkflowState.OVERFLOW)
                .sorted(Comparator.comparing(SpeakerPool::getCreatedAt))
                .toList();
    }

    /**
     * Check if an event is in overflow state.
     *
     * @param eventCode Event code
     * @return true if more speakers accepted than max slots
     */
    @Transactional(readOnly = true)
    public boolean isInOverflow(String eventCode) {
        Event event = eventRepository.findByEventCode(eventCode)
                .orElseThrow(() -> new NotFoundException("Event not found: " + eventCode));

        int maxSlots = eventTypeService.getEventType(event.getEventType()).getMaxSlots();
        long acceptedCount = countAcceptedSpeakers(event.getId());

        return acceptedCount > maxSlots;
    }

    /**
     * Get overflow statistics for an event.
     *
     * @param eventCode Event code
     * @return Overflow summary with counts
     */
    @Transactional(readOnly = true)
    public OverflowSummary getOverflowSummary(String eventCode) {
        Event event = eventRepository.findByEventCode(eventCode)
                .orElseThrow(() -> new NotFoundException("Event not found: " + eventCode));

        int maxSlots = eventTypeService.getEventType(event.getEventType()).getMaxSlots();
        long acceptedCount = countAcceptedSpeakers(event.getId());
        long overflowCount = speakerPoolRepository.findByEventId(event.getId()).stream()
                .filter(speaker -> speaker.getStatus() == SpeakerWorkflowState.OVERFLOW)
                .count();

        return new OverflowSummary(
                eventCode,
                maxSlots,
                (int) acceptedCount,
                (int) overflowCount,
                acceptedCount > maxSlots
        );
    }

    /**
     * Count speakers in ACCEPTED or later states (excluding OVERFLOW, DECLINED, WITHDREW).
     *
     * @param eventId Event ID
     * @return Count of accepted speakers
     */
    private long countAcceptedSpeakers(UUID eventId) {
        return speakerPoolRepository.findByEventId(eventId).stream()
                .filter(speaker -> speaker.getStatus() == SpeakerWorkflowState.ACCEPTED
                        || speaker.getStatus() == SpeakerWorkflowState.CONTENT_SUBMITTED
                        || speaker.getStatus() == SpeakerWorkflowState.QUALITY_REVIEWED
                        || speaker.getStatus() == SpeakerWorkflowState.SLOT_ASSIGNED
                        || speaker.getStatus() == SpeakerWorkflowState.CONFIRMED)
                .count();
    }

    /**
     * Overflow summary DTO.
     */
    public record OverflowSummary(
            String eventCode,
            int maxSlots,
            int acceptedCount,
            int overflowCount,
            boolean isInOverflow
    ) {}
}
