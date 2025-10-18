package ch.batbern.companyuser.service;

import ch.batbern.companyuser.domain.Company;
import ch.batbern.companyuser.dto.CreateCompanyRequest;
import ch.batbern.companyuser.dto.UpdateCompanyRequest;
import ch.batbern.companyuser.dto.CompanyResponse;
import ch.batbern.companyuser.exception.CompanyNotFoundException;
import ch.batbern.companyuser.exception.CompanyValidationException;
import ch.batbern.companyuser.exception.InvalidUIDException;
import ch.batbern.companyuser.repository.CompanyRepository;
import ch.batbern.companyuser.security.SecurityContextHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for CompanyService
 * Following TDD: Tests written BEFORE implementation
 * AC4: REST API CRUD operations
 * AC7: Domain events publishing (CompanyCreated, CompanyUpdated, CompanyDeleted)
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CompanyService Unit Tests")
class CompanyServiceTest {

    @Mock
    private CompanyRepository companyRepository;

    @Mock
    private SwissUIDValidationService uidValidationService;

    @Mock
    private CompanyEventPublisher eventPublisher;

    @Mock
    private CompanySearchService searchService;

    @Mock
    private SecurityContextHelper securityContextHelper;

    @InjectMocks
    private CompanyService companyService;

    private CreateCompanyRequest createRequest;
    private UpdateCompanyRequest updateRequest;
    private Company existingCompany;
    private UUID companyId;

    @BeforeEach
    void setUp() {
        companyId = UUID.randomUUID();

        createRequest = CreateCompanyRequest.builder()
                .name("Test Company AG")
                .displayName("Test Company")
                .swissUID("CHE-123.456.789")
                .website("https://testcompany.ch")
                .industry("Technology")
                .description("Test company description")
                .build();

        updateRequest = UpdateCompanyRequest.builder()
                .name("Updated Company AG")
                .displayName("Updated Company")
                .website("https://updated.ch")
                .industry("Finance")
                .description("Updated description")
                .build();

        existingCompany = Company.builder()
                .id(companyId)
                .name("Test Company AG")
                .displayName("Test Company")
                .swissUID("CHE-123.456.789")
                .website("https://testcompany.ch")
                .industry("Technology")
                .description("Test company description")
                .isVerified(false)
                .createdBy("test-user")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    // ==================== CREATE COMPANY TESTS ====================

    @Test
    @DisplayName("Test 4.1: should_createCompany_when_validRequestReceived")
    void should_createCompany_when_validRequestReceived() {
        // Given
        when(securityContextHelper.getCurrentUserId()).thenReturn("test-user");
        when(uidValidationService.isValidUID(createRequest.getSwissUID())).thenReturn(true);
        when(companyRepository.existsByName(createRequest.getName())).thenReturn(false);
        when(companyRepository.save(any(Company.class))).thenReturn(existingCompany);

        // When
        CompanyResponse response = companyService.createCompany(createRequest);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getName()).isEqualTo("Test Company AG");
        assertThat(response.getSwissUID()).isEqualTo("CHE-123.456.789");
        assertThat(response.isVerified()).isFalse();

        verify(securityContextHelper).getCurrentUserId();
        verify(companyRepository).save(any(Company.class));
        verify(eventPublisher).publishCompanyCreatedEvent(any(Company.class));
        verify(searchService).invalidateCache();
    }

    @Test
    @DisplayName("Test 4.2: should_throwInvalidUIDException_when_invalidSwissUIDProvided")
    void should_throwInvalidUIDException_when_invalidSwissUIDProvided() {
        // Given
        when(uidValidationService.isValidUID(createRequest.getSwissUID())).thenReturn(false);

        // When / Then
        assertThatThrownBy(() -> companyService.createCompany(createRequest))
                .isInstanceOf(InvalidUIDException.class)
                .hasMessageContaining(createRequest.getSwissUID());

        verify(companyRepository, never()).save(any(Company.class));
        verify(eventPublisher, never()).publishCompanyCreatedEvent(any(Company.class));
    }

