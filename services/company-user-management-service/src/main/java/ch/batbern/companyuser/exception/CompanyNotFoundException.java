package ch.batbern.companyuser.exception;

/**
 * Exception thrown when a company is not found by ID
 * AC4: REST API error handling (404 Not Found)
 */
public class CompanyNotFoundException extends RuntimeException {

    public CompanyNotFoundException(String companyId) {
        super("Company not found with ID: " + companyId);
    }
}
