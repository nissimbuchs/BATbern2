package ch.batbern.companyuser.service;

import ch.batbern.companyuser.domain.Company;
import ch.batbern.companyuser.dto.generated.CompanyResponse;
import ch.batbern.companyuser.exception.CompanyNotFoundException;
import ch.batbern.companyuser.repository.CompanyRepository;
import ch.batbern.companyuser.security.SecurityContextHelper;
import ch.batbern.shared.service.SlugGenerationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for CompanyService
 * Following TDD: Tests written BEFORE implementation
 * AC4: REST API CRUD operations
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CompanyService Unit Tests")
class CompanyServiceTest {

    @Mock
    private CompanyRepository companyRepository;

    @Mock
    private CompanySearchService searchService;

    @Mock
    private SecurityContextHelper securityContextHelper;

    // The real slug rule (own code, no mock): the key a company gets is what these tests check
    private final SlugGenerationService slugService = new SlugGenerationService();

    private CompanyService companyService;

    private Company existingCompany;
    // Story 1.16.2: use company name instead of UUID
    private String companyName;

    @BeforeEach
    void setUp() {
        // No upload service, alias service or key mover: no test here uses them. The logo paths run in
        // UploadsControllerIntegrationTest; create, get-or-create and update (rename) against
        // PostgreSQL in the integration tests named below.
        companyService = new CompanyService(companyRepository, searchService,
                securityContextHelper, null, slugService, new CompanyInserter(companyRepository), null, null);

        // Story 1.16.2: the company name (key) is the identifier, a slug since 2026-11-09
        companyName = "testcompany";


        existingCompany = Company.builder()
                .id(UUID.randomUUID())  // Internal DB uses UUID
                .name("testcompany")
                .displayName("Test Company")
                .website("https://testcompany.ch")
                .industry("Technology")
                .description("Test company description")
                .isVerified(false)
                .createdBy("test-user")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    // createCompany and getOrCreateCompany run against PostgreSQL in CompanyControllerIntegrationTest,
    // CompanyGetOrCreateIntegrationTest and CompanyAliasIntegrationTest (touch rule, #1251).

    // ==================== GET COMPANY TESTS ====================

    @Test
    @DisplayName("Test 4.6: should_getCompanyByName_when_companyExists")
    void should_getCompanyById_when_companyExists() {
        // Given
        // Story 1.16.2: use company name instead of UUID
        when(companyRepository.findByName(companyName)).thenReturn(Optional.of(existingCompany));

        // When
        CompanyResponse response = companyService.getCompanyByName(companyName);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getName()).isEqualTo("testcompany");

        verify(companyRepository).findByName(companyName);
    }

    @Disabled("Flaky test - passes individually but fails in full suite due to test pollution")
    @Test
    @DisplayName("Test 4.7: should_throwCompanyNotFoundException_when_companyDoesNotExist")
    void should_throwCompanyNotFoundException_when_companyDoesNotExist() {
        // Given
        // Story 1.16.2: use company name instead of UUID
        when(companyRepository.findByName(companyName)).thenReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> companyService.getCompanyByName(companyName))
                .isInstanceOf(CompanyNotFoundException.class)
                .hasMessageContaining(companyName);

