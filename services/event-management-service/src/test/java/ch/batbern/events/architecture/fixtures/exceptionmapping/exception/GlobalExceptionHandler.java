package ch.batbern.events.architecture.fixtures.exceptionmapping.exception;

/**
 * Breaks the exception mapping rule: maps a domain exception by hand, uses a look-alike helper and a
 * look-alike base. Not a {@code @RestControllerAdvice}: the EMS component scan reaches test packages.
 */
public class GlobalExceptionHandler {

    public String handleDomainException(DomainException ex) {
        return DomainErrorResponse.of(ex);
    }

    public String handleAgendaFull(FixtureAgendaFullException ex) {
        return GlobalExceptionHandlerSupport.status(ex);
    }

    public String handleOther(ch.batbern.events.architecture.fixtures.exceptionmapping.other.DomainException ex) {
        return new Nested().text(ex);
    }

    /** Nested class of the handler: allowed. */
    static final class Nested {
        String text(RuntimeException ex) {
            return ex.getMessage();
        }
    }
}
