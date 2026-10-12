package ch.batbern.events.service;

import ch.batbern.events.config.CacheConfig;
import ch.batbern.events.domain.Event;
import ch.batbern.events.sessions.dto.generated.AgendaConfigSource;
import ch.batbern.events.sessions.dto.generated.EventAgendaConfigResponse;
import ch.batbern.events.sessions.dto.generated.UpdateEventAgendaConfigRequest;
import ch.batbern.events.entity.AgendaConfig;
import ch.batbern.events.entity.EventAgendaConfig;
import ch.batbern.events.exception.EventNotFoundException;
import ch.batbern.events.repository.EventAgendaConfigRepository;
import ch.batbern.events.repository.EventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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

    private final EventRepository eventRepository;
    private final EventAgendaConfigRepository overrideRepository;
    private final AgendaConfigResolver agendaConfigResolver;
    private final CacheManager cacheManager;

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
        return toResponse(config, hasOverride ? AgendaConfigSource.EVENT_OVERRIDE : AgendaConfigSource.TEMPLATE);
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
        if (request.getMaxSlots() < request.getMinSlots()) {
            throw new IllegalArgumentException(String.format(
                    "maxSlots (%d) must be >= minSlots (%d)", request.getMaxSlots(), request.getMinSlots()));
        }
        Event event = loadEvent(eventCode);
        EventAgendaConfig entity = overrideRepository.findByEventId(event.getId())
                .orElseGet(() -> EventAgendaConfig.builder().eventId(event.getId()).build());
        apply(entity, request);
        EventAgendaConfig saved = overrideRepository.save(entity);
        evictSlotLimitCachesAfterCommit(eventCode);
        return toResponse(saved, AgendaConfigSource.EVENT_OVERRIDE);
    }

    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Min/max slots surface in the speaker status summary (the organizer UI's slot gate) and in
     * the event metrics. Evict both now and again after commit, so a concurrent read cannot
     * re-cache the pre-commit limits (same reasoning as {@code SpeakerWorkflowService}).
     */
    private void evictSlotLimitCachesAfterCommit(String eventCode) {
        Runnable evict = () -> {
            Cache summary = cacheManager.getCache(CacheConfig.STATUS_SUMMARY_CACHE);
            if (summary != null) {
                summary.evict(eventCode);
            }
            Cache eventWithIncludes = cacheManager.getCache(CacheConfig.EVENT_WITH_INCLUDES_CACHE);
            if (eventWithIncludes != null) {
                eventWithIncludes.clear();
            }
        };
        evict.run();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    evict.run();
                }
            });
        }
    }

    private Event loadEvent(String eventCode) {
        return eventRepository.findByEventCode(eventCode)
                .orElseThrow(() -> new EventNotFoundException("Event not found with code: " + eventCode));
    }

    private void apply(EventAgendaConfig entity, UpdateEventAgendaConfigRequest req) {
        entity.setMinSlots(req.getMinSlots());
        entity.setMaxSlots(req.getMaxSlots());
        entity.setSlotDuration(req.getSlotDuration());
        entity.setTheoreticalSlotsAM(req.getTheoreticalSlotsAM());
        entity.setBreakSlots(req.getBreakSlots());
        entity.setLunchSlots(req.getLunchSlots());
        entity.setDefaultCapacity(req.getDefaultCapacity());
        entity.setModerationStartDuration(req.getModerationStartDuration());
        entity.setModerationEndDuration(req.getModerationEndDuration());
        entity.setBreakDuration(req.getBreakDuration());
        entity.setLunchDuration(req.getLunchDuration());
        entity.setAperitifSlots(req.getAperitifSlots());
        entity.setAperitifDuration(req.getAperitifDuration());
        // Wire enum (start|end) → DB String column.
        entity.setAperitifPosition(req.getAperitifPosition() == null ? null : req.getAperitifPosition().getValue());
        entity.setTypicalStartTime(parseTime(req.getTypicalStartTime()));
        entity.setTypicalEndTime(parseTime(req.getTypicalEndTime()));
    }

    private EventAgendaConfigResponse toResponse(AgendaConfig config, AgendaConfigSource source) {
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
                .aperitifPosition(config.getAperitifPosition() == null ? null
                        : EventAgendaConfigResponse.AperitifPositionEnum.fromValue(config.getAperitifPosition()))
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
