package ch.batbern.events.architecture.fixtures.exceptionmappingclean.exception;

/** Keeps the exception mapping rule: domain exceptions only through the base. */
public class GlobalExceptionHandler {

    public String handleDomainException(DomainException ex) {
        return DomainErrorResponse.of(ex);
    }

    public String handleIllegalArgument(IllegalArgumentException ex) {
        return new Nested().text(ex);
    }

    /** Nested class of the handler: allowed. */
    static final class Nested {
        String text(RuntimeException ex) {
            return "400 " + ex.getMessage();
        }
    }
}
