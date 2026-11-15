package ch.batbern.events.architecture.fixtures.exceptionmappingclean.exception;

/** Keeps the base rule: extends DomainException, no @ResponseStatus. */
public class FixtureCleanException extends DomainException {

    public FixtureCleanException() {
        super("Clean");
    }
}
