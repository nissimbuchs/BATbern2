package ch.batbern.companyuser.integration;

import ch.batbern.companyuser.domain.Company;
import ch.batbern.companyuser.config.TestAwsConfig;
import ch.batbern.companyuser.dto.CreateCompanyRequest;
import ch.batbern.companyuser.dto.UpdateCompanyRequest;
import ch.batbern.companyuser.repository.CompanyRepository;
import ch.batbern.shared.test.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Comprehensive integration tests for CompanyController REST API
 * Tests all CRUD operations, authentication, authorization, and validation
 * AC4: REST API implementation with full coverage
 *
 * Uses Testcontainers PostgreSQL for production parity.
 * Architecture Reference: docs/architecture/06-backend-architecture.md
 */
@Transactional
@Import(TestAwsConfig.class)
@DisplayName("Company REST API Integration Tests")
class CompanyControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private Company testCompany;

    @BeforeEach
    void setUp() {
        companyRepository.deleteAll();

        testCompany = Company.builder()
                .name("Test Company")
                .displayName("Test Co")
                .swissUID("CHE-123.456.789")
                .website("https://test.example.com")
                .industry("Technology")
                .description("A test company")
                .isVerified(false)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .createdBy("test-user")
                .build();
        testCompany = companyRepository.save(testCompany);
    }

    // CREATE COMPANY TESTS (AC4)

    @Test
    @DisplayName("POST /companies - should create company when authenticated with valid role")
    @WithMockUser(roles = {"ORGANIZER"})
    void shouldCreateCompany_whenAuthenticatedWithValidRole() throws Exception {
        // Given
        CreateCompanyRequest request = CreateCompanyRequest.builder()
                .name("New Company")
                .displayName("New Co")
                .swissUID("CHE-987.654.321")
                .website("https://new.example.com")
                .industry("Finance")
                .description("A new company")
                .build();

        // When & Then
        mockMvc.perform(post("/api/v1/companies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").value("New Company"))
                .andExpect(jsonPath("$.displayName").value("New Co"))
                .andExpect(jsonPath("$.swissUID").value("CHE-987.654.321"))
                .andExpect(jsonPath("$.website").value("https://new.example.com"))
                .andExpect(jsonPath("$.industry").value("Finance"))
                .andExpect(jsonPath("$.description").value("A new company"))
                .andExpect(jsonPath("$.isVerified").value(false))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.updatedAt").exists());

        // Verify company was persisted
        assertThat(companyRepository.findByName("New Company")).isPresent();
    }

    @Test
    @DisplayName("POST /companies - should return 401 when not authenticated")
    void shouldReturn401_whenNotAuthenticated() throws Exception {
        // Given
        CreateCompanyRequest request = CreateCompanyRequest.builder()
                .name("New Company")
                .build();

        // When & Then
        mockMvc.perform(post("/api/v1/companies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /companies - should return 403 when authenticated with insufficient role")
    @WithMockUser(roles = {"ATTENDEE"})
    void shouldReturn403_whenInsufficientRole() throws Exception {
        // Given
        CreateCompanyRequest request = CreateCompanyRequest.builder()
                .name("New Company")
                .build();

        // When & Then
        mockMvc.perform(post("/api/v1/companies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /companies - should return 400 when required field is missing")
    @WithMockUser(roles = {"ORGANIZER"})
    void shouldReturn400_whenRequiredFieldMissing() throws Exception {
        // Given - Missing required 'name' field
        CreateCompanyRequest request = CreateCompanyRequest.builder()
                .displayName("New Co")
                .build();

        // When & Then
        mockMvc.perform(post("/api/v1/companies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /companies - should return 400 when Swiss UID format is invalid")
    @WithMockUser(roles = {"ORGANIZER"})
    void shouldReturn400_whenSwissUIDFormatInvalid() throws Exception {
        // Given
        CreateCompanyRequest request = CreateCompanyRequest.builder()
                .name("New Company")
                .swissUID("INVALID-UID")
                .build();

        // When & Then
        mockMvc.perform(post("/api/v1/companies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /companies - should return 409 when company name already exists")
    @WithMockUser(roles = {"ORGANIZER"})
    void shouldReturn409_whenCompanyNameAlreadyExists() throws Exception {
        // Given - Use existing company name
        CreateCompanyRequest request = CreateCompanyRequest.builder()
                .name(testCompany.getName())
                .build();

        // When & Then
        mockMvc.perform(post("/api/v1/companies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    // GET COMPANY BY ID TESTS (AC4)

    @Test
    @DisplayName("GET /companies/{id} - should return company when authenticated")
    @WithMockUser
    void shouldReturnCompany_whenAuthenticated() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/v1/companies/{id}", testCompany.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(testCompany.getId().toString()))
                .andExpect(jsonPath("$.name").value(testCompany.getName()))
                .andExpect(jsonPath("$.displayName").value(testCompany.getDisplayName()))
                .andExpect(jsonPath("$.swissUID").value(testCompany.getSwissUID()))
                .andExpect(jsonPath("$.website").value(testCompany.getWebsite()))
                .andExpect(jsonPath("$.industry").value(testCompany.getIndustry()))
                .andExpect(jsonPath("$.description").value(testCompany.getDescription()))
                .andExpect(jsonPath("$.isVerified").value(testCompany.isVerified()));
    }

    @Test
    @DisplayName("GET /companies/{id} - should return 401 when not authenticated")
    void shouldReturn401_whenNotAuthenticatedForGet() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/v1/companies/{id}", testCompany.getId()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /companies/{id} - should return 404 when company not found")
    @WithMockUser
    void shouldReturn404_whenCompanyNotFound() throws Exception {
        // Given
        UUID nonExistentId = UUID.randomUUID();

        // When & Then
        mockMvc.perform(get("/api/v1/companies/{id}", nonExistentId))
                .andExpect(status().isNotFound());
    }

    // GET ALL COMPANIES TESTS (AC4)

    @Test
    @DisplayName("GET /companies - should return all companies when authenticated")
    @WithMockUser
    void shouldReturnAllCompanies_whenAuthenticated() throws Exception {
        // Given - Create additional company
        Company company2 = Company.builder()
                .name("Another Company")
                .displayName("Another Co")
                .isVerified(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .createdBy("test-user")
                .build();
        companyRepository.save(company2);

        // When & Then
        mockMvc.perform(get("/api/v1/companies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[*].name", containsInAnyOrder("Test Company", "Another Company")))
                .andExpect(jsonPath("$.pagination.total").value(2));
    }

    @Test
    @DisplayName("GET /companies - should return 401 when not authenticated")
    void shouldReturn401_whenNotAuthenticatedForGetAll() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/v1/companies"))
                .andExpect(status().isUnauthorized());
    }

    // SEARCH COMPANIES TESTS (AC5)

    @Test
    @DisplayName("GET /companies/search - should return search results when authenticated")
    @WithMockUser
    void shouldReturnSearchResults_whenAuthenticated() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/v1/companies/search")
                        .param("query", "test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("Test Company"));
    }

    @Test
    @DisplayName("GET /companies/search - should return 401 when not authenticated")
    void shouldReturn401_whenNotAuthenticatedForSearch() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/v1/companies/search")
                        .param("query", "test"))
                .andExpect(status().isUnauthorized());
    }

    // UPDATE COMPANY TESTS (AC4)

    @Test
    @DisplayName("PUT /companies/{id} - should update company when authenticated with ORGANIZER role")
    @WithMockUser(roles = {"ORGANIZER"})
    void shouldUpdateCompany_whenAuthenticatedWithOrganizerRole() throws Exception {
        // Given
        UpdateCompanyRequest request = UpdateCompanyRequest.builder()
                .displayName("Updated Display Name")
                .website("https://updated.example.com")
                .industry("Updated Industry")
                .description("Updated description")
                .build();

        // When & Then
        mockMvc.perform(put("/api/v1/companies/{id}", testCompany.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(testCompany.getId().toString()))
                .andExpect(jsonPath("$.name").value(testCompany.getName())) // Name unchanged
                .andExpect(jsonPath("$.displayName").value("Updated Display Name"))
                .andExpect(jsonPath("$.website").value("https://updated.example.com"))
                .andExpect(jsonPath("$.industry").value("Updated Industry"))
                .andExpect(jsonPath("$.description").value("Updated description"));

        // Verify company was updated in database
        Company updated = companyRepository.findById(testCompany.getId()).orElseThrow();
        assertThat(updated.getDisplayName()).isEqualTo("Updated Display Name");
    }

    @Test
    @DisplayName("PUT /companies/{id} - should return 401 when not authenticated")
    void shouldReturn401_whenNotAuthenticatedForUpdate() throws Exception {
        // Given
        UpdateCompanyRequest request = UpdateCompanyRequest.builder()
                .displayName("Updated")
                .build();

        // When & Then
        mockMvc.perform(put("/api/v1/companies/{id}", testCompany.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("PUT /companies/{id} - should return 403 when authenticated without ORGANIZER role")
    @WithMockUser(roles = {"SPEAKER"})
    void shouldReturn403_whenNotOrganizerForUpdate() throws Exception {
        // Given
        UpdateCompanyRequest request = UpdateCompanyRequest.builder()
                .displayName("Updated")
                .build();

        // When & Then
        mockMvc.perform(put("/api/v1/companies/{id}", testCompany.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PUT /companies/{id} - should return 404 when company not found")
    @WithMockUser(roles = {"ORGANIZER"})
    void shouldReturn404_whenCompanyNotFoundForUpdate() throws Exception {
        // Given
        UUID nonExistentId = UUID.randomUUID();
        UpdateCompanyRequest request = UpdateCompanyRequest.builder()
                .displayName("Updated")
                .build();

        // When & Then
        mockMvc.perform(put("/api/v1/companies/{id}", nonExistentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    // DELETE COMPANY TESTS (AC4)

    @Test
    @DisplayName("DELETE /companies/{id} - should delete company when authenticated with ORGANIZER role")
    @WithMockUser(roles = {"ORGANIZER"})
    void shouldDeleteCompany_whenAuthenticatedWithOrganizerRole() throws Exception {
        // When & Then
        mockMvc.perform(delete("/api/v1/companies/{id}", testCompany.getId()))
                .andExpect(status().isNoContent());

        // Verify company was deleted
        assertThat(companyRepository.findById(testCompany.getId())).isEmpty();
    }

    @Test
    @DisplayName("DELETE /companies/{id} - should return 401 when not authenticated")
    void shouldReturn401_whenNotAuthenticatedForDelete() throws Exception {
        // When & Then
        mockMvc.perform(delete("/api/v1/companies/{id}", testCompany.getId()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("DELETE /companies/{id} - should return 403 when authenticated without ORGANIZER role")
    @WithMockUser(roles = {"SPEAKER"})
    void shouldReturn403_whenNotOrganizerForDelete() throws Exception {
        // When & Then
        mockMvc.perform(delete("/api/v1/companies/{id}", testCompany.getId()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("DELETE /companies/{id} - should return 404 when company not found")
    @WithMockUser(roles = {"ORGANIZER"})
    void shouldReturn404_whenCompanyNotFoundForDelete() throws Exception {
        // Given
        UUID nonExistentId = UUID.randomUUID();

        // When & Then
        mockMvc.perform(delete("/api/v1/companies/{id}", nonExistentId))
                .andExpect(status().isNotFound());
    }

    // VALIDATION TESTS

    @Test
    @DisplayName("POST /companies - should return 400 when name is too short")
    @WithMockUser(roles = {"ORGANIZER"})
    void shouldReturn400_whenNameTooShort() throws Exception {
        // Given
        CreateCompanyRequest request = CreateCompanyRequest.builder()
                .name("A") // Too short (min 2 characters)
                .build();

        // When & Then
        mockMvc.perform(post("/api/v1/companies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /companies - should return 400 when name is too long")
    @WithMockUser(roles = {"ORGANIZER"})
    void shouldReturn400_whenNameTooLong() throws Exception {
        // Given
        String longName = "A".repeat(256); // Too long (max 255 characters)
        CreateCompanyRequest request = CreateCompanyRequest.builder()
                .name(longName)
                .build();

        // When & Then
        mockMvc.perform(post("/api/v1/companies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // ROLE-BASED ACCESS TESTS

    @Test
    @DisplayName("POST /companies - should allow SPEAKER role to create company")
    @WithMockUser(roles = {"SPEAKER"})
    void shouldAllowSpeakerToCreateCompany() throws Exception {
        // Given
        CreateCompanyRequest request = CreateCompanyRequest.builder()
                .name("Speaker Company")
                .build();

        // When & Then
        mockMvc.perform(post("/api/v1/companies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("POST /companies - should allow PARTNER role to create company")
    @WithMockUser(roles = {"PARTNER"})
    void shouldAllowPartnerToCreateCompany() throws Exception {
        // Given
        CreateCompanyRequest request = CreateCompanyRequest.builder()
                .name("Partner Company")
                .build();

        // When & Then
        mockMvc.perform(post("/api/v1/companies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }
}