    @Test
    @DisplayName("Test 4.3: should_throwCompanyValidationException_when_duplicateNameExists")
    void should_throwCompanyValidationException_when_duplicateNameExists() {
        // Given
        when(uidValidationService.isValidUID(createRequest.getSwissUID())).thenReturn(true);
        when(companyRepository.existsByName(createRequest.getName())).thenReturn(true);

        // When / Then
        assertThatThrownBy(() -> companyService.createCompany(createRequest))
                .isInstanceOf(CompanyValidationException.class)
                .hasMessageContaining("already exists");

        verify(companyRepository, never()).save(any(Company.class));
        verify(eventPublisher, never()).publishCompanyCreatedEvent(any(Company.class));
    }

    @Test
    @DisplayName("Test 4.4: should_createCompany_when_swissUIDNotProvided")
    void should_createCompany_when_swissUIDNotProvided() {
        // Given
        CreateCompanyRequest requestWithoutUID = CreateCompanyRequest.builder()
                .name("Test Company AG")
                .displayName("Test Company")
                .build();

        Company companyWithoutUID = Company.builder()
                .id(companyId)
                .name("Test Company AG")
                .displayName("Test Company")
                .isVerified(false)
                .createdBy("test-user")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(securityContextHelper.getCurrentUserId()).thenReturn("test-user");
        when(companyRepository.existsByName(requestWithoutUID.getName())).thenReturn(false);
        when(companyRepository.save(any(Company.class))).thenReturn(companyWithoutUID);

        // When
        CompanyResponse response = companyService.createCompany(requestWithoutUID);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getName()).isEqualTo("Test Company AG");
        assertThat(response.getSwissUID()).isNull();

