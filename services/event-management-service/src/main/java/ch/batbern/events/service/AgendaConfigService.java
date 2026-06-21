package ch.batbern.events.service;

import ch.batbern.events.domain.Event;
import ch.batbern.events.dto.EventAgendaConfigResponse;
import ch.batbern.events.dto.UpdateEventAgendaConfigRequest;
import ch.batbern.events.entity.AgendaConfig;
import ch.batbern.events.entity.EventAgendaConfig;
import ch.batbern.events.exception.EventNotFoundException;
import ch.batbern.events.repository.EventAgendaConfigRepository;
import ch.batbern.events.repository.EventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.time.format.DateTimeParseException;

/**
 * Read/upsert service for the per-event agenda config (Story 15.2).
 *
 * <p>GET returns the resolved effective config (per-event override if present, else the shared
 * template). PUT performs copy-on-edit: it creates or updates the per-event
 * {@code event_agenda_config} row and never touches the shared template.</p>
 */
@Service
@RequiredArgsConstructor
public class AgendaConfigService {

    private static final String SOURCE_TEMPLATE = "TEMPLATE";
    private static final String SOURCE_OVERRIDE = "EVENT_OVERRIDE";

    private final EventRepository eventRepository;
    private final EventAgendaConfigRepository overrideRepository;
    private final AgendaConfigResolver agendaConfigResolver;

    /**
     * Return the resolved effective agenda config for an event.
     *
     * @param eventCode public event code
     * @return resolved config with a {@code source} flag (TEMPLATE or EVENT_OVERRIDE)
     * @throws EventNotFoundException if the event does not exist
     */
    @Transactional(readOnly = true)
    public EventAgendaConfigResponse getResolvedConfig(String eventCode) {
        Event event = loadEvent(eventCode);
        boolean hasOverride = overrideRepository.findByEventId(event.getId()).isPresent();
        AgendaConfig config = agendaConfigResolver.resolve(event);
        return toResponse(config, hasOverride ? SOURCE_OVERRIDE : SOURCE_TEMPLATE);
    }

    /**
     * Copy-on-edit: create or update the per-event override from the full knob set.
     * The shared event-type template is never modified.
     *
     * @param eventCode public event code
     * @param request   full set of knobs to persist for this event
     * @return the saved per-event config (source = EVENT_OVERRIDE)
     * @throws EventNotFoundException if the event does not exist
     */
    @Transactional
    public EventAgendaConfigResponse upsert(String eventCode, UpdateEventAgendaConfigRequest request) {
        // Cross-field rule surfaced as 400 (the entity @PrePersist throws IllegalStateException → 500).
        if (request.maxSlots() < request.minSlots()) {
            throw new IllegalArgumentException(String.format(
                    "maxSlots (%d) must be >= minSlots (%d)", request.maxSlots(), request.minSlots()));
        }
        Event event = loadEvent(eventCode);
        EventAgendaConfig entity = overrideRepository.findByEventId(event.getId())
                .orElseGet(() -> EventAgendaConfig.builder().eventId(event.getId()).build());
        apply(entity, request);
        EventAgendaConfig saved = overrideRepository.save(entity);
        return toResponse(saved, SOURCE_OVERRIDE);
    }

    // ─────────────────────────────────────────────────────────────────────────

    private Event loadEvent(String eventCode) {
        return eventRepository.findByEventCode(eventCode)
                .orElseThrow(() -> new EventNotFoundException("Event not found with code: " + eventCode));
    }

    private void apply(EventAgendaConfig entity, UpdateEventAgendaConfigRequest req) {
        entity.setMinSlots(req.minSlots());
        entity.setMaxSlots(req.maxSlots());
        entity.setSlotDuration(req.slotDuration());
        entity.setTheoreticalSlotsAM(req.theoreticalSlotsAM());
        entity.setBreakSlots(req.breakSlots());
        entity.setLunchSlots(req.lunchSlots());
        entity.setDefaultCapacity(req.defaultCapacity());
        entity.setModerationStartDuration(req.moderationStartDuration());
        entity.setModerationEndDuration(req.moderationEndDuration());
        entity.setBreakDuration(req.breakDuration());
        entity.setLunchDuration(req.lunchDuration());
        entity.setAperitifSlots(req.aperitifSlots());
        entity.setAperitifDuration(req.aperitifDuration());
        entity.setAperitifPosition(req.aperitifPosition());
        entity.setTypicalStartTime(parseTime(req.typicalStartTime()));
        entity.setTypicalEndTime(parseTime(req.typicalEndTime()));
    }

    private EventAgendaConfigResponse toResponse(AgendaConfig config, String source) {
        return EventAgendaConfigResponse.builder()
                .source(source)
                .minSlots(config.getMinSlots())
                .maxSlots(config.getMaxSlots())
                .slotDuration(config.getSlotDuration())
                .theoreticalSlotsAM(config.getTheoreticalSlotsAM())
                .breakSlots(config.getBreakSlots())
                .lunchSlots(config.getLunchSlots())
                .defaultCapacity(config.getDefaultCapacity())
                .moderationStartDuration(config.getModerationStartDuration())
                .moderationEndDuration(config.getModerationEndDuration())
                .breakDuration(config.getBreakDuration())
                .lunchDuration(config.getLunchDuration())
                .aperitifSlots(config.getAperitifSlots())
                .aperitifDuration(config.getAperitifDuration())
                .aperitifPosition(config.getAperitifPosition())
                .typicalStartTime(formatTime(config.getTypicalStartTime()))
                .typicalEndTime(formatTime(config.getTypicalEndTime()))
                .build();
    }

    private static LocalTime parseTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalTime.parse(value);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid time '" + value + "' (expected HH:mm)");
        }
    }

    private static String formatTime(LocalTime value) {
        return value == null ? null : value.toString();
    }
}
