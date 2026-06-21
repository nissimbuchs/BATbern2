package ch.batbern.events.service;

import ch.batbern.events.domain.Event;
import ch.batbern.events.entity.AgendaConfig;
import ch.batbern.events.repository.EventAgendaConfigRepository;
import ch.batbern.events.repository.EventTypeRepository;
import ch.batbern.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        return overrideRepository.findByEventId(event.getId())
                .map(override -> (AgendaConfig) override)
                .orElseGet(() -> {
                    if (event.getEventType() == null) {
                        throw new NotFoundException(
                                "Event '" + event.getEventCode() + "' has no event type configured");
                    }
                    return templateRepository.findByType(event.getEventType())
                            .orElseThrow(() -> new NotFoundException(
                                    "Event type configuration not found for: " + event.getEventType()));
                });
    }
}
