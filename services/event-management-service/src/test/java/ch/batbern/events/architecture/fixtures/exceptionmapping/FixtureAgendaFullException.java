package ch.batbern.events.architecture.fixtures.exceptionmapping;

/** A module's domain exception that the global handler maps by hand: the violation. */
public class FixtureAgendaFullException extends DomainException {

    public FixtureAgendaFullException() {
        super("Agenda is full");
    }
}
