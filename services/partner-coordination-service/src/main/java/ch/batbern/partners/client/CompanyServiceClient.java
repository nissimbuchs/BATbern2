package ch.batbern.partners.client;

import ch.batbern.partners.dto.CompanyDTO;

/**
 * Client interface for communicating with the Company Service API.
 *
 * This client provides company data retrieval for partner validation.
 * All methods use caching (15min TTL) to minimize API calls.
 */
public interface CompanyServiceClient {

    /**
     * Get company by company name.
     *
     * @param companyName Company's name (unique identifier per ADR-003)
     * @return Company data
     * @throws ch.batbern.partners.exception.CompanyNotFoundException if company not found (404)
     * @throws ch.batbern.partners.exception.CompanyServiceException if API communication fails (5xx, timeout, network error)
     */
    CompanyDTO getCompanyByName(String companyName);
}