        verify(uidValidationService, never()).isValidUID(anyString());
        verify(companyRepository).save(any(Company.class));
    }

    @Test
    @DisplayName("Test 4.5: should_useNameAsDisplayName_when_displayNameNotProvided")
    void should_useNameAsDisplayName_when_displayNameNotProvided() {
        // Given
        CreateCompanyRequest requestWithoutDisplay = CreateCompanyRequest.builder()
                .name("Test Company AG")
                .build();

        when(securityContextHelper.getCurrentUserId()).thenReturn("test-user");
        when(companyRepository.existsByName(requestWithoutDisplay.getName())).thenReturn(false);
        when(companyRepository.save(any(Company.class))).thenAnswer(invocation -> {
            Company company = invocation.getArgument(0);
            assertThat(company.getDisplayName()).isEqualTo("Test Company AG");
            return company;
        });

        // When
        companyService.createCompany(requestWithoutDisplay);

        // Then
        verify(companyRepository).save(argThat(company ->
            company.getDisplayName().equals("Test Company AG")
        ));
    }

    // ==================== GET COMPANY TESTS ====================

    @Test
    @DisplayName("Test 4.6: should_getCompanyById_when_companyExists")
    void should_getCompanyById_when_companyExists() {
        // Given
        when(companyRepository.findById(companyId)).thenReturn(Optional.of(existingCompany));

        // When
        CompanyResponse response = companyService.getCompanyById(companyId);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(companyId);
        assertThat(response.getName()).isEqualTo("Test Company AG");

        verify(companyRepository).findById(companyId);
    }

    @Test
    @DisplayName("Test 4.7: should_throwCompanyNotFoundException_when_companyDoesNotExist")
    void should_throwCompanyNotFoundException_when_companyDoesNotExist() {
        // Given
        when(companyRepository.findById(companyId)).thenReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> companyService.getCompanyById(companyId))
                .isInstanceOf(CompanyNotFoundException.class)
                .hasMessageContaining(companyId.toString());

        verify(companyRepository).findById(companyId);
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
        assertThat(responses.get(0).getName()).isEqualTo("Test Company AG");
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

    // ==================== UPDATE COMPANY TESTS ====================

    @Test
    @DisplayName("Test 4.10: should_updateCompany_when_validUpdateRequest")
    void should_updateCompany_when_validUpdateRequest() {
        // Given
        when(companyRepository.findById(companyId)).thenReturn(Optional.of(existingCompany));
        when(companyRepository.save(any(Company.class))).thenReturn(existingCompany);

        // When
        CompanyResponse response = companyService.updateCompany(companyId, updateRequest);

        // Then
        assertThat(response).isNotNull();
        verify(companyRepository).findById(companyId);
        verify(companyRepository).save(any(Company.class));
        verify(searchService).invalidateCache();
    }

    @Test
    @DisplayName("Test 4.11: should_throwCompanyNotFoundException_when_updateNonExistentCompany")
    void should_throwCompanyNotFoundException_when_updateNonExistentCompany() {
        // Given
        when(companyRepository.findById(companyId)).thenReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> companyService.updateCompany(companyId, updateRequest))
                .isInstanceOf(CompanyNotFoundException.class)
                .hasMessageContaining(companyId.toString());

        verify(companyRepository, never()).save(any(Company.class));
        verify(searchService, never()).invalidateCache();
    }

    @Test
    @DisplayName("Test 4.12: should_updateOnlyProvidedFields_when_partialUpdateRequest")
    void should_updateOnlyProvidedFields_when_partialUpdateRequest() {
        // Given
        UpdateCompanyRequest partialUpdate = UpdateCompanyRequest.builder()
                .name("New Name Only")
                .build();

        when(companyRepository.findById(companyId)).thenReturn(Optional.of(existingCompany));
        when(companyRepository.save(any(Company.class))).thenReturn(existingCompany);

        // When
        companyService.updateCompany(companyId, partialUpdate);

        // Then
        verify(companyRepository).save(argThat(company ->
            company.getName().equals("New Name Only") &&
            company.getWebsite().equals("https://testcompany.ch") // Original value preserved
        ));
    }

    // ==================== DELETE COMPANY TESTS ====================

    @Test
    @DisplayName("Test 4.13: should_deleteCompany_when_companyExists")
    void should_deleteCompany_when_companyExists() {
        // Given
        when(companyRepository.findById(companyId)).thenReturn(Optional.of(existingCompany));
        doNothing().when(companyRepository).delete(existingCompany);

        // When
        companyService.deleteCompany(companyId);

        // Then
        verify(companyRepository).findById(companyId);
        verify(companyRepository).delete(existingCompany);
        verify(searchService).invalidateCache();
    }

    @Test
    @DisplayName("Test 4.14: should_throwCompanyNotFoundException_when_deleteNonExistentCompany")
    void should_throwCompanyNotFoundException_when_deleteNonExistentCompany() {
        // Given
        when(companyRepository.findById(companyId)).thenReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> companyService.deleteCompany(companyId))
                .isInstanceOf(CompanyNotFoundException.class)
                .hasMessageContaining(companyId.toString());

        verify(companyRepository, never()).delete(any(Company.class));
        verify(searchService, never()).invalidateCache();
    }

    // ==================== VERIFICATION WORKFLOW TESTS ====================

    @Test
    @DisplayName("Test 4.17: should_markAsVerified_when_validCompanyProvided")
    void should_markAsVerified_when_validCompanyProvided() {
        // Given
        when(companyRepository.findById(companyId)).thenReturn(Optional.of(existingCompany));
        when(companyRepository.save(any(Company.class))).thenReturn(existingCompany);

        // When
        CompanyResponse response = companyService.markAsVerified(companyId);

        // Then
        assertThat(response).isNotNull();
        verify(companyRepository).save(argThat(company -> company.isVerified()));
        verify(eventPublisher).publishCompanyVerifiedEvent(any(Company.class));
    }

    @Test
    @DisplayName("Test 4.18: should_publishCompanyUpdatedEvent_when_companyUpdated")
    void should_publishCompanyUpdatedEvent_when_companyUpdated() {
        // Given
        when(companyRepository.findById(companyId)).thenReturn(Optional.of(existingCompany));
        when(companyRepository.save(any(Company.class))).thenReturn(existingCompany);

        // When
        companyService.updateCompany(companyId, updateRequest);

        // Then
        verify(eventPublisher).publishCompanyUpdatedEvent(any(Company.class));
    }

    @Test
    @DisplayName("Test 4.19: should_verifyCompany_when_validIdProvided")
    void should_verifyCompany_when_validIdProvided() {
        // Given
        when(companyRepository.findById(companyId)).thenReturn(Optional.of(existingCompany));
        when(companyRepository.save(any(Company.class))).thenReturn(existingCompany);

        // When
        CompanyResponse response = companyService.verifyCompany(companyId);

        // Then
        assertThat(response).isNotNull();
        verify(companyRepository).save(argThat(company -> company.isVerified()));
        verify(eventPublisher).publishCompanyVerifiedEvent(any(Company.class));
    }

    @Test
    @DisplayName("Test 4.20: should_throwCompanyNotFoundException_when_verifyNonExistentCompany")
    void should_throwCompanyNotFoundException_when_verifyNonExistentCompany() {
        // Given
        when(companyRepository.findById(companyId)).thenReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> companyService.verifyCompany(companyId))
                .isInstanceOf(CompanyNotFoundException.class)
                .hasMessageContaining(companyId.toString());

        verify(companyRepository, never()).save(any(Company.class));
        verify(eventPublisher, never()).publishCompanyVerifiedEvent(any(Company.class));
    }

    @Test
    @DisplayName("Test 4.21: should_includeLogoInResponse_when_companyHasLogo")
    void should_includeLogoInResponse_when_companyHasLogo() {
        // Given
        Company companyWithLogo = Company.builder()
                .id(companyId)
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

        when(companyRepository.findById(companyId)).thenReturn(Optional.of(companyWithLogo));

        // When
        CompanyResponse response = companyService.getCompanyById(companyId);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getLogo()).isNotNull();
        assertThat(response.getLogo().getUrl()).isEqualTo("https://cdn.example.com/logo.png");
        assertThat(response.getLogo().getS3Key()).isEqualTo("logos/file-123.png");
        assertThat(response.getLogo().getFileId()).isEqualTo("file-123");
    }

    @Test
    @DisplayName("Test 4.22: should_returnNullLogo_when_companyHasNoLogo")
    void should_returnNullLogo_when_companyHasNoLogo() {
        // Given
        Company companyWithoutLogo = Company.builder()
                .id(companyId)
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

        when(companyRepository.findById(companyId)).thenReturn(Optional.of(companyWithoutLogo));

        // When
        CompanyResponse response = companyService.getCompanyById(companyId);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getLogo()).isNull();
    }

    @Test
    @DisplayName("Test 4.23: should_throwCompanyValidationException_when_updateWithDuplicateName")
    void should_throwCompanyValidationException_when_updateWithDuplicateName() {
        // Given
        UpdateCompanyRequest updateWithNewName = UpdateCompanyRequest.builder()
                .name("Another Company Name")
                .build();

        when(companyRepository.findById(companyId)).thenReturn(Optional.of(existingCompany));
        when(companyRepository.existsByName("Another Company Name")).thenReturn(true);

        // When / Then
        assertThatThrownBy(() -> companyService.updateCompany(companyId, updateWithNewName))
                .isInstanceOf(CompanyValidationException.class)
                .hasMessageContaining("already exists");

        verify(companyRepository, never()).save(any(Company.class));
        verify(searchService, never()).invalidateCache();
    }

    @Test
    @DisplayName("Test 4.24: should_allowSameName_when_updateWithoutChangingName")
    void should_allowSameName_when_updateWithoutChangingName() {
        // Given
        UpdateCompanyRequest updateWithSameName = UpdateCompanyRequest.builder()
                .name("Test Company AG") // Same as existing
                .displayName("Updated Display")
                .build();

        when(companyRepository.findById(companyId)).thenReturn(Optional.of(existingCompany));
        when(companyRepository.save(any(Company.class))).thenReturn(existingCompany);

        // When
        CompanyResponse response = companyService.updateCompany(companyId, updateWithSameName);

        // Then
        assertThat(response).isNotNull();
        verify(companyRepository).save(any(Company.class));
        verify(searchService).invalidateCache();
        // Should NOT check existsByName when name hasn't changed
        verify(companyRepository, never()).existsByName(anyString());
    }
}
