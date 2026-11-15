package ch.batbern.events.architecture.fixtures.exceptionmapping.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Breaks the base rule: carries its status in @ResponseStatus although it extends DomainException. */
@ResponseStatus(HttpStatus.CONFLICT)
public class FixtureStatusException extends DomainException {

    public FixtureStatusException() {
        super("Conflict");
    }
}
