package ch.batbern.events.architecture.fixtures.eventcontrollerhttpclean;

import ch.batbern.events.architecture.fixtures.eventcontrollerhttpclean.core.api.generated.FixtureEventsApi;
import ch.batbern.events.architecture.fixtures.eventcontrollerhttpclean.security.FixtureCallerRoles;

/** Keeps the "uses event core only" rule: an event-core service, the core API, a platform class. */
public class EventController implements FixtureEventsApi {

    private final EventFixtureReadService reads;

    public EventController(EventFixtureReadService reads) {
        this.reads = reads;
    }

    @Override
    public String getEvent() {
        return FixtureCallerRoles.isOrganizer() ? "" : reads.read();
    }
}
