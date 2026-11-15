package ch.batbern.events.architecture.fixtures.exceptionmappingclean;

/** Keeps the exception mapping rule: domain exceptions only through the base. */
public class GlobalExceptionHandler {

    public String handleDomainException(DomainException ex) {
        return DomainErrorResponse.of(ex);
    }

    public String handleIllegalArgument(IllegalArgumentException ex) {
        return "400 " + ex.getMessage();
    }
}
