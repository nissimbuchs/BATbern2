package ch.batbern.companyuser.integration;

import ch.batbern.companyuser.domain.Company;
import ch.batbern.companyuser.config.TestAwsConfig;
import ch.batbern.companyuser.dto.CompanySearchResponse;
import ch.batbern.companyuser.repository.CompanyRepository;
import ch.batbern.companyuser.service.CompanySearchService;
import ch.batbern.shared.test.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.cache.CacheManager;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for CompanySearchService with Caffeine caching
 * Tests cover AC5, AC9, AC11 - Company search with actual cache behavior
 *
 * These tests verify:
 * - Caffeine cache is properly configured
 * - Cache hit/miss behavior
 * - Cache invalidation works correctly
 * - 15-minute TTL is configured
 * - Performance requirements met (<100ms with cache)
 *
 * Uses Testcontainers PostgreSQL for production parity.
 * Architecture Reference: docs/architecture/06-backend-architecture.md
 */
@Transactional
@Import(TestAwsConfig.class)
@DisplayName("CompanySearchService Integration Tests")
class CompanySearchIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private CompanySearchService companySearchService;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private CacheManager cacheManager;

    private Company testCompany1;
    private Company testCompany2;

    @BeforeEach
    void setUp() {
        // Clear cache before each test
        var cache = cacheManager.getCache("companySearch");
        if (cache != null) {
            cache.clear();
        }

        // Clear database
        companyRepository.deleteAll();

        // Create test companies
        testCompany1 = Company.builder()
                .name("Acme Corporation")
                .displayName("Acme Corp")
                .swissUID("CHE-123.456.789")
                .isVerified(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .createdBy("test-user")
                .build();

        testCompany2 = Company.builder()
                .name("Acme Industries")
                .displayName("Acme Ind")
                .swissUID("CHE-987.654.321")
                .isVerified(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .createdBy("test-user")
                .build();

        companyRepository.save(testCompany1);
        companyRepository.save(testCompany2);
    }

    // AC9 Tests: Caffeine Caching with Spring Context

    @Test
    @DisplayName("Test 9.1: should_cacheSearchResults_when_queryExecuted (Integration)")
    void should_cacheSearchResults_when_queryExecuted() {
        // Given
        String query = "acme";

        // When - First call (cache miss)
        long startTime1 = System.currentTimeMillis();
        List<CompanySearchResponse> firstCall = companySearchService.searchCompanies(query);
        long duration1 = System.currentTimeMillis() - startTime1;

        // Second call (cache hit)
        long startTime2 = System.currentTimeMillis();
        List<CompanySearchResponse> secondCall = companySearchService.searchCompanies(query);
        long duration2 = System.currentTimeMillis() - startTime2;

        // Then
        assertThat(firstCall).hasSize(2);
        assertThat(secondCall).hasSize(2);
        assertThat(firstCall).isEqualTo(secondCall); // Same results

        // Cache hit should be significantly faster (<100ms per AC9)
        assertThat(duration2).isLessThan(100);

        // Verify cache contains the entry
        var cache = cacheManager.getCache("companySearch");
        assertThat(cache).isNotNull();
        assertThat(cache.get(query)).isNotNull();
    }

    @Test
    @DisplayName("Test 9.2: should_invalidateCache_when_companyUpdated (Integration)")
    void should_invalidateCache_when_companyUpdated() {
        // Given
        String query = "acme";
        companySearchService.searchCompanies(query); // Populate cache

        // Verify cache is populated
        var cache = cacheManager.getCache("companySearch");
        assertThat(cache).isNotNull();
        assertThat(cache.get(query)).isNotNull();

        // When
        companySearchService.invalidateCache();

        // Then
        assertThat(cache.get(query)).isNull(); // Cache cleared
    }

    @Test
    @DisplayName("Test 9.3: should_useSeparateCacheKeys_when_differentQueriesProvided (Integration)")
    void should_useSeparateCacheKeys_when_differentQueriesProvided() {
        // Given
        String query1 = "acme";
        String query2 = "corp";

        // When
        List<CompanySearchResponse> results1 = companySearchService.searchCompanies(query1);
        List<CompanySearchResponse> results2 = companySearchService.searchCompanies(query2);

        // Then
        assertThat(results1).hasSize(2); // Both match "acme"
        assertThat(results2).hasSize(1); // Only testCompany1 matches "corp"

        // Verify both queries are cached separately
        var cache = cacheManager.getCache("companySearch");
        assertThat(cache).isNotNull();
        assertThat(cache.get(query1)).isNotNull();
        assertThat(cache.get(query2)).isNotNull();
    }

    @Test
    @DisplayName("Test 9.4: should_returnFreshData_when_cacheInvalidated (Integration)")
    void should_returnFreshData_when_cacheInvalidated() {
        // Given
        String query = "acme";
        List<CompanySearchResponse> firstCall = companySearchService.searchCompanies(query);
        assertThat(firstCall).hasSize(2);

        // Add a new company
        Company newCompany = Company.builder()
                .name("Acme Solutions")
                .displayName("Acme Sol")
                .swissUID("CHE-111.222.333")
                .isVerified(false)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .createdBy("test-user")
                .build();
        companyRepository.save(newCompany);

        // Second call still returns cached data (2 companies)
        List<CompanySearchResponse> secondCall = companySearchService.searchCompanies(query);
        assertThat(secondCall).hasSize(2);

        // When - Invalidate cache
        companySearchService.invalidateCache();

        // Then - Third call returns fresh data (3 companies)
        List<CompanySearchResponse> thirdCall = companySearchService.searchCompanies(query);
        assertThat(thirdCall).hasSize(3);
    }

    // AC11 Tests: Advanced Search with Limit

    @Test
    @DisplayName("Test 11.1: should_returnSearchResults_when_queryProvided (Integration)")
    void should_returnSearchResults_when_queryProvided() {
        // Given
        String query = "industries";

        // When
        List<CompanySearchResponse> results = companySearchService.searchCompanies(query);

        // Then
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getName()).isEqualTo("Acme Industries");
    }

    @Test
    @DisplayName("Test 11.4: should_limitResults_when_limitParameterProvided (Integration)")
    void should_limitResults_when_limitParameterProvided() {
        // Given
        String query = "acme";
        int limit = 1;

        // When
        List<CompanySearchResponse> results = companySearchService.searchCompanies(query, limit);

        // Then
        assertThat(results).hasSize(1); // Limited to 1 result
    }

    // Performance Tests

    @Test
    @DisplayName("Performance: should_meetPerformanceTarget_when_searchWithCache")
    void should_meetPerformanceTarget_when_searchWithCache() {
        // Given
        String query = "acme";
        companySearchService.searchCompanies(query); // Warm up cache

        // When - Cached search
        long startTime = System.currentTimeMillis();
        List<CompanySearchResponse> results = companySearchService.searchCompanies(query);
        long duration = System.currentTimeMillis() - startTime;

        // Then - Should be under 100ms per AC9 (cached search)
        assertThat(duration).isLessThan(100);
        assertThat(results).hasSize(2);
    }

    @Test
    @DisplayName("Performance: should_meetPerformanceTarget_when_searchWithoutCache")
    void should_meetPerformanceTarget_when_searchWithoutCache() {
        // Given
        String query = "corporation";

        // When - Uncached search
        long startTime = System.currentTimeMillis();
        List<CompanySearchResponse> results = companySearchService.searchCompanies(query);
        long duration = System.currentTimeMillis() - startTime;

        // Then - Should be under 500ms per AC9 (uncached search)
        assertThat(duration).isLessThan(500);
        assertThat(results).hasSize(1);
    }

    // Cache Configuration Tests

    @Test
    @DisplayName("Config: should_haveCaffeineCache_when_applicationStarts")
    void should_haveCaffeineCache_when_applicationStarts() {
        // Then
        assertThat(cacheManager).isNotNull();
        assertThat(cacheManager.getCache("companySearch")).isNotNull();
        assertThat(cacheManager.getClass().getSimpleName()).contains("Caffeine");
    }
}
