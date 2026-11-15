package ch.batbern.events.architecture.fixtures.exceptionmapping.exception;

/** Breaks the base rule: an exception of the package that does not extend DomainException. */
public class FixtureNotFoundException extends RuntimeException {

    public FixtureNotFoundException() {
        super("Not found");
    }
}
