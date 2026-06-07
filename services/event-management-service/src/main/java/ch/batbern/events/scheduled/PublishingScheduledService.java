package ch.batbern.events.scheduled;

import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Session;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.PublishingConfigRepository;
import ch.batbern.events.repository.SessionRepository;
import ch.batbern.events.service.publishing.PublishingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * Publishing Scheduled Service
 * Story BAT-16 (AC5): Scheduled Auto-Publishing
 *
 * Handles automated publishing of event phases based on configurable schedules:
 * - Speakers phase: Auto-publishes N days before event (default: 30 days)
 * - Agenda phase: Auto-publishes N days before event (default: 14 days)
 *
 * Runs daily at 1 AM to check for events ready to auto-publish.
 */
@Service
@EnableScheduling
@RequiredArgsConstructor
@Slf4j
public class PublishingScheduledService {

    private final EventRepository eventRepository;
    private final SessionRepository sessionRepository;
    private final PublishingConfigRepository publishingConfigRepository;
    private final PublishingService publishingService;

    /**
     * Auto-publish speakers phase
     * Runs daily at 1 AM to find events that should have speakers published
     *
     * Default: 30 days before event date
     * Configurable per event via PublishingConfig.autoPublishSpeakersDaysBefore
     */
    @Scheduled(cron = "0 0 1 * * *") // Daily at 1 AM
    public void autoPublishSpeakers() {
        log.info("Running auto-publish speakers job");

        // Default: 30 days before event
        int daysBefore = 30;

        // Calculate target date (events happening N days from now)
        LocalDate targetDate = LocalDate.now().plusDays(daysBefore);
        Instant dayStart = targetDate.atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant dayEnd = targetDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();

        // Find all events on this date (regardless of workflow state)
        // We'll filter by current published phase below
        List<Event> candidateEvents = eventRepository.findAll().stream()
                .filter(e -> {
                    if (e.getDate() == null) {
                        return false;
                    }
                    Instant eventDate = e.getDate();
                    return !eventDate.isBefore(dayStart) && eventDate.isBefore(dayEnd);
                })
                .toList();

        log.info("Found {} candidate events for auto-publish speakers", candidateEvents.size());

        // Filter events that:
        // 1. Have currentPublishedPhase = null or "topic" (not yet published speakers)
        // 2. Have auto-publish enabled (check PublishingConfig)
        candidateEvents.stream()
                .filter(e -> e.getCurrentPublishedPhase() == null
                        || "topic".equalsIgnoreCase(e.getCurrentPublishedPhase()))
                .forEach(event -> {
                    try {
                        // Check if auto-publish is enabled for this event
                        boolean autoPublishEnabled = publishingConfigRepository.findByEventId(event.getId())
                                .map(config -> config.getAutoPublishSpeakers() != null
                                        && config.getAutoPublishSpeakers())
                                .orElse(true); // Default to true if no config exists

                        if (autoPublishEnabled) {
                            log.info("Auto-publishing speakers for event: {}", event.getEventCode());
                            publishingService.publishPhase(event.getEventCode(), "speakers");
                            log.info("Successfully auto-published speakers for event: {}", event.getEventCode());
                        } else {
                            log.debug("Auto-publish speakers disabled for event: {}", event.getEventCode());
                        }
                    } catch (Exception ex) {
                        log.error("Failed to auto-publish speakers for event: {}", event.getEventCode(), ex);
                    }
                });
    }

    /**
     * Auto-publish agenda phase
     * Runs daily at 1 AM to find events that should have agenda published
     *
     * Default: 14 days before event date
     * Configurable per event via PublishingConfig.autoPublishAgendaDaysBefore
     *
     * Prerequisites:
     * - Current published phase must be "speakers"
     * - All sessions must have timing assigned
     */
    @Scheduled(cron = "0 0 1 * * *") // Daily at 1 AM
    public void autoPublishAgenda() {
        log.info("Running auto-publish agenda job");

        // Default: 14 days before event
        int daysBefore = 14;

        // Calculate target date (events happening N days from now)
        LocalDate targetDate = LocalDate.now().plusDays(daysBefore);
        Instant dayStart = targetDate.atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant dayEnd = targetDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();

        // Find all events on this date
        List<Event> candidateEvents = eventRepository.findAll().stream()
                .filter(e -> {
                    if (e.getDate() == null) {
                        return false;
                    }
                    Instant eventDate = e.getDate();
                    return !eventDate.isBefore(dayStart) && eventDate.isBefore(dayEnd);
                })
                .toList();

        log.info("Found {} candidate events for auto-publish agenda", candidateEvents.size());

        // Filter events that:
        // 1. Have currentPublishedPhase = "speakers" (not yet published agenda)
        // 2. Have all sessions with timing assigned
        // 3. Have auto-publish enabled
        candidateEvents.stream()
                .filter(e -> "speakers".equalsIgnoreCase(e.getCurrentPublishedPhase()))
                .filter(this::allSessionsHaveTiming)
                .forEach(event -> {
                    try {
                        // Check if auto-publish is enabled for this event
                        boolean autoPublishEnabled = publishingConfigRepository.findByEventId(event.getId())
                                .map(config -> config.getAutoPublishAgenda() != null
                                        && config.getAutoPublishAgenda())
                                .orElse(true); // Default to true if no config exists

                        if (autoPublishEnabled) {
                            log.info("Auto-publishing agenda for event: {}", event.getEventCode());
                            publishingService.publishPhase(event.getEventCode(), "agenda");
                            log.info("Successfully auto-published agenda for event: {}", event.getEventCode());
                        } else {
                            log.debug("Auto-publish agenda disabled for event: {}", event.getEventCode());
                        }
                    } catch (Exception ex) {
                        log.error("Failed to auto-publish agenda for event: {}", event.getEventCode(), ex);
                    }
                });
    }

    /**
     * Check if all sessions for an event have timing assigned
     *
     * @param event The event to check
     * @return true if all sessions have start and end times
     */
    private boolean allSessionsHaveTiming(Event event) {
        List<Session> sessions = sessionRepository.findByEventId(event.getId());

        if (sessions.isEmpty()) {
            log.warn("Event {} has no sessions, cannot auto-publish agenda", event.getEventCode());
            return false;
        }

        boolean allHaveTiming = sessions.stream()
                .allMatch(s -> s.getStartTime() != null && s.getEndTime() != null);

        if (!allHaveTiming) {
            log.debug("Event {} has sessions without timing, skipping auto-publish agenda",
                    event.getEventCode());
        }

        return allHaveTiming;
    }
}
