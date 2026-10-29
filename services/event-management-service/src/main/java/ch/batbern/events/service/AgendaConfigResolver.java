package ch.batbern.events.service;

import ch.batbern.events.domain.Event;
import ch.batbern.events.entity.AgendaConfig;
import ch.batbern.events.repository.EventAgendaConfigRepository;
import ch.batbern.events.repository.EventTypeRepository;
import ch.batbern.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Resolves the effective {@link AgendaConfig} for an event (Story 15.2).
 *
 * <p>Returns the per-event copy-on-edit override ({@code event_agenda_config}) when one exists,
 * else the shared event-type template ({@code event_types}). This is the single fallback point
 * routed through by every {@link TimetableService#computeTimeline} consumer (timetable read,
 * structural-session generation, auto-assign) so a per-event override takes effect uniformly.</p>
 */
@Service
@RequiredArgsConstructor
public class AgendaConfigResolver {

    private final EventAgendaConfigRepository overrideRepository;
    private final EventTypeRepository templateRepository;

    /**
     * Resolve the effective agenda config for an event.
     *
     * @param event the event
     * @return per-event override if present, else the shared template
     * @throws NotFoundException if no override exists and the event has no event type / template
     */
    @Transactional(readOnly = true)
    public AgendaConfig resolve(Event event) {
        return find(event).orElseThrow(() -> new NotFoundException(event.getEventType() == null
                ? "Event '" + event.getEventCode() + "' has no event type configured"
                : "Event type configuration not found for: " + event.getEventType()));
    }

    /**
     * Same as {@link #resolve} without throwing. Callers that tolerate a missing config inside a
     * larger transaction must use this: an exception thrown through this transactional bean
     * marks the caller's transaction rollback-only even when the caller catches it.
     */
    @Transactional(readOnly = true)
    public Optional<AgendaConfig> find(Event event) {
        Optional<AgendaConfig> override = overrideRepository.findByEventId(event.getId())
                .map(o -> (AgendaConfig) o);
        if (override.isPresent() || event.getEventType() == null) {
            return override;
        }
        return templateRepository.findByType(event.getEventType()).map(t -> (AgendaConfig) t);
    }
}
