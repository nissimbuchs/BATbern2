package ch.batbern.partners.client;

import ch.batbern.partners.client.company.dto.CompanyResponse;

/**
 * Client interface for communicating with the Company Service API.
 *
 * This client provides company data retrieval for partner validation.
 * All methods use caching (15min TTL) to minimize API calls.
 *
 * Uses generated DTO from companies-api.openapi.yml for type safety.
 */
public interface CompanyServiceClient {

    /**
     * Get company by company name.
     *
     * @param companyName Company's name (unique identifier per ADR-003)
     * @return Company data (generated from companies-api.openapi.yml)
     * @throws ch.batbern.partners.exception.CompanyNotFoundException if company not found (404)
     * @throws ch.batbern.partners.exception.CompanyServiceException if API communication fails
     *         (5xx, timeout, network error)
     */
    CompanyResponse getCompanyByName(String companyName);

    /**
     * Get company by company name (convenience method).
     *
     * @param companyName Company's name (unique identifier per ADR-003)
     * @return Company data (generated from companies-api.openapi.yml)
     * @throws ch.batbern.partners.exception.CompanyNotFoundException if company not found (404)
     * @throws ch.batbern.partners.exception.CompanyServiceException if API communication fails
     *         (5xx, timeout, network error)
     */
    default CompanyResponse getCompany(String companyName) {
        return getCompanyByName(companyName);
    }
}
