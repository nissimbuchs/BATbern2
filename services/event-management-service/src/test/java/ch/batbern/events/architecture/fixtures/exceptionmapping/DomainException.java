package ch.batbern.events.architecture.fixtures.exceptionmapping;

/** Fixture of the exception base the global handler may use. */
public abstract class DomainException extends RuntimeException {

    protected DomainException(String message) {
        super(message);
    }
}
