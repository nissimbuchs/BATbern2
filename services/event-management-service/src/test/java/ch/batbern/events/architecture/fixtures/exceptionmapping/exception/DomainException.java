package ch.batbern.events.architecture.fixtures.exceptionmapping.exception;

/** Fixture of the exception base: the global handler may use it, every exception extends it. */
public abstract class DomainException extends RuntimeException {

    protected DomainException(String message) {
        super(message);
    }
}
