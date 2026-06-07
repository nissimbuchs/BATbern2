package ch.batbern.events.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when a newsletter subscribe attempt uses an RFC 2606 / RFC 6761 reserved
 * domain (e.g. example.com, *.test, *.invalid). Prevents test-fixture or scanner
 * pollution of the production subscriber list. Maps to HTTP 400.
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class ReservedEmailDomainException extends RuntimeException {

    public ReservedEmailDomainException(String email) {
        super("Email domain is reserved for testing/documentation and cannot subscribe: " + email);
    }
}
