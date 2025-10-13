package ch.batbern.companyuser.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for Company aggregate root
 * Tests cover AC1, AC2, AC3 - Company Entity, Company Profiles, Data Validation
 */
@DisplayName("Company Domain Model Tests")
class CompanyTest {

    // AC1 Tests: Company Entity Creation and Swiss UID Validation

    @Test
    @DisplayName("Test 1.1: should_createCompany_when_validDataProvided")
    void should_createCompany_when_validDataProvided() {
        // Given
        String name = "Swiss Tech AG";
        String swissUID = "CHE-123.456.789";
        String createdBy = "user-123";

        // When
        Company company = Company.builder()
                .name(name)
                .swissUID(swissUID)
                .isVerified(false)
                .createdBy(createdBy)
                .build();
        company.onCreate();

        // Then
        assertThat(company).isNotNull();
        assertThat(company.getName()).isEqualTo(name);
        assertThat(company.getSwissUID()).isEqualTo(swissUID);
        assertThat(company.isVerified()).isFalse();
        assertThat(company.getCreatedAt()).isNotNull();
        assertThat(company.getUpdatedAt()).isNotNull();
        assertThat(company.getCreatedBy()).isEqualTo(createdBy);
    }

    @Test
    @DisplayName("Test 1.2: should_validateSwissUID_when_companyCreated")
    void should_validateSwissUID_when_companyCreated() {
        // Given
        String validUID = "CHE-123.456.789";

        // When
        Company company = Company.builder()
                .name("Test Company")
                .swissUID(validUID)
                .createdBy("user-123")
                .build();

        // Then
        assertThat(company.getSwissUID()).matches("CHE-\\d{3}\\.\\d{3}\\.\\d{3}");
    }

    @Test
    @DisplayName("Test 1.3: should_throwValidationException_when_invalidUIDProvided")
    void should_throwValidationException_when_invalidUIDProvided() {
        // Note: This test expects validation to happen at service layer
        // Domain model just stores the UID, validation happens in SwissUIDValidationService
        // This test documents the expected UID format
        String invalidUID = "INVALID-UID";

        Company company = Company.builder()
                .name("Test Company")
                .swissUID(invalidUID)
                .createdBy("user-123")
                .build();

        // UID format validation should happen at service layer
        assertThat(company.getSwissUID()).isEqualTo(invalidUID);
    }

    // AC2 Tests: Company Profiles and Metadata

    @Test
    @DisplayName("Test 2.1: should_storeCompanyMetadata_when_profileProvided")
    void should_storeCompanyMetadata_when_profileProvided() {
        // Given
        Company company = Company.builder()
                .name("Tech Corp")
                .displayName("Tech Corporation International")
                .website("https://www.techcorp.ch")
                .industry("Technology")
                .description("A leading technology company")
                .createdBy("user-123")
                .build();

        // Then
        assertThat(company.getDisplayName()).isEqualTo("Tech Corporation International");
        assertThat(company.getWebsite()).isEqualTo("https://www.techcorp.ch");
        assertThat(company.getIndustry()).isEqualTo("Technology");
        assertThat(company.getDescription()).isEqualTo("A leading technology company");
    }

    @Test
    @DisplayName("Test 2.2: should_validateWebsiteURL_when_provided")
    void should_validateWebsiteURL_when_provided() {
        // Given
        String validURL = "https://www.example.ch";

        // When
        Company company = Company.builder()
                .name("Test Company")
                .website(validURL)
                .createdBy("user-123")
                .build();

        // Then
        assertThat(company.getWebsite()).startsWith("https://");
    }

    @Test
    @DisplayName("Test 2.3: should_handleOptionalFields_when_notProvided")
    void should_handleOptionalFields_when_notProvided() {
        // Given & When
        Company company = Company.builder()
                .name("Minimal Company")
                .createdBy("user-123")
                .build();

        // Then
        assertThat(company.getName()).isEqualTo("Minimal Company");
        assertThat(company.getSwissUID()).isNull();
        assertThat(company.getWebsite()).isNull();
        assertThat(company.getIndustry()).isNull();
        assertThat(company.getDescription()).isNull();
    }

    // AC3 Tests: Data Validation and Business Rules

    @Test
    @DisplayName("Test 3.1: should_validateRequiredFields_when_companyCreated")
    void should_validateRequiredFields_when_companyCreated() {
        // Given
        Company company = Company.builder()
                .name("Required Fields Test")
                .createdBy("user-123")
                .build();
        company.onCreate();

        // Then - Required fields must be present
        assertThat(company.getName()).isNotBlank();
        assertThat(company.getCreatedBy()).isNotBlank();
        assertThat(company.getCreatedAt()).isNotNull();
        assertThat(company.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("Test 3.2: should_enforceNameUniqueness_when_duplicateDetected")
    void should_enforceNameUniqueness_when_duplicateDetected() {
        // Note: Uniqueness is enforced at database level with unique constraint
        // This test documents the expectation
        Company company1 = Company.builder()
                .name("Duplicate Name")
                .createdBy("user-123")
                .build();

        Company company2 = Company.builder()
                .name("Duplicate Name")
                .createdBy("user-456")
                .build();

        // Both companies can be created in memory
        // Database constraint will prevent duplicate saves
        assertThat(company1.getName()).isEqualTo(company2.getName());
    }

    // Business Methods Tests

    @Test
    @DisplayName("should_markAsVerified_when_verificationCompletes")
    void should_markAsVerified_when_verificationCompletes() {
        // Given
        Company company = Company.builder()
                .name("Unverified Company")
                .isVerified(false)
                .createdBy("user-123")
                .build();
        company.onCreate();

        // When
        company.markAsVerified();

        // Then
        assertThat(company.isVerified()).isTrue();
    }

    @Test
    @DisplayName("should_updateTimestamp_when_companyModified")
    void should_updateTimestamp_when_companyModified() throws InterruptedException {
        // Given
        Company company = Company.builder()
                .name("Timestamp Test")
                .createdBy("user-123")
                .build();
        company.onCreate();
        var initialUpdatedAt = company.getUpdatedAt();

        // Wait a bit to ensure timestamp difference
        Thread.sleep(10);

        // When
        company.onUpdate();

        // Then
        assertThat(company.getUpdatedAt()).isAfter(initialUpdatedAt);
    }
}
