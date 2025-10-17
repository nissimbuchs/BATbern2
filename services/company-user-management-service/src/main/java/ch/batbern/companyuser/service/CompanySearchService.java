package ch.batbern.companyuser.service;

import ch.batbern.companyuser.domain.Company;
import ch.batbern.companyuser.dto.CompanySearchResponse;
import ch.batbern.companyuser.repository.CompanyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for company search with Caffeine caching
 * AC5: Company search functionality
 * AC9: Caffeine-based caching with 15-minute TTL
 * AC11: Advanced search with autocomplete (max 20 results)
 *
 * Caching Strategy:
 * - Cache key: "companySearch::{query}"
 * - TTL: 15 minutes (configured in CacheConfig)
 * - Cache invalidation: On company create/update/delete
 * - Max autocomplete results: 20
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@Slf4j
public class CompanySearchService {

    private final CompanyRepository companyRepository;
    private final CacheManager cacheManager;

    private static final int DEFAULT_AUTOCOMPLETE_LIMIT = 20;

    /**
     * Search companies with autocomplete functionality
     * Results are cached for 15 minutes using Caffeine
     *
     * @param query Search query (case-insensitive partial match)
     * @return List of matching companies (max 20 results)
     */
    @Cacheable(value = "companySearch", key = "#query")
    public List<CompanySearchResponse> searchCompanies(String query) {
        log.debug("Searching companies with query: {}", query);

        List<Company> companies = companyRepository.findByNameContainingIgnoreCase(query);

        // Limit to 20 autocomplete results
        return companies.stream()
                .limit(DEFAULT_AUTOCOMPLETE_LIMIT)
                .map(this::mapToSearchResponse)
                .collect(Collectors.toList());
    }

    /**
     * Search companies with custom limit
     *
     * @param query Search query
     * @param limit Maximum number of results
     * @return List of matching companies (up to limit)
     */
    @Cacheable(value = "companySearch", key = "#query + ':' + #limit")
    public List<CompanySearchResponse> searchCompanies(String query, int limit) {
        log.debug("Searching companies with query: {} and limit: {}", query, limit);

        List<Company> companies = companyRepository.findByNameContainingIgnoreCase(query);

        return companies.stream()
                .limit(limit)
                .map(this::mapToSearchResponse)
                .collect(Collectors.toList());
    }

    /**
     * Invalidate company search cache
     * Called when companies are created, updated, or deleted
     */
    public void invalidateCache() {
        log.debug("Invalidating company search cache");

        var cache = cacheManager.getCache("companySearch");
        if (cache != null) {
            cache.clear();
            log.info("Company search cache invalidated successfully");
        } else {
            log.warn("Company search cache not found - cannot invalidate");
        }
    }

    /**
     * Map Company entity to CompanySearchResponse DTO
     */
    private CompanySearchResponse mapToSearchResponse(Company company) {
        return CompanySearchResponse.builder()
                .id(company.getId())
                .name(company.getName())
                .displayName(company.getDisplayName())
                .swissUID(company.getSwissUID())
                .isVerified(company.isVerified())
                .build();
    }
}
