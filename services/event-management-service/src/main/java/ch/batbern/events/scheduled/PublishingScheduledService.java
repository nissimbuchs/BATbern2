package ch.batbern.events.scheduled;

import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.PublishingConfig;
import ch.batbern.events.domain.Session;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.SessionRepository;
import ch.batbern.events.service.publishing.PublishingPhase;
import ch.batbern.events.service.publishing.PublishingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Publishing Scheduled Service
 * Story BAT-16 (AC5): Scheduled Auto-Publishing
 *
 * <p>Publishes a phase on the day its stored schedule names (the {@code publishing_config} row of
 * the event, set through {@code PATCH /events/{eventCode}/publishing}; without a row the defaults
 * apply: speakers 30 days, agenda 14 days before the event, both enabled):
 * <ul>
 *   <li>Speakers: when the event is {@code autoPublishSpeakersDaysBefore} days away, enabled, and
 *       nothing or only the topic is published.</li>
 *   <li>Agenda: when the event is {@code autoPublishAgendaDaysBefore} days away, enabled, the
 *       speakers are published and every session is timed.</li>
 * </ul>
 * Until Story 4.9b the job used fixed 30 and 14 days and ignored the stored days.
 *
 * <p>A failure for one event is recorded in {@link ScheduledJobFailures} (metric and alarmed log
 * marker) and the job goes on with the next event.
 *
 * <p>Runs daily at 1 AM. The lock sits on {@link #runDailyAutoPublish()} only, so a test (or an
 * operator) can call the two jobs directly without waiting for the lock of an earlier run.
 */
@Service
@EnableScheduling
@RequiredArgsConstructor
@Slf4j
public class PublishingScheduledService {

    private final EventRepository eventRepository;
    private final SessionRepository sessionRepository;
    private final PublishingService publishingService;
    private final ScheduledJobFailures failures;

    @Scheduled(cron = "0 0 1 * * *")
    @SchedulerLock(name = "PublishingScheduledService_runDailyAutoPublish",
            lockAtLeastFor = "PT5M", lockAtMostFor = "PT30M")
    public void runDailyAutoPublish() {
        autoPublishSpeakers();
        autoPublishAgenda();
    }

    /** Publish the speakers phase of every event whose schedule says today. */
    public void autoPublishSpeakers() {
        autoPublish(PublishingPhase.SPEAKERS,
                e -> e.getCurrentPublishedPhase() == null
                        || PublishingPhase.TOPIC.value().equalsIgnoreCase(e.getCurrentPublishedPhase()),
                config -> Boolean.TRUE.equals(config.getAutoPublishSpeakers()),
                PublishingConfig::getAutoPublishSpeakersDaysBefore);
    }

    /** Publish the agenda phase of every event whose schedule says today and whose sessions are timed. */
    public void autoPublishAgenda() {
        autoPublish(PublishingPhase.AGENDA,
                e -> PublishingPhase.SPEAKERS.value().equalsIgnoreCase(e.getCurrentPublishedPhase())
                        && allSessionsHaveTiming(e),
                config -> Boolean.TRUE.equals(config.getAutoPublishAgenda()),
                PublishingConfig::getAutoPublishAgendaDaysBefore);
    }

    private void autoPublish(PublishingPhase phase,
                             Predicate<Event> ready,
                             Predicate<PublishingConfig> enabled,
                             Function<PublishingConfig, Integer> daysBefore) {
        LocalDate today = LocalDate.now();
        Instant startOfToday = today.atStartOfDay(ZoneId.systemDefault()).toInstant();
        List<Event> upcoming = eventRepository.findByDateAfter(startOfToday);
        log.info("Auto-publish {}: {} upcoming event(s) to check", phase.value(), upcoming.size());

        for (Event event : upcoming) {
            // Everything per event inside the try: one bad row never stops the others (4.9b)
            try {
                PublishingConfig schedule = publishingService.scheduleOf(event);
                if (!enabled.test(schedule)) {
                    log.debug("Auto-publish {} disabled for event {}", phase.value(), event.getEventCode());
                    continue;
                }
                LocalDate eventDay = event.getDate().atZone(ZoneId.systemDefault()).toLocalDate();
                if (!eventDay.equals(today.plusDays(daysBefore.apply(schedule)))) {
                    continue;
                }
                if (!ready.test(event)) {
                    log.debug("Event {} is not ready for auto-publish {}", event.getEventCode(), phase.value());
                    continue;
                }
                publishingService.publishPhase(event.getEventCode(), phase);
                log.info("Auto-published {} for event {}", phase.value(), event.getEventCode());
            } catch (Exception ex) {
                failures.record("auto-publish-" + phase.value(), event.getEventCode(), ex);
            }
        }
    }

    private boolean allSessionsHaveTiming(Event event) {
        List<Session> sessions = sessionRepository.findByEventId(event.getId());
        if (sessions.isEmpty()) {
            log.warn("Event {} has no sessions, cannot auto-publish agenda", event.getEventCode());
            return false;
        }
        return sessions.stream().allMatch(s -> s.getStartTime() != null && s.getEndTime() != null);
    }
}
