package ch.batbern.companyuser.controller;

import ch.batbern.companyuser.dto.CompanyResponse;
import ch.batbern.companyuser.dto.CreateCompanyRequest;
import ch.batbern.companyuser.dto.PaginatedCompanyResponse;
import ch.batbern.companyuser.dto.UpdateCompanyRequest;
import ch.batbern.companyuser.exception.CompanyNotFoundException;
import ch.batbern.companyuser.exception.CompanyValidationException;
import ch.batbern.companyuser.exception.InvalidUIDException;
import ch.batbern.companyuser.service.CompanyService;
import ch.batbern.shared.api.PaginationMetadata;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Test Suite for CompanyController REST API endpoints
 * Covers AC4: REST API implementation with OpenAPI documentation
 * Tests cover all CRUD operations, validation, and error scenarios
 */
@WebMvcTest(controllers = CompanyController.class)
@Import(ch.batbern.companyuser.config.SecurityConfig.class)
class CompanyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CompanyService companyService;

    @MockBean
    private ch.batbern.companyuser.service.CompanySearchService searchService;

    @MockBean
    private ch.batbern.companyuser.service.SwissUIDValidationService uidValidationService;

    @MockBean
    private ch.batbern.companyuser.service.CompanyQueryService queryService;

    @MockBean
    private ch.batbern.companyuser.service.CompanyLogoService logoService;

    private UUID testCompanyId;
    private CreateCompanyRequest createRequest;
    private UpdateCompanyRequest updateRequest;
    private CompanyResponse companyResponse;

    @BeforeEach
    void setUp() {
        testCompanyId = UUID.randomUUID();

        createRequest = CreateCompanyRequest.builder()
                .name("Test Company AG")
                .displayName("Test Company")
                .swissUID("CHE-123.456.789")
                .website("https://testcompany.ch")
                .industry("Technology")
                .description("A test company")
                .build();

        updateRequest = UpdateCompanyRequest.builder()
                .name("Updated Company AG")
                .displayName("Updated Company")
                .website("https://updatedcompany.ch")
                .industry("Finance")
                .description("Updated description")
                .build();

        companyResponse = CompanyResponse.builder()
                .id(testCompanyId)
                .name("Test Company AG")
                .displayName("Test Company")
                .swissUID("CHE-123.456.789")
                .website("https://testcompany.ch")
                .industry("Technology")
                .description("A test company")
                .isVerified(false)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    /**
     * Test 4.1: should_createCompany_when_validRequestReceived
     * Verifies that POST /api/v1/companies creates a company successfully
     */
    @Test
    @WithMockUser(roles = {"ORGANIZER"})
    void should_createCompany_when_validRequestReceived() throws Exception {
        when(companyService.createCompany(any(CreateCompanyRequest.class)))
                .thenReturn(companyResponse);

        mockMvc.perform(post("/api/v1/companies")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(testCompanyId.toString()))
                .andExpect(jsonPath("$.name").value("Test Company AG"))
                .andExpect(jsonPath("$.displayName").value("Test Company"))
                .andExpect(jsonPath("$.swissUID").value("CHE-123.456.789"))
                .andExpect(jsonPath("$.website").value("https://testcompany.ch"))
                .andExpect(jsonPath("$.industry").value("Technology"))
                .andExpect(jsonPath("$.isVerified").value(false));
    }

    /**
     * Test 4.2: should_getCompanyById_when_companyExists
     * Verifies that GET /api/v1/companies/{id} returns company details
     */
    @Test
    @WithMockUser
    void should_getCompanyById_when_companyExists() throws Exception {
        when(companyService.getCompanyById(testCompanyId))
                .thenReturn(companyResponse);

        mockMvc.perform(get("/api/v1/companies/{id}", testCompanyId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(testCompanyId.toString()))
                .andExpect(jsonPath("$.name").value("Test Company AG"))
                .andExpect(jsonPath("$.swissUID").value("CHE-123.456.789"));
    }

    /**
     * Test 4.3: should_updateCompany_when_validUpdateRequest
     * Verifies that PUT /api/v1/companies/{id} updates company successfully
     */
    @Test
    @WithMockUser(roles = {"ORGANIZER"})
    void should_updateCompany_when_validUpdateRequest() throws Exception {
        CompanyResponse updatedResponse = CompanyResponse.builder()
                .id(testCompanyId)
                .name("Updated Company AG")
                .displayName("Updated Company")
                .swissUID("CHE-123.456.789")
                .website("https://updatedcompany.ch")
                .industry("Finance")
                .description("Updated description")
                .isVerified(false)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(companyService.updateCompany(eq(testCompanyId), any(UpdateCompanyRequest.class)))
                .thenReturn(updatedResponse);

        mockMvc.perform(put("/api/v1/companies/{id}", testCompanyId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(testCompanyId.toString()))
                .andExpect(jsonPath("$.name").value("Updated Company AG"))
                .andExpect(jsonPath("$.industry").value("Finance"));
    }

    /**
     * Test 4.4: should_deleteCompany_when_requested
     * Verifies that DELETE /api/v1/companies/{id} deletes company
     */
    @Test
    @WithMockUser(roles = {"ORGANIZER"})
    void should_deleteCompany_when_requested() throws Exception {
        mockMvc.perform(delete("/api/v1/companies/{id}", testCompanyId)
                        .with(csrf()))
                .andExpect(status().isNoContent());
    }

    /**
     * Test 4.5: should_return404_when_companyNotFound
     * Verifies that proper 404 response is returned when company doesn't exist
     */
    @Test
    @WithMockUser
    void should_return404_when_companyNotFound() throws Exception {
        UUID nonExistentId = UUID.randomUUID();
        when(companyService.getCompanyById(nonExistentId))
                .thenThrow(new CompanyNotFoundException(nonExistentId.toString()));

        mockMvc.perform(get("/api/v1/companies/{id}", nonExistentId))
                .andExpect(status().isNotFound());
    }

    /**
     * Test 4.7: should_getAllCompanies_when_requested
     * Verifies that GET /api/v1/companies returns paginated list of companies (AC14)
     * Note: This endpoint now uses CompanyQueryService and returns PaginatedCompanyResponse
     */
    @Test
    @WithMockUser
    void should_getAllCompanies_when_requested() throws Exception {
        CompanyResponse company2 = CompanyResponse.builder()
                .id(UUID.randomUUID())
                .name("Second Company AG")
                .displayName("Second Company")
                .isVerified(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        List<CompanyResponse> companies = Arrays.asList(companyResponse, company2);
        PaginatedCompanyResponse paginatedResponse = PaginatedCompanyResponse.builder()
                .data(companies)
                .pagination(PaginationMetadata.builder()
                    .page(1)
                    .limit(20)
                    .total(2L)
                    .totalPages(1)
                    .hasNext(false)
                    .hasPrev(false)
                    .build())
                .build();

        when(queryService.queryCompanies(any(), any(), any(), any(), any(), any()))
                .thenReturn(paginatedResponse);

        mockMvc.perform(get("/api/v1/companies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].name").value("Test Company AG"))
                .andExpect(jsonPath("$.data[1].name").value("Second Company AG"))
                .andExpect(jsonPath("$.pagination.page").value(1))
                .andExpect(jsonPath("$.pagination.total").value(2));
    }

    /**
     * Test 4.8: should_returnValidationError_when_invalidRequestBody
     * Verifies that request validation is enforced
     */
    @Test
    @WithMockUser(roles = {"ORGANIZER"})
    void should_returnValidationError_when_invalidRequestBody() throws Exception {
        CreateCompanyRequest invalidRequest = CreateCompanyRequest.builder()
                .name("") // Empty name should fail validation
                .build();

        mockMvc.perform(post("/api/v1/companies")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());
    }

    /**
     * Test 4.9: should_returnBadRequest_when_invalidSwissUID
     * Verifies that invalid Swiss UID returns proper error
     */
    @Test
    @WithMockUser(roles = {"ORGANIZER"})
    void should_returnBadRequest_when_invalidSwissUID() throws Exception {
        when(companyService.createCompany(any(CreateCompanyRequest.class)))
                .thenThrow(new InvalidUIDException("INVALID-UID"));

        CreateCompanyRequest invalidUIDRequest = CreateCompanyRequest.builder()
                .name("Test Company AG")
                .swissUID("INVALID-UID")
                .build();

        mockMvc.perform(post("/api/v1/companies")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidUIDRequest)))
                .andExpect(status().isBadRequest());
    }

    /**
     * Test 4.10: should_returnConflict_when_duplicateCompanyName
     * Verifies that duplicate company names are rejected
     */
    @Test
    @WithMockUser(roles = {"ORGANIZER"})
    void should_returnConflict_when_duplicateCompanyName() throws Exception {
        when(companyService.createCompany(any(CreateCompanyRequest.class)))
                .thenThrow(new CompanyValidationException("Company with name 'Test Company AG' already exists"));

        mockMvc.perform(post("/api/v1/companies")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isConflict());
    }

    /**
     * Test 4.11: should_returnUnauthorized_when_notAuthenticated
     * Verifies that unauthenticated requests are rejected
     * Returns 401 Unauthorized for anonymous/unauthenticated users
     */
    @Test
    void should_returnUnauthorized_when_notAuthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/companies/{id}", testCompanyId))
                .andExpect(status().isUnauthorized()); // 401 for unauthenticated users
    }

    /**
     * Test 4.12: should_returnForbidden_when_insufficientPermissions
     * Verifies that users without ORGANIZER role cannot delete companies
     * NOTE: Skipping in @WebMvcTest as method security requires full integration test context
     * This will be covered in CompanyControllerIntegrationTest instead
     */
    @Test
    @org.junit.jupiter.api.Disabled("Method security requires integration test - see CompanyControllerIntegrationTest")
    @WithMockUser(roles = {"SPEAKER"})
    void should_returnForbidden_when_insufficientPermissions() throws Exception {
        mockMvc.perform(delete("/api/v1/companies/{id}", testCompanyId)
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }
}
