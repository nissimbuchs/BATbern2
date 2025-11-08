package ch.batbern.partners.client.impl;

import ch.batbern.partners.client.CompanyServiceClient;
import ch.batbern.partners.dto.CompanyDTO;
import ch.batbern.partners.exception.CompanyNotFoundException;
import ch.batbern.partners.exception.CompanyServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

/**
 * Implementation of CompanyServiceClient using Spring RestTemplate.
 *
 * Communicates with the Company User Management Service REST API to retrieve company data.
 *
 * Features:
 * - JWT token propagation from incoming requests
 * - Aggressive caching (15min TTL) for performance
 * - Fail-fast error handling
 * - Comprehensive logging
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CompanyServiceClientImpl implements CompanyServiceClient {

    private final RestTemplate restTemplate;

    @Value("${company-service.base-url}")
    private String companyServiceBaseUrl;

    /**
     * Get company by company name.
     *
     * Cached for 15 minutes to minimize API calls.
     *
     * @param companyName Company's name (unique identifier per ADR-003)
     * @return Company data
     * @throws CompanyNotFoundException if company not found
     * @throws CompanyServiceException if API communication fails
     */
    @Override
    @Cacheable(value = "companyApiCache", key = "#companyName")
    public CompanyDTO getCompanyByName(String companyName) {
        log.debug("Fetching company data for company name: {}", companyName);

        String url = companyServiceBaseUrl + "/api/v1/companies/" + companyName;

        try {
            HttpHeaders headers = createHeadersWithJwtToken();
            HttpEntity<Void> request = new HttpEntity<>(headers);

            ResponseEntity<CompanyDTO> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    request,
                    CompanyDTO.class
            );

            CompanyDTO company = response.getBody();
            log.debug("Successfully fetched company data for: {}", companyName);
            return company;

        } catch (HttpClientErrorException.NotFound e) {
            log.warn("Company not found: {}", companyName);
            throw new CompanyNotFoundException(companyName, e);

        } catch (HttpClientErrorException e) {
            log.error("Client error fetching company {}: {} - {}",
                    companyName, e.getStatusCode(), e.getMessage());
            throw new CompanyServiceException(
                    "Client error fetching company: " + companyName,
                    e.getStatusCode().value(),
                    e
            );

        } catch (HttpServerErrorException e) {
            log.error("Server error from Company Service for company {}: {} - {}",
                    companyName, e.getStatusCode(), e.getMessage());
            throw new CompanyServiceException(
                    "Company Service error for company: " + companyName,
                    e.getStatusCode().value(),
                    e
            );

        } catch (ResourceAccessException e) {
            log.error("Network error connecting to Company Service for company {}: {}",
                    companyName, e.getMessage());
            throw new CompanyServiceException(
                    "Failed to connect to Company Service for company: " + companyName,
                    e
            );

        } catch (Exception e) {
            log.error("Unexpected error fetching company {}: {}", companyName, e.getMessage(), e);
            throw new CompanyServiceException(
                    "Unexpected error fetching company: " + companyName,
                    e
            );
        }
    }

    /**
     * Create HTTP headers with JWT token propagated from SecurityContext.
     *
     * Extracts the JWT token from the current security context and adds it
     * to the Authorization header for service-to-service communication.
     *
     * @return HttpHeaders with Authorization Bearer token
     */
    private HttpHeaders createHeadersWithJwtToken() {
        HttpHeaders headers = new HttpHeaders();

        try {
            Object principal = SecurityContextHolder.getContext()
                    .getAuthentication()
                    .getPrincipal();

            if (principal instanceof Jwt jwt) {
                String token = jwt.getTokenValue();
                headers.set("Authorization", "Bearer " + token);
                log.trace("JWT token propagated to Company Service");
            } else {
                log.warn("No JWT token found in SecurityContext, principal type: {}",
                        principal != null ? principal.getClass().getSimpleName() : "null");
            }

        } catch (Exception e) {
            log.warn("Failed to extract JWT token from SecurityContext: {}", e.getMessage());
            // Continue without token - let the Company Service handle authorization
        }

        return headers;
    }
}
