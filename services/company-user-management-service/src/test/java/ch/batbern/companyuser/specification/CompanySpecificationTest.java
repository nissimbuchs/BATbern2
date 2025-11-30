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
import java.time.temporal.ChronoUnit;
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
    private Company oldCompany;

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

        // Create old company for date filtering tests
        Instant tenDaysAgo = Instant.now().minus(10, ChronoUnit.DAYS);
        oldCompany = Company.builder()
            .name("Old Company")
            .displayName("Old Legacy Company")
            .swissUID("CHE-444.444.444")
            .industry("Technology")
            .description("Legacy technology company")
            .isVerified(false)
            .createdBy("test-user")
            .build();
        oldCompany = companyRepository.save(oldCompany);
        // Manually set old timestamp
        oldCompany.setCreatedAt(tenDaysAgo);
        oldCompany.setUpdatedAt(tenDaysAgo);
        companyRepository.saveAndFlush(oldCompany);
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

        // Then - Tech Corp and Old Company are both Technology
        assertThat(result).hasSize(2);
        assertThat(result).extracting(Company::getIndustry)
            .containsOnly("Technology");
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

        // Then - Tech Corp, Old Company (Technology) and Finance Group
        assertThat(result).hasSize(3);
        assertThat(result).extracting(Company::getIndustry)
            .containsOnly("Technology", "Finance");
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

    @Test
    @DisplayName("STARTS_WITH operator - should filter by prefix")
    void should_filterByPrefix_when_startsWithOperatorUsed() {
        // Given
        FilterCriteria criteria = FilterCriteria.builder()
            .field("name")
            .operator(FilterOperator.STARTS_WITH)
            .value("Tech")
            .build();

        // When
        Specification<Company> spec = CompanySpecification.fromFilterCriteria(criteria);
        List<Company> result = companyRepository.findAll(spec);

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Tech Corp");
    }

    @Test
    @DisplayName("ENDS_WITH operator - should filter by suffix")
    void should_filterBySuffix_when_endsWithOperatorUsed() {
        // Given
        FilterCriteria criteria = FilterCriteria.builder()
            .field("name")
            .operator(FilterOperator.ENDS_WITH)
            .value("Solutions")
            .build();

        // When
        Specification<Company> spec = CompanySpecification.fromFilterCriteria(criteria);
        List<Company> result = companyRepository.findAll(spec);

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Health Solutions");
    }

    @Test
    @DisplayName("GREATER_THAN_OR_EQUAL operator - should filter by greater than or equal")
    void should_filterByGreaterThanOrEqual_when_gteUsed() {
        // Given - use createdBy field with a value to test GREATER_THAN_OR_EQUAL
        // Since we can't reliably manipulate timestamps in @Transactional tests,
        // we'll test with a comparable string field instead
        FilterCriteria criteria = FilterCriteria.builder()
            .field("name")
            .operator(FilterOperator.GREATER_THAN_OR_EQUAL)
            .value("Health Solutions")
            .build();

        // When
        Specification<Company> spec = CompanySpecification.fromFilterCriteria(criteria);
        List<Company> result = companyRepository.findAll(spec);

        // Then - should return companies with name >= "Health Solutions" alphabetically
        assertThat(result).isNotEmpty();
        assertThat(result).allMatch(c -> c.getName().compareTo("Health Solutions") >= 0);
        assertThat(result).extracting(Company::getName)
            .contains("Health Solutions", "Old Company", "Tech Corp");
    }

    @Test
    @DisplayName("LESS_THAN_OR_EQUAL operator - should filter by less than or equal")
    void should_filterByLessThanOrEqual_when_lteUsed() {
        // Given - use name field with LESS_THAN_OR_EQUAL
        // Test with comparable string field since timestamp manipulation doesn't work in @Transactional
        FilterCriteria criteria = FilterCriteria.builder()
            .field("name")
            .operator(FilterOperator.LESS_THAN_OR_EQUAL)
            .value("Health Solutions")
            .build();

        // When
        Specification<Company> spec = CompanySpecification.fromFilterCriteria(criteria);
        List<Company> result = companyRepository.findAll(spec);

        // Then - should return companies with name <= "Health Solutions" alphabetically
        assertThat(result).isNotEmpty();
        assertThat(result).allMatch(c -> c.getName().compareTo("Health Solutions") <= 0);
        assertThat(result).extracting(Company::getName)
            .contains("Finance Group", "Health Solutions");
    }

    @Test
    @DisplayName("NOT_EQUALS operator - should filter by not equal")
    void should_filterByNotEqual_when_notEqualsOperatorUsed() {
        // Given
        FilterCriteria criteria = FilterCriteria.builder()
            .field("industry")
            .operator(FilterOperator.NOT_EQUALS)
            .value("Technology")
            .build();

        // When
        Specification<Company> spec = CompanySpecification.fromFilterCriteria(criteria);
        List<Company> result = companyRepository.findAll(spec);

        // Then - should return Finance and Healthcare companies only
        assertThat(result).hasSize(2);
        assertThat(result).extracting(Company::getIndustry)
            .containsExactlyInAnyOrder("Finance", "Healthcare");
    }

    @Test
    @DisplayName("NOT_IN operator - should filter by not in list")
    void should_filterByNotInList_when_notInOperatorUsed() {
        // Given
        FilterCriteria criteria = FilterCriteria.builder()
            .field("industry")
            .operator(FilterOperator.NOT_IN)
            .value(List.of("Technology"))
            .build();

        // When
        Specification<Company> spec = CompanySpecification.fromFilterCriteria(criteria);
        List<Company> result = companyRepository.findAll(spec);

        // Then - should exclude Technology companies
        assertThat(result).hasSize(2);
        assertThat(result).extracting(Company::getIndustry)
            .containsExactlyInAnyOrder("Finance", "Healthcare");
    }

    @Test
    @DisplayName("NOT operator - should negate nested criteria")
    void should_negateCriteria_when_notOperatorUsed() {
        // Given - NOT (industry = "Healthcare")
        FilterCriteria criteria = FilterCriteria.builder()
            .operator(FilterOperator.NOT)
            .children(List.of(
                FilterCriteria.builder()
                    .field("industry")
                    .operator(FilterOperator.EQUALS)
                    .value("Healthcare")
                    .build()
            ))
            .build();

        // When
        Specification<Company> spec = CompanySpecification.fromFilterCriteria(criteria);
        List<Company> result = companyRepository.findAll(spec);

        // Then - should return all except Healthcare
        assertThat(result).hasSize(3);
        assertThat(result).extracting(Company::getIndustry)
            .doesNotContain("Healthcare");
    }

    @Test
    @DisplayName("Complex nested filters - should handle AND/OR combinations")
    void should_handleComplexNestedFilters_when_combinedAndOr() {
        // Given - (industry = "Technology" AND verified = true) OR industry = "Finance"
        FilterCriteria techAndVerified = FilterCriteria.builder()
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

        FilterCriteria financeFilter = FilterCriteria.builder()
            .field("industry")
            .operator(FilterOperator.EQUALS)
            .value("Finance")
            .build();

        FilterCriteria orCriteria = FilterCriteria.builder()
            .operator(FilterOperator.OR)
            .children(List.of(techAndVerified, financeFilter))
            .build();

        // When
        Specification<Company> spec = CompanySpecification.fromFilterCriteria(orCriteria);
        List<Company> result = companyRepository.findAll(spec);

        // Then - should return Tech Corp (verified Technology) and Finance Group
        assertThat(result).hasSize(2);
        assertThat(result).extracting(Company::getName)
            .containsExactlyInAnyOrder("Tech Corp", "Finance Group");
    }

    @Test
    @DisplayName("IS_NULL operator - should filter by null value check")
    void should_filterByNullCheck_when_isNullOperatorUsed() {
        // Given - filter for companies without website (Healthcare and Old Company have no website)
        FilterCriteria criteria = FilterCriteria.builder()
            .field("website")
            .operator(FilterOperator.IS_NULL)
            .value(true)
            .build();

        // When
        Specification<Company> spec = CompanySpecification.fromFilterCriteria(criteria);
        List<Company> result = companyRepository.findAll(spec);

        // Then - should return companies without website
        assertThat(result).hasSizeGreaterThanOrEqualTo(2);
        assertThat(result).extracting(Company::getName)
            .contains("Health Solutions", "Old Company");
        assertThat(result).allMatch(c -> c.getWebsite() == null);
    }
}
