package ch.batbern.events.architecture.fixtures.eventcontrollerhttp;

import org.springframework.data.repository.Repository;

/** A Spring Data repository with an event-core name: only the repository check catches it. */
public interface FixtureEventRepository extends Repository<Object, Long> {

    long count();
}
