package ch.batbern.events.listener;

import ch.batbern.events.domain.Event;
import ch.batbern.events.event.EventCreatedEvent;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.service.RegistrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Auto-enrolls all organizers and partners as confirmed participants when a new event is created.
 * <p>
 * Triggered by the local EventCreatedEvent published after event persistence.
 * Runs synchronously (no @Async) so the JWT security context is available for user service calls.
 * Uses REQUIRES_NEW propagation to isolate any enrollment failure from the parent event-creation
 * transaction. Top-level exceptions are caught and logged — enrollment failures never fail the
 * event creation HTTP response.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class AutoEnrollmentListener {

    private final EventRepository eventRepository;
    private final RegistrationService registrationService;

    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onEventCreated(EventCreatedEvent domainEvent) {
        String eventCode = domainEvent.getEventCode();
        try {
            Event event = eventRepository.findByEventCode(eventCode)
                    .orElseThrow(() -> new IllegalStateException("Event not found: " + eventCode));
            RegistrationService.EnrollmentSummary result = registrationService.enrollStakeholders(event);
            log.info("Auto-enrollment complete for event {}: enrolled={}, skipped={}",
                    eventCode, result.enrolled(), result.skipped());
        } catch (Exception e) {
            log.warn("Auto-enrollment skipped for event {}: {}", eventCode, e.getMessage());
        }
    }
}
