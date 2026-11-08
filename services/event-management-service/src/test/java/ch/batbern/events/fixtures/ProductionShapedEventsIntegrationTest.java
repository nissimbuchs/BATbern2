package ch.batbern.events.fixtures;

import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Registration;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.RegistrationRepository;
import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.shared.test.fixtures.ProductionShapedPeople;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Guard of the EMS production-shaped fixtures (Story 17.0d), against real PostgreSQL: the events
 * and registrations persist as built, past and future are relative to the clock, and the
 * registrations cover exactly the statuses {@code registrations_status_check} allows, read from
 * the database. A status a migration adds fails here until the fixture covers it.
 *
 * <p>No class-level {@code @Transactional}: every save commits like in production, and
 * {@link #cleanUp()} removes what the test created.
 */
@DisplayName("Production-shaped events (PostgreSQL)")
class ProductionShapedEventsIntegrationTest extends AbstractIntegrationTest {

    private static final Pattern QUOTED = Pattern.compile("'([^']+)'");

    @Autowired
    private EventRepository eventRepository;
    @Autowired
    private RegistrationRepository registrationRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final Clock clock = Clock.systemUTC();
    private final List<UUID> createdEventIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        for (UUID eventId : createdEventIds) {
            registrationRepository.deleteAll(registrationRepository.findByEventId(eventId));
        }
        eventRepository.deleteAllById(createdEventIds);
        createdEventIds.clear();
    }

    @Test
    void should_coverExactlyTheStatusesTheDatabaseAllows() {
        Set<String> allowed = allowedStatuses();
        // Positive control: the constraint was found and parsed.
        assertThat(allowed).contains("confirmed").hasSizeGreaterThan(1);

        Event past = save(ProductionShapedEvents.pastEvent(clock));
        Event future = save(ProductionShapedEvents.futureEvent(clock));
        registrationRepository.saveAllAndFlush(ProductionShapedEvents.registrations(past, future, clock));

        Set<String> stored = new TreeSet<>();
        stored.addAll(statusesOf(past));
        stored.addAll(statusesOf(future));
        assertThat(stored).as("statuses of the fixture registrations vs registrations_status_check")
                .isEqualTo(new TreeSet<>(allowed));
    }

    @Test
    void should_refuseAStatusOutsideTheConstraint_positiveControl() {
        Event future = save(ProductionShapedEvents.futureEvent(clock));
        Registration bogus = ProductionShapedEvents.registration(future, ProductionShapedPeople.CJK, "pending",
                clock);

        assertThatThrownBy(() -> registrationRepository.saveAndFlush(bogus))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("registrations_status_check");
    }

    @Test
    void should_persistPastAndFutureEventsRelativeToTheClock() {
        Event past = save(ProductionShapedEvents.pastEvent(clock));
        Event future = save(ProductionShapedEvents.futureEvent(clock));
        Instant now = clock.instant();

        assertThat(eventRepository.findById(past.getId()).orElseThrow().getDate()).isBefore(now);
        assertThat(eventRepository.findById(future.getId()).orElseThrow().getDate()).isAfter(now);
        assertThat(eventRepository.findById(future.getId()).orElseThrow().getRegistrationDeadline())
                .isAfter(now);

        Clock inTwoYears = Clock.offset(clock, Duration.ofDays(730));
        assertThat(ProductionShapedEvents.futureEvent(inTwoYears).getDate()).isAfter(inTwoYears.instant());
    }

    @Test
    void should_storeTheAttendeesAsEntered() {
        Event past = save(ProductionShapedEvents.pastEvent(clock));
        Event future = save(ProductionShapedEvents.futureEvent(clock));
        registrationRepository.saveAllAndFlush(ProductionShapedEvents.registrations(past, future, clock));

        List<Registration> stored = new ArrayList<>(registrationRepository.findByEventId(past.getId()));
        stored.addAll(registrationRepository.findByEventId(future.getId()));

        assertThat(stored).extracting(Registration::getAttendeeLastName)
                .contains("O’Neill", "王", "Meier", "Keller");
        assertThat(stored).extracting(Registration::getAttendeeEmail).contains("Anna.Keller@Example.CH");
        // The two identical "Matthias Keller" are two registrations of two people.
        assertThat(stored.stream().filter(r -> "Keller".equals(r.getAttendeeLastName())
                        && "Matthias".equals(r.getAttendeeFirstName()))
                .map(Registration::getAttendeeUsername).collect(Collectors.toSet()))
                .containsExactlyInAnyOrder("matthias.keller", "matthias.keller.2");
    }

    private Set<String> allowedStatuses() {
        String definition = jdbcTemplate.queryForObject(
                "SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conname = 'registrations_status_check'",
                String.class);
        Set<String> values = new TreeSet<>();
        Matcher m = QUOTED.matcher(definition == null ? "" : definition);
        while (m.find()) {
            values.add(m.group(1));
        }
        return values;
    }

    private List<String> statusesOf(Event event) {
        return registrationRepository.findByEventId(event.getId()).stream().map(Registration::getStatus).toList();
    }

    private Event save(Event event) {
        Event saved = eventRepository.saveAndFlush(event);
        createdEventIds.add(saved.getId());
        return saved;
    }
}
