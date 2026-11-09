package ch.batbern.events.fixtures;

import ch.batbern.events.core.dto.generated.EventType;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Registration;
import ch.batbern.shared.test.fixtures.ProductionShapedPeople;
import ch.batbern.shared.test.fixtures.ProductionShapedPeople.Person;
import ch.batbern.shared.types.EventWorkflowState;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Production-shaped events and registrations (Story 17.0d), the EMS twin of shared-kernel
 * {@link ProductionShapedPeople}: a past and a future event, and one registration per status the
 * database allows, the attendees from the production-shaped people (Hông Ân, O’Neill, 王, a
 * mixed-case and an IDN e-mail, two identical "Matthias Keller").
 *
 * <p>Dates are relative to the {@link Clock} passed in, never a calendar date: a fixed date that
 * is "in the future" today becomes a past one later and turns tests red for no code change (#1069,
 * #1241). The builders return unsaved entities; a test persists what it needs and removes it
 * again. {@code ProductionShapedEventsIntegrationTest} guards that the statuses cover exactly the
 * values of {@code registrations_status_check}.
 */
public final class ProductionShapedEvents {

    /** Event numbers far above real ones, so a committing test never collides with real codes. */
    public static final int PAST_EVENT_NUMBER = 9_058;
    public static final int FUTURE_EVENT_NUMBER = 9_061;

    /**
     * One attendee per registration status, as the database stores the status (lower case).
     * Past-event statuses ({@code attended}, {@code no_show}, {@code cancelled}) belong to the past event.
     */
    public static final Map<String, Person> ATTENDEE_BY_STATUS;

    static {
        Map<String, Person> byStatus = new LinkedHashMap<>();
        byStatus.put("registered", ProductionShapedPeople.VIETNAMESE);
        byStatus.put("waitlist", ProductionShapedPeople.IDENTICAL_NAMES.get(0));
        byStatus.put("confirmed", ProductionShapedPeople.MIXED_CASE_EMAIL);
        byStatus.put("cancelled", ProductionShapedPeople.APOSTROPHE);
        byStatus.put("attended", ProductionShapedPeople.CJK);
        byStatus.put("no_show", ProductionShapedPeople.IDN_EMAIL);
        ATTENDEE_BY_STATUS = Collections.unmodifiableMap(byStatus);
    }

    /** Statuses of registrations to the past event; every other status goes to the future event. */
    private static final List<String> PAST_STATUSES = List.of("attended", "no_show", "cancelled");

    private ProductionShapedEvents() {
    }

    /** An archived event about a year before {@code clock}'s now. */
    public static Event pastEvent(Clock clock) {
        return event("BATbern" + PAST_EVENT_NUMBER, PAST_EVENT_NUMBER, "Plattformen, Teams & Conway’s Law",
                at(clock, -365), EventWorkflowState.ARCHIVED);
    }

    /** An event with open registration 40 days after {@code clock}'s now. */
    public static Event futureEvent(Clock clock) {
        return event("BATbern" + FUTURE_EVENT_NUMBER, FUTURE_EVENT_NUMBER,
                "Künstliche Intelligenz & Architektur", at(clock, 40), EventWorkflowState.AGENDA_PUBLISHED);
    }

    /**
     * One registration per status of {@link #ATTENDEE_BY_STATUS}, on the saved {@code past} or
     * {@code future} event, plus two more confirmed ones on the future event: the namesake of the
     * waitlisted attendee (same name, a different person) and the attendee with the IDN e-mail.
     */
    public static List<Registration> registrations(Event past, Event future, Clock clock) {
        List<Registration> all = new ArrayList<>();
        ATTENDEE_BY_STATUS.forEach((status, person) ->
                all.add(registration(PAST_STATUSES.contains(status) ? past : future, person, status, clock)));
        all.add(registration(future, ProductionShapedPeople.IDENTICAL_NAMES.get(1), "confirmed", clock));
        all.add(registration(future, ProductionShapedPeople.IDN_EMAIL, "confirmed", clock));
        return all;
    }

    /** A registration of {@code person} to the saved {@code event}. */
    public static Registration registration(Event event, Person person, String status, Clock clock) {
        return Registration.builder()
                .registrationCode(event.getEventCode() + "-" + person.username())
                .eventId(event.getId())
                .deregistrationToken(UUID.randomUUID())
                .attendeeUsername(person.username())
                .attendeeFirstName(person.firstName())
                .attendeeLastName(person.lastName())
                .attendeeEmail(person.email())
                .attendeeCompanyId(person.companyId())
                .status(status)
                .waitlistPosition("waitlist".equals(status) ? 1 : null)
                .registrationDate(clock.instant().minus(Duration.ofDays(10)))
                .build();
    }

    private static Instant at(Clock clock, int days) {
        // 16:00 UTC is 18:00 in Bern in summer, 17:00 in winter: an evening event.
        return clock.instant().plus(days, ChronoUnit.DAYS).truncatedTo(ChronoUnit.DAYS)
                .plus(Duration.ofHours(16));
    }

    private static Event event(String code, int number, String title, Instant date, EventWorkflowState state) {
        return Event.builder()
                .eventCode(code)
                .title(title)
                .eventNumber(number)
                .date(date)
                .registrationDeadline(date.minus(7, ChronoUnit.DAYS))
                .venueName("Kursaal Bern")
                .venueAddress("Kornhausstrasse 3, 3013 Bern")
                .venueCapacity(250)
                .organizerUsername(ProductionShapedPeople.MIXED_CASE_EMAIL.username())
                .currentAttendeeCount(0)
                .eventType(EventType.EVENING)
                .workflowState(state)
                .build();
    }
}
