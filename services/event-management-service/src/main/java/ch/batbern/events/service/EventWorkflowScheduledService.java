package ch.batbern.events.service;

import ch.batbern.events.domain.Event;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.shared.types.EventWorkflowState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

/**
 * Scheduled service for automatic event workflow state transitions
 *
 * Story: GAP-2 - Event Workflow Scheduled Transitions
 * V82: AGENDA_FINALIZED removed — scheduler now transitions AGENDA_PUBLISHED directly to EVENT_LIVE
 *
 * Automatic Transitions:
 * 1. AGENDA_PUBLISHED → EVENT_LIVE (when event date is reached)
 * 2. EVENT_LIVE → EVENT_COMPLETED (when event date has passed)
 * 3. AGENDA_PUBLISHED → EVENT_COMPLETED (catch-up: event date passed while scheduler was down)
 *
 * Schedule:
 * - processEventsGoingLive(): Daily at 00:01 (1 minute past midnight)
 * - processCompletedEvents(): Daily at 23:59 (1 minute before midnight)
 *
 * ShedLock ensures only ONE ECS instance executes these jobs, preventing:
 * - Duplicate state transitions
 * - Duplicate notification emails
 * - Race conditions on database updates
 *
 * Production scenario: If 3 ECS tasks are running, ShedLock ensures only 1 executes
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class EventWorkflowScheduledService {

    private final EventRepository eventRepository;
    private final EventWorkflowStateMachine workflowStateMachine;

    /**
     * Scheduled job: Transition events to EVENT_LIVE when event date is reached
     *
     * Runs daily at 00:01 (1 minute past midnight)
     * Cron: "0 1 0 * * *" = second:0, minute:1, hour:0 (00:01)
     *
     * V82: Finds AGENDA_PUBLISHED events (AGENDA_FINALIZED no longer exists).
     *
     * ShedLock configuration:
     * - lockAtMostFor: 5 minutes (job should complete quickly, this is failsafe)
     * - lockAtLeastFor: 30 seconds (prevents re-execution if job completes very fast)
     */
    @Scheduled(cron = "${workflow.scheduled.events-going-live.cron:0 1 0 * * *}")
    @SchedulerLock(
            name = "processEventsGoingLive",
            lockAtMostFor = "5m",
            lockAtLeastFor = "30s"
    )
    @Transactional
    public void processEventsGoingLive() {
        log.info("Starting scheduled job: processEventsGoingLive");

        ZoneId bernZone = ZoneId.of("Europe/Zurich");
        LocalDate today = LocalDate.now(bernZone);

        // Convert today to Instant range (00:00:00 to 23:59:59 Bern time)
        Instant startOfDay = today.atStartOfDay(bernZone).toInstant();
        Instant endOfDay = today.atTime(LocalTime.MAX).atZone(bernZone).toInstant();

        // Find all events in AGENDA_PUBLISHED state where event date is today
        List<Event> events = eventRepository.findByWorkflowStateAndDateBetween(
                EventWorkflowState.AGENDA_PUBLISHED,
                startOfDay,
                endOfDay
        );

        if (events.isEmpty()) {
            log.info("No events found going live today ({})", today);
            return;
        }

        log.info("Found {} events going live today ({})", events.size(), today);

        int transitioned = 0;
        for (Event event : events) {
            try {
                workflowStateMachine.transitionToState(
                        event.getEventCode(),
                        EventWorkflowState.EVENT_LIVE,
                        "scheduler"
                );

                log.info("Transitioned event {} from AGENDA_PUBLISHED to EVENT_LIVE (date: {})",
                        event.getEventCode(), event.getDate());

                transitioned++;
            } catch (Exception e) {
                log.error("Failed to transition event {} to EVENT_LIVE",
                        event.getEventCode(), e);
                // Continue with next event - don't let one failure stop the batch
            }
        }

        log.info("Completed processEventsGoingLive: {}/{} events transitioned",
                transitioned, events.size());
    }

    /**
     * Scheduled job: Transition events to EVENT_COMPLETED when event date has passed
     *
     * Runs daily at 23:59 (1 minute before midnight)
     * Cron: "0 59 23 * * *" = second:0, minute:59, hour:23 (23:59)
     *
     * V82: Also handles AGENDA_PUBLISHED events past their date as a catch-up mechanism
     * (resilience for scheduler downtime — if the 00:01 job was down on event day, this
     * ensures the event still transitions to EVENT_COMPLETED at end of that day or later).
     *
     * ShedLock configuration:
     * - lockAtMostFor: 5 minutes (job should complete quickly, this is failsafe)
     * - lockAtLeastFor: 30 seconds (prevents re-execution if job completes very fast)
     */
    @Scheduled(cron = "${workflow.scheduled.events-completed.cron:0 59 23 * * *}")
    @SchedulerLock(
            name = "processCompletedEvents",
            lockAtMostFor = "5m",
            lockAtLeastFor = "30s"
    )
    @Transactional
    public void processCompletedEvents() {
        log.info("Starting scheduled job: processCompletedEvents");

        ZoneId bernZone = ZoneId.of("Europe/Zurich");
        LocalDate today = LocalDate.now(bernZone);

        // Convert today's start to Instant (events before today = before 00:00:00 today)
        Instant startOfToday = today.atStartOfDay(bernZone).toInstant();

        // Primary path: EVENT_LIVE events whose date has passed
        List<Event> liveEvents = eventRepository.findByWorkflowStateAndDateBefore(
                EventWorkflowState.EVENT_LIVE,
                startOfToday
        );

        // Catch-up path: AGENDA_PUBLISHED events whose date has passed
        // (handles case where processEventsGoingLive was down on event day)
        List<Event> stuckPublishedEvents = eventRepository.findByWorkflowStateAndDateBefore(
                EventWorkflowState.AGENDA_PUBLISHED,
                startOfToday
        );

        int total = liveEvents.size() + stuckPublishedEvents.size();
        if (total == 0) {
            log.info("No events found to complete (date < {})", today);
            return;
        }

        log.info("Found {} events to complete (date < {}): {} EVENT_LIVE, {} AGENDA_PUBLISHED (catch-up)",
                total, today, liveEvents.size(), stuckPublishedEvents.size());

        int transitioned = 0;

        for (Event event : liveEvents) {
            try {
                workflowStateMachine.transitionToState(
                        event.getEventCode(),
                        EventWorkflowState.EVENT_COMPLETED,
                        "scheduler"
                );
                log.info("Transitioned event {} from EVENT_LIVE to EVENT_COMPLETED (date: {})",
                        event.getEventCode(), event.getDate());
                transitioned++;
            } catch (Exception e) {
                log.error("Failed to transition event {} to EVENT_COMPLETED", event.getEventCode(), e);
            }
        }

        for (Event event : stuckPublishedEvents) {
            try {
                // Use override=true: AGENDA_PUBLISHED → EVENT_COMPLETED is not a normal transition
                workflowStateMachine.transitionToState(
                        event.getEventCode(),
                        EventWorkflowState.EVENT_COMPLETED,
                        "scheduler",
                        true,
                        "catch-up: event date passed while still in AGENDA_PUBLISHED (scheduler missed event day)"
                );
                log.warn("Catch-up: transitioned event {} from AGENDA_PUBLISHED to EVENT_COMPLETED (date: {})",
                        event.getEventCode(), event.getDate());
                transitioned++;
            } catch (Exception e) {
                log.error("Failed catch-up transition for event {} to EVENT_COMPLETED", event.getEventCode(), e);
            }
        }

        log.info("Completed processCompletedEvents: {}/{} events transitioned", transitioned, total);
    }

    /**
     * Manual trigger for going live transition (for testing or emergency use)
     * Can be called via admin endpoint if needed
     */
    public void triggerManualGoingLive() {
        log.warn("Manual trigger: processEventsGoingLive");
        processEventsGoingLive();
    }

    /**
     * Manual trigger for completion transition (for testing or emergency use)
     * Can be called via admin endpoint if needed
     */
    public void triggerManualCompletion() {
        log.warn("Manual trigger: processCompletedEvents");
        processCompletedEvents();
    }
}
