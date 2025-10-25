package ch.batbern.companyuser.exception;

import ch.batbern.shared.exception.NotFoundException;

/**
 * Exception thrown when a company is not found by ID
 * AC4: REST API error handling (404 Not Found)
 *
 * Extends shared-kernel NotFoundException for consistent error handling
 */
public class CompanyNotFoundException extends NotFoundException {

    public CompanyNotFoundException(String companyId) {
        super("Company", companyId);
    }
}
