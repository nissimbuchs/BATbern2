package ch.batbern.companyuser.specification;

import ch.batbern.companyuser.config.TestAwsConfig;
import ch.batbern.companyuser.domain.Company;
import ch.batbern.companyuser.repository.CompanyRepository;
import ch.batbern.shared.api.FilterCriteria;
import ch.batbern.shared.api.FilterOperator;
import ch.batbern.shared.test.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for CompanySpecification - SQL Injection-safe filtering
 *
 * Tests JPA Specification builder for safe dynamic queries.
 * Uses Testcontainers PostgreSQL for production parity.
 */
@Transactional
@Import(TestAwsConfig.class)
@DisplayName("CompanySpecification Tests")
class CompanySpecificationTest extends AbstractIntegrationTest {

    @Autowired
    private CompanyRepository companyRepository;

    private Company techCompany;
    private Company financeCompany;
    private Company healthcareCompany;

    @BeforeEach
    void setUp() {
        // Clean up any existing companies to ensure test isolation
        companyRepository.deleteAll();
        companyRepository.flush();

        // Create test companies with all required fields
        techCompany = Company.builder()
            .name("Tech Corp")
            .displayName("Technology Corporation")
            .swissUID("CHE-111.111.111")
            .industry("Technology")
            .website("https://techcorp.ch")
            .description("Leading tech company")
            .isVerified(true)
            .createdBy("test-user")
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();
        companyRepository.saveAndFlush(techCompany);

        financeCompany = Company.builder()
            .name("Finance Group")
            .displayName("Financial Services Group")
            .swissUID("CHE-222.222.222")
            .industry("Finance")
            .website("https://financegroup.ch")
            .description("Financial services provider")
            .isVerified(false)
            .createdBy("test-user")
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();
        companyRepository.saveAndFlush(financeCompany);

        healthcareCompany = Company.builder()
            .name("Health Solutions")
            .displayName("Healthcare Solutions Ltd")
            .swissUID("CHE-333.333.333")
            .industry("Healthcare")
            .description("Healthcare technology solutions")
            .isVerified(true)
            .createdBy("test-user")
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();
        companyRepository.saveAndFlush(healthcareCompany);
    }

    @Test
    @DisplayName("EQUALS operator - should filter by exact match")
    void should_filterByExactMatch_when_equalsOperatorUsed() {
        // Given
        FilterCriteria criteria = FilterCriteria.builder()
            .field("industry")
            .operator(FilterOperator.EQUALS)
            .value("Technology")
            .build();

        // When
        Specification<Company> spec = CompanySpecification.fromFilterCriteria(criteria);
        List<Company> result = companyRepository.findAll(spec);

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Tech Corp");
    }

    @Test
    @DisplayName("CONTAINS operator - should filter by substring (case-insensitive)")
    void should_filterBySubstring_when_containsOperatorUsed() {
        // Given
        FilterCriteria criteria = FilterCriteria.builder()
            .field("displayName")
            .operator(FilterOperator.CONTAINS)
            .value("solutions")  // lowercase to test case-insensitivity
            .build();

        // When
        Specification<Company> spec = CompanySpecification.fromFilterCriteria(criteria);
        List<Company> result = companyRepository.findAll(spec);

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Health Solutions");
    }

    @Test
    @DisplayName("IN operator - should filter by value list")
    void should_filterByValueList_when_inOperatorUsed() {
        // Given
        FilterCriteria criteria = FilterCriteria.builder()
            .field("industry")
            .operator(FilterOperator.IN)
            .value(List.of("Technology", "Finance"))
            .build();

        // When
        Specification<Company> spec = CompanySpecification.fromFilterCriteria(criteria);
        List<Company> result = companyRepository.findAll(spec);

        // Then
        assertThat(result).hasSize(2);
        assertThat(result).extracting(Company::getIndustry)
            .containsExactlyInAnyOrder("Technology", "Finance");
    }

    @Test
    @DisplayName("AND operator - should combine criteria with logical AND")
    void should_combineCriteria_when_andOperatorUsed() {
        // Given - companies in Technology industry AND verified
        FilterCriteria criteria = FilterCriteria.builder()
            .operator(FilterOperator.AND)
            .children(List.of(
                FilterCriteria.builder()
                    .field("industry")
                    .operator(FilterOperator.EQUALS)
                    .value("Technology")
                    .build(),
                FilterCriteria.builder()
                    .field("isVerified")
                    .operator(FilterOperator.EQUALS)
                    .value(true)
                    .build()
            ))
            .build();

        // When
        Specification<Company> spec = CompanySpecification.fromFilterCriteria(criteria);
        List<Company> result = companyRepository.findAll(spec);

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Tech Corp");
    }

    @Test
    @DisplayName("OR operator - should combine criteria with logical OR")
    void should_combineCriteria_when_orOperatorUsed() {
        // Given - companies in Finance industry OR verified
        FilterCriteria criteria = FilterCriteria.builder()
            .operator(FilterOperator.OR)
            .children(List.of(
                FilterCriteria.builder()
                    .field("industry")
                    .operator(FilterOperator.EQUALS)
                    .value("Finance")
                    .build(),
                FilterCriteria.builder()
                    .field("isVerified")
                    .operator(FilterOperator.EQUALS)
                    .value(true)
                    .build()
            ))
            .build();

        // When
        Specification<Company> spec = CompanySpecification.fromFilterCriteria(criteria);
        List<Company> result = companyRepository.findAll(spec);

        // Then - should include Finance Group (finance) + Tech Corp (verified) + Health Solutions (verified)
        assertThat(result).hasSize(3);
    }

    @Test
    @DisplayName("Null criteria - should return null specification")
    void should_returnNull_when_criteriaIsNull() {
        // When
        Specification<Company> spec = CompanySpecification.fromFilterCriteria(null);

        // Then
        assertThat(spec).isNull();
    }
}
