package ch.batbern.events.architecture.fixtures.exceptionmapping.exception;

/** A helper whose name starts like the handler's: using it is a mapping violation. */
public final class GlobalExceptionHandlerSupport {

    private GlobalExceptionHandlerSupport() {
    }

    public static String status(DomainException ex) {
        return "409 " + ex.getMessage();
    }
}
