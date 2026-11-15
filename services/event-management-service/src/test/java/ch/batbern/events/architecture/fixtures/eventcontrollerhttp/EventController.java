package ch.batbern.events.architecture.fixtures.eventcontrollerhttp;

import ch.batbern.events.architecture.fixtures.eventcontrollerhttp.analytics.api.generated.FixtureReportingApi;

/** Breaks the "uses event core only" rule three ways: a repository, a sessions class, a generated API. */
public class EventController implements FixtureReportingApi {

    private final FixtureEventRepository repository;
    private final SessionFixtureService sessions;

    public EventController(FixtureEventRepository repository, SessionFixtureService sessions) {
        this.repository = repository;
        this.sessions = sessions;
    }

    @Override
    public String report() {
        return sessions.sessions() + repository.count();
    }
}
