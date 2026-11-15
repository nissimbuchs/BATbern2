package ch.batbern.events.architecture.fixtures.exceptionmapping;

/**
 * Breaks the exception mapping rule: maps a module exception by hand. Not a
 * {@code @RestControllerAdvice}: the EMS component scan reaches test packages.
 */
public class GlobalExceptionHandler {

    public String handleDomainException(DomainException ex) {
        return DomainErrorResponse.of(ex);
    }

    public String handleAgendaFull(FixtureAgendaFullException ex) {
        return "409 " + ex.getMessage();
    }
}
