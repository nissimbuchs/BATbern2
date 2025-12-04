package ch.batbern.partners.controller;

import ch.batbern.partners.client.CompanyServiceClient;
import ch.batbern.partners.config.TestAwsConfig;
import ch.batbern.partners.config.TestSecurityConfig;
import ch.batbern.partners.domain.Partner;
import ch.batbern.partners.domain.PartnershipLevel;
import ch.batbern.partners.client.company.dto.CompanyResponse;
import ch.batbern.partners.repository.PartnerRepository;
import ch.batbern.shared.test.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;

/**
 * Integration tests for Partner CRUD API endpoints.
 * Tests extend AbstractIntegrationTest for PostgreSQL Testcontainer.
 * Tests are written BEFORE implementation (TDD RED phase).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import({TestSecurityConfig.class, TestAwsConfig.class})
class PartnerControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PartnerRepository partnerRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CompanyServiceClient companyServiceClient;

    private CompanyResponse mockCompanyResponse;

    @BeforeEach
    void setUp() {
        partnerRepository.deleteAll();

        // Mock Company Service HTTP response
        mockCompanyResponse = new CompanyResponse();
        mockCompanyResponse.setName("GoogleZH");  // Generated DTO uses setName()
        mockCompanyResponse.setDisplayName("Google Zurich");
        // Note: Logo is CompanyLogo object in generated DTO, skip for this test

        when(companyServiceClient.getCompany(anyString())).thenReturn(mockCompanyResponse);
    }

    @Test
    void should_returnPartnerList_when_listEndpointCalled() throws Exception {
        // Given
        createTestPartner("GoogleZH", PartnershipLevel.GOLD);
        createTestPartner("MicrosoftZH", PartnershipLevel.PLATINUM);

        // When/Then
        mockMvc.perform(get("/api/v1/partners")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].companyName").exists())
                .andExpect(jsonPath("$.data[0].partnershipLevel").exists())
                .andExpect(jsonPath("$.metadata").exists());
    }

    @Test
    void should_filterByPartnershipLevel_when_levelParameterProvided() throws Exception {
        // Given
        createTestPartner("GoogleZH", PartnershipLevel.GOLD);
        createTestPartner("MicrosoftZH", PartnershipLevel.PLATINUM);

        // When/Then
        mockMvc.perform(get("/api/v1/partners")
                        .param("filter", "partnershipLevel=gold")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].partnershipLevel").value("GOLD"));
    }

    @Test
    void should_filterByActiveStatus_when_isActiveParameterProvided() throws Exception {
        // Given
        Partner activePartner = createTestPartner("GoogleZH", PartnershipLevel.GOLD);

        // Create inactive partner with end date in the past
        Partner inactivePartner = new Partner();
        inactivePartner.setCompanyName("MicrosoftZH");
        inactivePartner.setPartnershipLevel(PartnershipLevel.PLATINUM);
        inactivePartner.setPartnershipStartDate(LocalDate.now().minusYears(2));
        inactivePartner.setPartnershipEndDate(LocalDate.now().minusYears(1)); // Ended 1 year ago
        partnerRepository.save(inactivePartner);

        // When/Then
        mockMvc.perform(get("/api/v1/partners")
                        .param("filter", "isActive=true")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].companyName").value("GoogleZH"))
                .andExpect(jsonPath("$.data[0].isActive").value(true));
    }

    @Test
    void should_returnPartnerByCompanyName_when_validCompanyNameProvided() throws Exception {
        // Given
        createTestPartner("GoogleZH", PartnershipLevel.GOLD);

        // When/Then
        mockMvc.perform(get("/api/v1/partners/GoogleZH")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companyName").value("GoogleZH"))
                .andExpect(jsonPath("$.partnershipLevel").value("GOLD"))
                .andExpect(jsonPath("$.isActive").value(true));
    }

    @Test
    void should_return404_when_companyNameNotFound() throws Exception {
        // When/Then - Use valid company name length (≤12 chars per VARCHAR(12) schema)
        mockMvc.perform(get("/api/v1/partners/NotFoundCo")
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void should_enrichWithCompanyData_when_includeCompanyRequested() throws Exception {
        // Given
        createTestPartner("GoogleZH", PartnershipLevel.GOLD);

        // When/Then
        mockMvc.perform(get("/api/v1/partners/GoogleZH")
                        .param("include", "company")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companyName").value("GoogleZH"))
                .andExpect(jsonPath("$.company").exists())
                .andExpect(jsonPath("$.company.companyName").value("GoogleZH"))
                .andExpect(jsonPath("$.company.displayName").value("Google Zurich"));
    }

    @Test
    void should_createPartner_when_validRequestProvided() throws Exception {
        // Given
        Map<String, Object> request = new HashMap<>();
        request.put("companyName", "GoogleZH");
        request.put("partnershipLevel", "GOLD");
        request.put("partnershipStartDate", LocalDate.now().toString());

        // When/Then - ADR-003: companyName is the meaningful ID, no UUID in response
        mockMvc.perform(post("/api/v1/partners")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.companyName").value("GoogleZH"))
                .andExpect(jsonPath("$.partnershipLevel").value("GOLD"))
                .andExpect(jsonPath("$.isActive").value(true));
    }

    @Test
    void should_return400_when_partnerAlreadyExistsForCompany() throws Exception {
        // Given
        createTestPartner("GoogleZH", PartnershipLevel.GOLD);

        Map<String, Object> request = new HashMap<>();
        request.put("companyName", "GoogleZH");
        request.put("partnershipLevel", "PLATINUM");
        request.put("partnershipStartDate", LocalDate.now().toString());

        // When/Then
        mockMvc.perform(post("/api/v1/partners")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("GoogleZH")))
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void should_updatePartnershipLevel_when_patchRequestValid() throws Exception {
        // Given
        createTestPartner("GoogleZH", PartnershipLevel.GOLD);

        Map<String, Object> updateRequest = new HashMap<>();
        updateRequest.put("partnershipLevel", "PLATINUM");

        // When/Then
        mockMvc.perform(patch("/api/v1/partners/GoogleZH")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companyName").value("GoogleZH"))
                .andExpect(jsonPath("$.partnershipLevel").value("PLATINUM"));
    }

    @Test
    void should_updatePartnershipEndDate_when_patchRequestValid() throws Exception {
        // Given
        createTestPartner("GoogleZH", PartnershipLevel.GOLD);

        Map<String, Object> updateRequest = new HashMap<>();
        LocalDate endDate = LocalDate.now().plusYears(2);
        updateRequest.put("partnershipEndDate", endDate.toString());

        // When/Then
        mockMvc.perform(patch("/api/v1/partners/GoogleZH")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.partnershipEndDate").value(endDate.toString()));
    }

    @Test
    void should_return404_when_updatingNonExistentPartner() throws Exception {
        // Given
        Map<String, Object> updateRequest = new HashMap<>();
        updateRequest.put("partnershipLevel", "PLATINUM");

        // When/Then - Use valid company name length (≤12 chars per VARCHAR(12) schema)
        mockMvc.perform(patch("/api/v1/partners/NotFoundCo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void should_softDeletePartner_when_deleteEndpointCalled() throws Exception {
        // Given
        createTestPartner("GoogleZH", PartnershipLevel.GOLD);

        // When/Then
        mockMvc.perform(delete("/api/v1/partners/GoogleZH")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNoContent());

        // Verify soft delete
        mockMvc.perform(get("/api/v1/partners/GoogleZH")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isActive").value(false));
    }

    @Test
    void should_return404_when_deletingNonExistentPartner() throws Exception {
        // When/Then - Use valid company name length (≤12 chars per VARCHAR(12) schema)
        mockMvc.perform(delete("/api/v1/partners/NotFoundCo")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").exists());
    }

    // Helper methods
    private Partner createTestPartner(String companyName, PartnershipLevel level) {
        Partner partner = new Partner();
        partner.setCompanyName(companyName);
        partner.setPartnershipLevel(level);
        partner.setPartnershipStartDate(LocalDate.now());
        // Note: isActive() is calculated based on dates, no setActive() method
        return partnerRepository.save(partner);
    }
}
