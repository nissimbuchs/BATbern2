package ch.batbern.events.exception;

import ch.batbern.shared.exception.BATbernException.Severity;
import org.springframework.http.HttpStatus;

/**
 * Thrown when a newsletter subscribe attempt uses an RFC 2606 / RFC 6761 reserved
 * domain (e.g. example.com, *.test, *.invalid). Prevents test-fixture or scanner
 * pollution of the production subscriber list. Maps to HTTP 400.
 */
public class ReservedEmailDomainException extends DomainException {

    public ReservedEmailDomainException(String email) {
        super(HttpStatus.BAD_REQUEST, Severity.LOW, null,
                "Email domain is reserved for testing/documentation and cannot subscribe: " + email);
    }
}
