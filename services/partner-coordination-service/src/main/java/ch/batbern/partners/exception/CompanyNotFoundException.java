package ch.batbern.partners.exception;

/**
 * Exception thrown when a company is not found in the Company Service.
 *
 * Thrown when Company Service API returns 404.
 */
public class CompanyNotFoundException extends RuntimeException {

    public CompanyNotFoundException(String companyName) {
        super("Company not found: " + companyName);
    }

    public CompanyNotFoundException(String companyName, Throwable cause) {
        super("Company not found: " + companyName, cause);
    }
}
