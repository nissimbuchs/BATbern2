package ch.batbern.events.architecture.fixtures.exceptionmapping.other;

/** A class named like the base in another package: using it is a mapping violation. */
public class DomainException extends RuntimeException {

    public DomainException(String message) {
        super(message);
    }
}