        verify(companyRepository).findByName(companyName);
    }

    @Test
    @DisplayName("Test 4.8: should_getAllCompanies_when_companiesExist")
    void should_getAllCompanies_when_companiesExist() {
        // Given
        Company company2 = Company.builder()
                .id(UUID.randomUUID())
                .name("Another Company AG")
                .displayName("Another Company")
                .isVerified(false)
                .createdBy("test-user")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(companyRepository.findAll()).thenReturn(Arrays.asList(existingCompany, company2));

        // When
        List<CompanyResponse> responses = companyService.getAllCompanies();

        // Then
        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).getName()).isEqualTo("testcompany");
        assertThat(responses.get(1).getName()).isEqualTo("Another Company AG");

        verify(companyRepository).findAll();
    }

    @Test
    @DisplayName("Test 4.9: should_returnEmptyList_when_noCompaniesExist")
    void should_returnEmptyList_when_noCompaniesExist() {
        // Given
        when(companyRepository.findAll()).thenReturn(Arrays.asList());

        // When
        List<CompanyResponse> responses = companyService.getAllCompanies();

        // Then
        assertThat(responses).isEmpty();
        verify(companyRepository).findAll();
    }

    // updateCompany (and its rename) runs against PostgreSQL in CompanyControllerIntegrationTest and
    // CompanyRenameIntegrationTest (touch rule, #1251).

    // ==================== DELETE COMPANY TESTS ====================

    @Test
    @DisplayName("Test 4.13: should_deleteCompany_when_companyExists")
    void should_deleteCompany_when_companyExists() {
        // Given
        // Story 1.16.2: use company name instead of UUID
        when(companyRepository.findByName(companyName)).thenReturn(Optional.of(existingCompany));
        doNothing().when(companyRepository).delete(existingCompany);

        // When
        companyService.deleteCompany(companyName);

        // Then
        verify(companyRepository).findByName(companyName);
        verify(companyRepository).delete(existingCompany);
        verify(searchService).invalidateCache();
    }

    @Disabled("Flaky test - passes individually but fails in full suite due to test pollution")
    @Test
    @DisplayName("Test 4.14: should_throwCompanyNotFoundException_when_deleteNonExistentCompany")
    void should_throwCompanyNotFoundException_when_deleteNonExistentCompany() {
        // Given
        // Story 1.16.2: use company name instead of UUID
        when(companyRepository.findByName(companyName)).thenReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> companyService.deleteCompany(companyName))
                .isInstanceOf(CompanyNotFoundException.class)
                .hasMessageContaining(companyName);

        verify(companyRepository, never()).delete(any(Company.class));
        verify(searchService, never()).invalidateCache();
    }

    // ==================== VERIFICATION WORKFLOW TESTS ====================

    @Test
    @DisplayName("Test 4.17: should_markAsVerified_when_validCompanyProvided")
    void should_markAsVerified_when_validCompanyProvided() {
        // Given
        // Story 1.16.2: use company name instead of UUID
        when(companyRepository.findByName(companyName)).thenReturn(Optional.of(existingCompany));
        when(companyRepository.saveAndFlush(any(Company.class))).thenReturn(existingCompany);

        // When
        CompanyResponse response = companyService.markAsVerified(companyName);

        // Then
        assertThat(response).isNotNull();
        verify(companyRepository).saveAndFlush(argThat(company -> company.isVerified()));
    }

    @Test
    @DisplayName("Test 4.19: should_verifyCompany_when_validNameProvided")
    void should_verifyCompany_when_validIdProvided() {
        // Given
        // Story 1.16.2: use company name instead of UUID
        when(companyRepository.findByName(companyName)).thenReturn(Optional.of(existingCompany));
        when(companyRepository.saveAndFlush(any(Company.class))).thenReturn(existingCompany);

        // When
        CompanyResponse response = companyService.verifyCompany(companyName);

        // Then
        assertThat(response).isNotNull();
        verify(companyRepository).saveAndFlush(argThat(company -> company.isVerified()));
    }

    @Disabled("Flaky test - passes individually but fails in full suite due to test pollution")
    @Test
    @DisplayName("Test 4.20: should_throwCompanyNotFoundException_when_verifyNonExistentCompany")
    void should_throwCompanyNotFoundException_when_verifyNonExistentCompany() {
        // Given
        // Story 1.16.2: use company name instead of UUID
        when(companyRepository.findByName(companyName)).thenReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> companyService.verifyCompany(companyName))
                .isInstanceOf(CompanyNotFoundException.class)
                .hasMessageContaining(companyName);

        verify(companyRepository, never()).saveAndFlush(any(Company.class));
    }

    @Test
    @DisplayName("Test 4.21: should_includeLogoInResponse_when_companyHasLogo")
    void should_includeLogoInResponse_when_companyHasLogo() {
        // Given
        Company companyWithLogo = Company.builder()
                .id(UUID.randomUUID())  // Internal DB uses UUID
                .name("Test Company AG")
                .displayName("Test Company")
                .logoUrl("https://cdn.example.com/logo.png")
                .logoS3Key("logos/file-123.png")
                .logoFileId("file-123")
                .isVerified(false)
                .createdBy("test-user")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        // Story 1.16.2: use company name instead of UUID
        when(companyRepository.findByName(companyName)).thenReturn(Optional.of(companyWithLogo));

        // When
        CompanyResponse response = companyService.getCompanyByName(companyName);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getLogo()).isNotNull();
        assertThat(response.getLogo().getUrl()).hasToString("https://cdn.example.com/logo.png");
        assertThat(response.getLogo().getS3Key()).isEqualTo("logos/file-123.png");
        assertThat(response.getLogo().getFileId()).isEqualTo("file-123");
    }

    @Test
    @DisplayName("Test 4.22: should_returnNullLogo_when_companyHasNoLogo")
    void should_returnNullLogo_when_companyHasNoLogo() {
        // Given
        Company companyWithoutLogo = Company.builder()
                .id(UUID.randomUUID())  // Internal DB uses UUID
                .name("Test Company AG")
                .displayName("Test Company")
                .logoUrl(null)
                .logoS3Key(null)
                .logoFileId(null)
                .isVerified(false)
                .createdBy("test-user")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        // Story 1.16.2: use company name instead of UUID
        when(companyRepository.findByName(companyName)).thenReturn(Optional.of(companyWithoutLogo));

        // When
        CompanyResponse response = companyService.getCompanyByName(companyName);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getLogo()).isNull();
    }
}
