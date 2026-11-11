package ch.batbern.events.service;

import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.events.exception.EventNotFoundException;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.SpeakerPoolRepository;
import ch.batbern.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Loads the speaker pool entry addressed by {@code /events/{eventCode}/speakers/{speakerId}}
 * scoped to its event (ADR-003 amendment, Story 4.10). The {@code speakerId} is a UUID that
 * never leaves its meaningful parent: an entry of another event is not found (404), so no
 * speaker operation can read or change another event's speaker through this event's path.
 * Every speaker operation loads its entry here, never with an unscoped {@code findById}.
 */
@Component
@RequiredArgsConstructor
public class SpeakerPoolLookup {

    private final EventRepository eventRepository;
    private final SpeakerPoolRepository speakerPoolRepository;

    /**
     * @param eventCode the event code from the path
     * @param speakerId the speaker pool entry ID from the path
     * @return the entry of that event
     * @throws EventNotFoundException if the event does not exist (404)
     * @throws NotFoundException      if the entry does not exist in that event (404)
     */
    public SpeakerPool inEvent(String eventCode, UUID speakerId) {
        Event event = eventRepository.findByEventCode(eventCode)
                .orElseThrow(() -> new EventNotFoundException("Event not found: " + eventCode));
        return inEvent(event, speakerId);
    }

    /**
     * @param event     the event the path names
     * @param speakerId the speaker pool entry ID from the path
     * @return the entry of that event
     * @throws NotFoundException if the entry does not exist in that event (404)
     */
    public SpeakerPool inEvent(Event event, UUID speakerId) {
        return speakerPoolRepository.findByIdAndEventId(speakerId, event.getId())
                .orElseThrow(() -> new NotFoundException(
                        "Speaker pool entry not found in " + event.getEventCode() + ": " + speakerId));
    }
}
