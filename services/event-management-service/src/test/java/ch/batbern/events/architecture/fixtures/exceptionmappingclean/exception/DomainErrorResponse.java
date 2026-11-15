package ch.batbern.events.architecture.fixtures.exceptionmappingclean.exception;

/** Fixture of the body builder the global handler may use. */
public final class DomainErrorResponse {

    private DomainErrorResponse() {
    }

    public static String of(DomainException ex) {
        return ex.getMessage();
    }
}
