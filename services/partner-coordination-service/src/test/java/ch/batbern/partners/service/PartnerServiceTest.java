package ch.batbern.partners.service;

import ch.batbern.partners.client.CompanyServiceClient;
import ch.batbern.partners.client.company.dto.CompanyResponse;
import ch.batbern.partners.domain.Partner;
import ch.batbern.partners.domain.PartnershipLevel;
import ch.batbern.partners.dto.generated.CreatePartnerRequest;
import ch.batbern.partners.dto.generated.PartnerResponse;
import ch.batbern.partners.dto.generated.UpdatePartnerRequest;
import ch.batbern.partners.exception.CompanyNotFoundException;
import ch.batbern.partners.exception.PartnerAlreadyExistsException;
import ch.batbern.partners.exception.PartnerNotFoundException;
import ch.batbern.partners.repository.PartnerRepository;
import ch.batbern.partners.security.SecurityContextHelper;
import ch.batbern.shared.events.DomainEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


/**
 * Unit tests for PartnerService business logic.
 * Tests are written BEFORE implementation (TDD RED phase).
 */
@ExtendWith(MockitoExtension.class)
class PartnerServiceTest {

    @Mock
    private PartnerRepository partnerRepository;

    @Mock
    private CompanyServiceClient companyServiceClient;

    @Mock
    private DomainEventPublisher eventPublisher;

    @Mock
    private SecurityContextHelper securityContextHelper;

    @InjectMocks
    private PartnerService partnerService;

    private CreatePartnerRequest createPartnerRequest;
    private Partner partner;
    private CompanyResponse companyDTO;

    @BeforeEach
    void setUp() {
        // Mock securityContextHelper to return "test-user" for all tests (lenient to avoid unnecessary stubbing errors)
        lenient().when(securityContextHelper.getCurrentUserIdOrSystem()).thenReturn("test-user");

        createPartnerRequest = new CreatePartnerRequest();
        createPartnerRequest.setCompanyName("GoogleZH");
        createPartnerRequest.setPartnershipLevel(ch.batbern.partners.dto.generated.PartnershipLevel.GOLD);
        createPartnerRequest.setPartnershipStartDate(LocalDate.now());

        partner = new Partner();
        partner.setId(UUID.randomUUID());
        partner.setCompanyName("GoogleZH");
        partner.setPartnershipLevel(PartnershipLevel.GOLD);
        partner.setPartnershipStartDate(LocalDate.now());
        // Note: isActive() is calculated based on dates, no setActive() method

        companyDTO = new CompanyResponse();
        companyDTO.setName("GoogleZH");  // Generated DTO uses setName()
        companyDTO.setDisplayName("Google Zurich");
    }

