package ch.batbern.events.architecture.fixtures.exceptionmapping.exception;

/** A domain exception that the global handler maps by hand: a mapping violation. */
public class FixtureAgendaFullException extends DomainException {

    public FixtureAgendaFullException() {
        super("Agenda is full");
    }
}
