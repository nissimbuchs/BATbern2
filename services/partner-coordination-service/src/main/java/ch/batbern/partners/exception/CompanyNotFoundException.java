package ch.batbern.partners.exception;

import ch.batbern.shared.exception.NotFoundException;

/**
 * Exception thrown when a company is not found in the Company Service.
 *
 * Thrown when Company Service API returns 404.
 * Extends shared-kernel NotFoundException.
 */
public class CompanyNotFoundException extends NotFoundException {

    public CompanyNotFoundException(String message) {
        super(message);
    }
}