    @Test
    void should_createPartner_when_validRequestProvided() {
        // Given
        when(partnerRepository.findByCompanyName("GoogleZH")).thenReturn(Optional.empty());
        when(companyServiceClient.getCompany("GoogleZH")).thenReturn(companyDTO);
        when(partnerRepository.save(any(Partner.class))).thenReturn(partner);

        // When
        PartnerResponse response = partnerService.createPartner(createPartnerRequest);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getCompanyName()).isEqualTo("GoogleZH");
        assertThat(response.getPartnershipLevel()).isEqualTo(ch.batbern.partners.dto.generated.PartnershipLevel.GOLD);
        verify(partnerRepository).save(any(Partner.class));
        verify(eventPublisher).publish(any());
    }

    @Test
    void should_throwPartnerAlreadyExistsException_when_partnerExistsForCompany() {
        // Given
        when(partnerRepository.findByCompanyName("GoogleZH")).thenReturn(Optional.of(partner));

        // When/Then
        assertThatThrownBy(() -> partnerService.createPartner(createPartnerRequest))
                .isInstanceOf(PartnerAlreadyExistsException.class)
                .hasMessageContaining("GoogleZH");

        verify(partnerRepository, never()).save(any());
        verify(eventPublisher, never()).publish(any());
    }

    @Test
    void should_throwCompanyNotFoundException_when_companyDoesNotExist() {
        // Given
        when(partnerRepository.findByCompanyName("GoogleZH")).thenReturn(Optional.empty());
        when(companyServiceClient.getCompany("GoogleZH"))
                .thenThrow(new CompanyNotFoundException("Company not found: GoogleZH"));

        // When/Then
        assertThatThrownBy(() -> partnerService.createPartner(createPartnerRequest))
                .isInstanceOf(CompanyNotFoundException.class)
                .hasMessageContaining("GoogleZH");

        verify(partnerRepository, never()).save(any());
    }

    @Test
    void should_getPartnerByCompanyName_when_partnerExists() {
        // Given
        when(partnerRepository.findByCompanyName("GoogleZH")).thenReturn(Optional.of(partner));

        // When
        PartnerResponse response = partnerService.getPartnerByCompanyName("GoogleZH", Collections.emptySet());

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getCompanyName()).isEqualTo("GoogleZH");
    }

    @Test
    void should_throwPartnerNotFoundException_when_partnerNotFound() {
        // Given
        when(partnerRepository.findByCompanyName("GoogleZH")).thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> partnerService.getPartnerByCompanyName("GoogleZH", Collections.emptySet()))
                .isInstanceOf(PartnerNotFoundException.class)
                .hasMessageContaining("GoogleZH");
    }

    @Test
    void should_enrichWithCompanyData_when_includeCompanyRequested() {
        // Given
        when(partnerRepository.findByCompanyName("GoogleZH")).thenReturn(Optional.of(partner));
        when(companyServiceClient.getCompany("GoogleZH")).thenReturn(companyDTO);

        // When
        PartnerResponse response = partnerService.getPartnerByCompanyName("GoogleZH", Set.of("company"));

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getCompany()).isNotNull();
        assertThat(response.getCompany().getCompanyName()).isEqualTo("GoogleZH");
        assertThat(response.getCompany().getDisplayName()).isEqualTo("Google Zurich");
        verify(companyServiceClient).getCompany("GoogleZH");
    }

    @Test
    void should_updatePartner_when_validRequestProvided() {
        // Given
        UpdatePartnerRequest updateRequest = new UpdatePartnerRequest();
        updateRequest.setPartnershipLevel(ch.batbern.partners.dto.generated.PartnershipLevel.PLATINUM);
        updateRequest.setPartnershipEndDate(LocalDate.now().plusYears(2));

        when(partnerRepository.findByCompanyName("GoogleZH")).thenReturn(Optional.of(partner));
        when(partnerRepository.save(any(Partner.class))).thenReturn(partner);

        // When
        PartnerResponse response = partnerService.updatePartner("GoogleZH", updateRequest);

        // Then
        assertThat(response).isNotNull();
        verify(partnerRepository).save(any(Partner.class));
        verify(eventPublisher).publish(any());
    }

    @Test
    void should_softDeletePartner_when_deleteRequested() {
        // Given
        when(partnerRepository.findByCompanyName("GoogleZH")).thenReturn(Optional.of(partner));

        // When
        partnerService.deletePartner("GoogleZH");

        // Then
        verify(partnerRepository).save(argThat(p -> !p.isActive()));
        verify(eventPublisher).publish(any());
    }

    @Test
    void should_listPartners_when_noFiltersProvided() {
        // Given
        List<Partner> partners = Arrays.asList(partner);
        when(partnerRepository.findAll()).thenReturn(partners);

        // When
        List<PartnerResponse> responses = partnerService.listPartners(null, null, 0, 20, null);

        // Then
        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).getCompanyName()).isEqualTo("GoogleZH");
    }

    @Test
    void should_filterByPartnershipLevel_when_filterProvided() {
        // Given
        List<Partner> partners = Arrays.asList(partner);
        when(partnerRepository.findByPartnershipLevel(PartnershipLevel.GOLD)).thenReturn(partners);

        // When (accepts both uppercase and lowercase, but uppercase is clearer)
        List<PartnerResponse> responses = partnerService.listPartners("partnershipLevel=GOLD", null, 0, 20, null);

        // Then
        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).getPartnershipLevel()).isEqualTo(ch.batbern.partners.dto.generated.PartnershipLevel.GOLD);
    }

    @Test
    void should_filterByActiveStatus_when_isActiveFilterProvided() {
        // Given
        List<Partner> partners = Arrays.asList(partner);
        when(partnerRepository.findActivePartners(any(LocalDate.class))).thenReturn(partners);

        // When
        List<PartnerResponse> responses = partnerService.listPartners("isActive=true", null, 0, 20, null);

        // Then
        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).getIsActive()).isTrue();
    }
}
