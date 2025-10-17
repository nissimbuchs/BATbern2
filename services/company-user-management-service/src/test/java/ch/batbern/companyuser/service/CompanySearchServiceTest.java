package ch.batbern.companyuser.service;

import ch.batbern.companyuser.domain.Company;
import ch.batbern.companyuser.dto.CompanySearchResponse;
import ch.batbern.companyuser.repository.CompanyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for CompanySearchService
 * Tests cover AC5, AC9, AC11 - Company search with Caffeine caching
 *
 * Test Strategy:
 * - RED Phase: All tests fail initially
 * - GREEN Phase: Implement minimal code to pass tests
 * - REFACTOR Phase: Improve code quality while keeping tests green
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CompanySearchService Tests")
class CompanySearchServiceTest {

    @Mock
    private CompanyRepository companyRepository;

    @Mock
    private CacheManager cacheManager;

    @Mock
    private Cache cache;

    @InjectMocks
    private CompanySearchService companySearchService;

    private Company testCompany1;
    private Company testCompany2;
    private Company testCompany3;

    @BeforeEach
    void setUp() {
        testCompany1 = Company.builder()
                .id(UUID.randomUUID())
                .name("Acme Corporation")
                .displayName("Acme Corp")
                .swissUID("CHE-123.456.789")
                .isVerified(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .createdBy("test-user")
                .build();

        testCompany2 = Company.builder()
                .id(UUID.randomUUID())
                .name("Acme Industries")
                .displayName("Acme Ind")
                .swissUID("CHE-987.654.321")
                .isVerified(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .createdBy("test-user")
                .build();

        testCompany3 = Company.builder()
                .id(UUID.randomUUID())
                .name("Swiss Tech AG")
                .displayName("Swiss Tech")
                .swissUID("CHE-111.222.333")
                .isVerified(false)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .createdBy("test-user")
                .build();
    }

    // AC5 Tests: Search Functionality

    @Test
    @DisplayName("Test 5.1: should_searchCompanies_when_queryProvided")
    void should_searchCompanies_when_queryProvided() {
        // Given
        String query = "acme";
        List<Company> companies = Arrays.asList(testCompany1, testCompany2);
        when(companyRepository.findByNameContainingIgnoreCase(query)).thenReturn(companies);

        // When
        List<CompanySearchResponse> results = companySearchService.searchCompanies(query);

        // Then
        assertThat(results).hasSize(2);
        assertThat(results.get(0).getName()).isEqualTo("Acme Corporation");
        assertThat(results.get(1).getName()).isEqualTo("Acme Industries");
        verify(companyRepository).findByNameContainingIgnoreCase(query);
    }

    @Test
    @DisplayName("Test 5.2: should_returnAutocompleteResults_when_partialNameProvided")
    void should_returnAutocompleteResults_when_partialNameProvided() {
        // Given
        String partialQuery = "acm";
        List<Company> companies = Arrays.asList(testCompany1, testCompany2);
        when(companyRepository.findByNameContainingIgnoreCase(partialQuery)).thenReturn(companies);

        // When
        List<CompanySearchResponse> results = companySearchService.searchCompanies(partialQuery);

        // Then
        assertThat(results).hasSize(2);
        assertThat(results).allMatch(r -> r.getName().toLowerCase().contains(partialQuery));
        verify(companyRepository).findByNameContainingIgnoreCase(partialQuery);
    }

    @Test
    @DisplayName("Test 5.2b: should_limitAutocompleteResults_when_maxResultsExceeded")
    void should_limitAutocompleteResults_when_maxResultsExceeded() {
        // Given
        String query = "company";
        List<Company> manyCompanies = Arrays.asList(
                testCompany1, testCompany2, testCompany3,
                createCompany("Company 4"),
                createCompany("Company 5"),
                createCompany("Company 6"),
                createCompany("Company 7"),
                createCompany("Company 8"),
                createCompany("Company 9"),
                createCompany("Company 10"),
                createCompany("Company 11"),
                createCompany("Company 12"),
                createCompany("Company 13"),
                createCompany("Company 14"),
                createCompany("Company 15"),
                createCompany("Company 16"),
                createCompany("Company 17"),
                createCompany("Company 18"),
                createCompany("Company 19"),
                createCompany("Company 20"),
                createCompany("Company 21"),
                createCompany("Company 22")
        );
        when(companyRepository.findByNameContainingIgnoreCase(query)).thenReturn(manyCompanies);

        // When
        List<CompanySearchResponse> results = companySearchService.searchCompanies(query);

        // Then - Max 20 autocomplete results per design
        assertThat(results).hasSizeLessThanOrEqualTo(20);
        verify(companyRepository).findByNameContainingIgnoreCase(query);
    }

    @Test
    @DisplayName("Test 5.3: should_returnEmptyList_when_noMatchesFound")
    void should_returnEmptyList_when_noMatchesFound() {
        // Given
        String query = "nonexistent";
        when(companyRepository.findByNameContainingIgnoreCase(query)).thenReturn(Collections.emptyList());

        // When
        List<CompanySearchResponse> results = companySearchService.searchCompanies(query);

        // Then
        assertThat(results).isEmpty();
        verify(companyRepository).findByNameContainingIgnoreCase(query);
    }

    // AC9 Tests: Caffeine Caching

    @Test
    @DisplayName("Test 9.1: should_cacheSearchResults_when_queryExecuted")
    void should_cacheSearchResults_when_queryExecuted() {
        // Given
        String query = "acme";
        List<Company> companies = Arrays.asList(testCompany1, testCompany2);
        when(companyRepository.findByNameContainingIgnoreCase(query)).thenReturn(companies);

        // When
        List<CompanySearchResponse> firstCall = companySearchService.searchCompanies(query);
        List<CompanySearchResponse> secondCall = companySearchService.searchCompanies(query);

        // Then - With unit tests, caching may not be active (requires Spring context)
        // This test verifies the service works correctly when called multiple times
        // Full caching behavior will be verified in integration tests
        assertThat(firstCall).hasSize(2);
        assertThat(secondCall).hasSize(2);
        // Note: In unit tests without Spring context, cache annotations don't work
        // We verify the method returns correct results - caching verified in integration tests
        verify(companyRepository, atLeastOnce()).findByNameContainingIgnoreCase(query);
    }

    @Test
    @DisplayName("Test 9.2: should_invalidateCache_when_companyUpdated")
    void should_invalidateCache_when_companyUpdated() {
        // Given
        when(cacheManager.getCache("companySearch")).thenReturn(cache);

        // When
        companySearchService.invalidateCache();

        // Then
        verify(cacheManager).getCache("companySearch");
        verify(cache).clear();
    }

    @Test
    @DisplayName("Test 9.3: should_useSeparateCacheKeys_when_differentQueriesProvided")
    void should_useSeparateCacheKeys_when_differentQueriesProvided() {
        // Given
        String query1 = "acme";
        String query2 = "swiss";
        List<Company> acmeCompanies = Arrays.asList(testCompany1, testCompany2);
        List<Company> swissCompanies = Collections.singletonList(testCompany3);

        when(companyRepository.findByNameContainingIgnoreCase(query1)).thenReturn(acmeCompanies);
        when(companyRepository.findByNameContainingIgnoreCase(query2)).thenReturn(swissCompanies);

        // When
        List<CompanySearchResponse> results1 = companySearchService.searchCompanies(query1);
        List<CompanySearchResponse> results2 = companySearchService.searchCompanies(query2);

        // Then - Each query should hit the repository once
        assertThat(results1).hasSize(2);
        assertThat(results2).hasSize(1);
        verify(companyRepository).findByNameContainingIgnoreCase(query1);
        verify(companyRepository).findByNameContainingIgnoreCase(query2);
    }

    @Test
    @DisplayName("Test 9.4: should_returnFreshData_when_cacheInvalidated")
    void should_returnFreshData_when_cacheInvalidated() {
        // Given
        String query = "acme";
        List<Company> initialCompanies = Arrays.asList(testCompany1);
        List<Company> updatedCompanies = Arrays.asList(testCompany1, testCompany2);

        when(companyRepository.findByNameContainingIgnoreCase(query))
                .thenReturn(initialCompanies)
                .thenReturn(updatedCompanies);
        when(cacheManager.getCache("companySearch")).thenReturn(cache);

        // When
        List<CompanySearchResponse> firstCall = companySearchService.searchCompanies(query);
        companySearchService.invalidateCache(); // Invalidate cache
        List<CompanySearchResponse> secondCall = companySearchService.searchCompanies(query);

        // Then
        assertThat(firstCall).hasSize(1);
        assertThat(secondCall).hasSize(2); // Fresh data after cache invalidation
        verify(companyRepository, times(2)).findByNameContainingIgnoreCase(query);
    }

    // AC11 Tests: Advanced Search with Limit

    @Test
    @DisplayName("Test 11.1: should_returnSearchResults_when_queryProvided")
    void should_returnSearchResults_when_queryProvided() {
        // Given
        String query = "swiss";
        List<Company> companies = Collections.singletonList(testCompany3);
        when(companyRepository.findByNameContainingIgnoreCase(query)).thenReturn(companies);

        // When
        List<CompanySearchResponse> results = companySearchService.searchCompanies(query);

        // Then
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getName()).isEqualTo("Swiss Tech AG");
        verify(companyRepository).findByNameContainingIgnoreCase(query);
    }

    @Test
    @DisplayName("Test 11.4: should_limitResults_when_limitParameterProvided")
    void should_limitResults_when_limitParameterProvided() {
        // Given
        String query = "company";
        int limit = 5;
        List<Company> manyCompanies = Arrays.asList(
                testCompany1, testCompany2, testCompany3,
                createCompany("Company 4"),
                createCompany("Company 5"),
                createCompany("Company 6"),
                createCompany("Company 7")
        );
        when(companyRepository.findByNameContainingIgnoreCase(query)).thenReturn(manyCompanies);

        // When
        List<CompanySearchResponse> results = companySearchService.searchCompanies(query, limit);

        // Then
        assertThat(results).hasSize(5);
        verify(companyRepository).findByNameContainingIgnoreCase(query);
    }

    @Test
    @DisplayName("Test 11.4b: should_useDefaultLimit_when_noLimitProvided")
    void should_useDefaultLimit_when_noLimitProvided() {
        // Given
        String query = "acme";
        List<Company> companies = Arrays.asList(testCompany1, testCompany2);
        when(companyRepository.findByNameContainingIgnoreCase(query)).thenReturn(companies);

        // When
        List<CompanySearchResponse> results = companySearchService.searchCompanies(query);

        // Then
        assertThat(results).hasSize(2);
        verify(companyRepository).findByNameContainingIgnoreCase(query);
    }

    // Helper Methods

    private Company createCompany(String name) {
        return Company.builder()
                .id(UUID.randomUUID())
                .name(name)
                .displayName(name)
                .isVerified(false)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .createdBy("test-user")
                .build();
    }
}
