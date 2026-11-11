package ch.batbern.partners.client.impl;

import ch.batbern.partners.client.CompanyServiceClient;
import ch.batbern.partners.client.company.dto.CompanyResponse;
import ch.batbern.partners.exception.CompanyNotFoundException;
import ch.batbern.partners.exception.CompanyServiceException;
import ch.batbern.shared.security.S2sHeaders;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;

/**
 * Implementation of CompanyServiceClient using Spring RestTemplate.
 *
 * Reads company data from the Company User Management Service's internal API
 * ({@code docs/api/internal/cums-internal.openapi.yml}): {@code /internal/companies/**} is not routed
 * by the API gateway and is reachable only inside the VPC (ADR-016 decision 2, Epic 17 6b).
 *
 * Features:
 * - Headers from {@link S2sHeaders#forCurrentUser()}: the user's JWT when there is one, none otherwise
 * - Aggressive caching (15min TTL) for performance
 * - Fail-fast error handling
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CompanyServiceClientImpl implements CompanyServiceClient {

    private final RestTemplate restTemplate;

    @Value("${company-service.base-url}")
    private String companyServiceBaseUrl;

    /**
     * Get company by company name, from {@code GET /internal/companies/{name}} (the same
     * {@code CompanyResponse} as the public {@code GET /api/v1/companies/{name}}, which stays for users).
     *
     * Cached for 15 minutes to minimize API calls. The answer does not depend on the caller.
     *
     * @param companyName Company's name (unique identifier per ADR-003)
     * @return Company data
     * @throws CompanyNotFoundException if company not found
     * @throws CompanyServiceException if API communication fails
     */
    @Override
    @Cacheable(value = "companyApiCache", key = "#companyName")
    public CompanyResponse getCompanyByName(String companyName) {
        log.debug("Fetching company data for company name: {}", companyName);

        // One strictly encoded path segment: a '/', '?' or space in the name cannot change the path.
        URI url = UriComponentsBuilder.fromUriString(companyServiceBaseUrl + "/internal/companies/{name}")
                .encode().buildAndExpand(companyName).toUri();

        try {
            HttpEntity<Void> request = new HttpEntity<>(S2sHeaders.forCurrentUser());

            ResponseEntity<CompanyResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    request,
                    CompanyResponse.class
            );

            CompanyResponse company = response.getBody();
            log.debug("Successfully fetched company data for: {}", companyName);
            return company;

        } catch (HttpClientErrorException.NotFound e) {
            log.warn("Company not found: {}", companyName);
            throw new CompanyNotFoundException("Company not found: " + companyName);

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
     * {@inheritDoc}
     *
     * <p>{@code /internal/companies} is not routed by the API gateway and needs no JWT
     * (service-to-service inside the VPC, like {@code /internal/users}); the user's JWT is
     * forwarded when there is one ({@link S2sHeaders}).
     */
    @Override
    public List<String> findCompanyNames(String search) {
        URI uri = UriComponentsBuilder.fromUriString(companyServiceBaseUrl)
                .path("/internal/companies")
                .queryParam("search", "{search}")
                .encode()
                .buildAndExpand(search) // a template variable is encoded strictly: & + % ( too
                .toUri();
        try {
            ResponseEntity<List<String>> response = restTemplate.exchange(
                    uri, HttpMethod.GET, new HttpEntity<>(S2sHeaders.forCurrentUser()),
                    new ParameterizedTypeReference<List<String>>() { });
            List<String> names = response.getBody();
            return names != null ? names : List.of();
        } catch (HttpStatusCodeException e) {
            log.error("Company Service answered {} to the company-name search", e.getStatusCode());
            throw new CompanyServiceException("Company name search failed", e.getStatusCode().value(), e);
        } catch (RestClientException e) {
            log.error("Company name search failed: {}", e.getMessage());
            throw new CompanyServiceException("Failed to connect to Company Service for the company-name search", e);
        }
    }
}
