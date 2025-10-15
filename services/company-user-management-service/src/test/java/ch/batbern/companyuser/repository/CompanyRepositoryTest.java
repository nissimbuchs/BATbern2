package ch.batbern.companyuser.repository;

import ch.batbern.companyuser.config.TestAwsConfig;
import ch.batbern.companyuser.domain.Company;
import ch.batbern.shared.test.AbstractIntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for CompanyRepository
 * Tests cover AC1, AC2 - Company persistence and queries
 * Uses Testcontainers PostgreSQL for production parity
 */
@Transactional
@Import(TestAwsConfig.class)
@DisplayName("Company Repository Tests")
class CompanyRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void setUp() {
        companyRepository.deleteAll();
    }

    // AC1 Tests: Company CRUD Operations

    @Test
    @DisplayName("should_saveCompany_when_validCompanyProvided")
    void should_saveCompany_when_validCompanyProvided() {
        // Given
        Company company = Company.builder()
                .name("Test Company AG")
                .swissUID("CHE-123.456.789")
                .displayName("Test Company")
                .isVerified(false)
                .createdBy("user-123")
                .build();
        company.onCreate();

        // When
        Company saved = companyRepository.save(company);
        entityManager.flush();

        // Then
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getName()).isEqualTo("Test Company AG");
        assertThat(saved.getSwissUID()).isEqualTo("CHE-123.456.789");
    }

    @Test
    @DisplayName("should_findById_when_companyExists")
    void should_findById_when_companyExists() {
        // Given
        Company company = createAndSaveCompany("Find By ID Test");

        // When
        Optional<Company> found = companyRepository.findById(company.getId());

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Find By ID Test");
    }

    @Test
    @DisplayName("should_returnEmpty_when_companyNotFound")
    void should_returnEmpty_when_companyNotFound() {
        // Given
        UUID nonExistentId = UUID.randomUUID();

        // When
        Optional<Company> found = companyRepository.findById(nonExistentId);

        // Then
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("should_updateCompany_when_modificationsMade")
    void should_updateCompany_when_modificationsMade() {
        // Given
        Company company = createAndSaveCompany("Original Name");

        // When
        company.setName("Updated Name");
        company.setWebsite("https://www.updated.ch");
        company.onUpdate();
        Company updated = companyRepository.save(company);
        entityManager.flush();

        // Then
        Company retrieved = companyRepository.findById(updated.getId()).orElseThrow();
        assertThat(retrieved.getName()).isEqualTo("Updated Name");
        assertThat(retrieved.getWebsite()).isEqualTo("https://www.updated.ch");
        assertThat(retrieved.getUpdatedAt()).isAfter(retrieved.getCreatedAt());
    }

    @Test
    @DisplayName("should_deleteCompany_when_requested")
    void should_deleteCompany_when_requested() {
        // Given
        Company company = createAndSaveCompany("To Be Deleted");
        UUID companyId = company.getId();

        // When
        companyRepository.delete(company);
        entityManager.flush();

        // Then
        Optional<Company> deleted = companyRepository.findById(companyId);
        assertThat(deleted).isEmpty();
    }

    // AC2 Tests: Custom Query Methods

    @Test
    @DisplayName("should_findByName_when_exactNameProvided")
    void should_findByName_when_exactNameProvided() {
        // Given
        createAndSaveCompany("Exact Name Company");

        // When
        Optional<Company> found = companyRepository.findByName("Exact Name Company");

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Exact Name Company");
    }

    @Test
    @DisplayName("should_findByNameContaining_when_partialNameProvided")
    void should_findByNameContaining_when_partialNameProvided() {
        // Given
        createAndSaveCompany("Swiss Tech AG");
        createAndSaveCompany("Swiss Finance Corp");
        createAndSaveCompany("German Bank");

        // When
        List<Company> swissCompanies = companyRepository.findByNameContainingIgnoreCase("Swiss");

        // Then
        assertThat(swissCompanies).hasSize(2);
        assertThat(swissCompanies)
                .extracting(Company::getName)
                .contains("Swiss Tech AG", "Swiss Finance Corp");
    }

    @Test
    @DisplayName("should_findBySwissUID_when_uidProvided")
    void should_findBySwissUID_when_uidProvided() {
        // Given
        String uid = "CHE-111.222.333";
        Company company = createAndSaveCompany("UID Test Company");
        company.setSwissUID(uid);
        companyRepository.save(company);
        entityManager.flush();

        // When
        Optional<Company> found = companyRepository.findBySwissUID(uid);

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getSwissUID()).isEqualTo(uid);
    }

    @Test
    @DisplayName("should_existsByName_when_companyExists")
    void should_existsByName_when_companyExists() {
        // Given
        createAndSaveCompany("Existing Company");

        // When
        boolean exists = companyRepository.existsByName("Existing Company");
        boolean notExists = companyRepository.existsByName("Non-Existing Company");

        // Then
        assertThat(exists).isTrue();
        assertThat(notExists).isFalse();
    }

    // AC3 Tests: Data Validation and Constraints

    @Test
    @DisplayName("should_throwException_when_duplicateNameInserted")
    void should_throwException_when_duplicateNameInserted() {
        // Given
        createAndSaveCompany("Duplicate Name");

        // When/Then - PostgreSQL returns different error message than H2
        assertThatThrownBy(() -> {
            Company duplicate = Company.builder()
                    .name("Duplicate Name")
                    .createdBy("user-456")
                    .build();
            duplicate.onCreate();
            companyRepository.save(duplicate);
            entityManager.flush();
        }).satisfiesAnyOf(
                // PostgreSQL error message
                e -> assertThat(e.getMessage()).containsIgnoringCase("duplicate key"),
                // H2 error message (legacy)
                e -> assertThat(e.getMessage()).containsIgnoringCase("Unique index or primary key violation")
        );
    }

    @Test
    @DisplayName("should_countCompanies_when_companiesExist")
    void should_countCompanies_when_companiesExist() {
        // Given
        createAndSaveCompany("Company 1");
        createAndSaveCompany("Company 2");
        createAndSaveCompany("Company 3");

        // When
        long count = companyRepository.count();

        // Then
        assertThat(count).isEqualTo(3);
    }

    @Test
    @DisplayName("should_findAllCompanies_when_multipleExist")
    void should_findAllCompanies_when_multipleExist() {
        // Given
        createAndSaveCompany("Company A");
        createAndSaveCompany("Company B");

        // When
        List<Company> all = companyRepository.findAll();

        // Then
        assertThat(all).hasSize(2);
    }

    // Helper Methods

    private Company createAndSaveCompany(String name) {
        Company company = Company.builder()
                .name(name)
                .displayName(name)
                .isVerified(false)
                .createdBy("test-user")
                .build();
        company.onCreate();
        return companyRepository.saveAndFlush(company);
    }
}
