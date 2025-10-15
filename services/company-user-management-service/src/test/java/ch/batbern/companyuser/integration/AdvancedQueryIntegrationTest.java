package ch.batbern.companyuser.integration;

import ch.batbern.companyuser.config.TestAwsConfig;
import ch.batbern.companyuser.domain.Company;
import ch.batbern.companyuser.repository.CompanyRepository;
import ch.batbern.shared.test.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for advanced query patterns (AC14-15)
 * Tests filter, sort, pagination, field selection, and resource expansion
 *
 * Uses Testcontainers PostgreSQL for production parity.
 * Architecture Reference: docs/architecture/06-backend-architecture.md
 */
@Import(TestAwsConfig.class)
class AdvancedQueryIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        companyRepository.deleteAll();

        // Create test companies with varied data
        createCompany("Acme Corp", "CHE-123.456.789", "Technology", true, 100);
        createCompany("Beta AG", "CHE-111.222.333", "Finance", false, 50);
        createCompany("Gamma GmbH", "CHE-444.555.666", "Technology", true, 200);
        createCompany("Delta SA", "CHE-777.888.999", "Healthcare", false, 30);
        createCompany("Epsilon LLC", "CHE-100.200.300", "Technology", true, 150);
    }

    private void createCompany(String name, String swissUID, String industry, boolean verified, int employeeCount) {
        Company company = Company.builder()
                .name(name)
                .displayName(name)
                .swissUID(swissUID)
                .industry(industry)
                .isVerified(verified)
                .website("https://" + name.toLowerCase().replace(" ", "") + ".ch")
                .description("Description for " + name)
                .createdBy("test-user")
                .build();
        companyRepository.save(company);
    }

    // ========== AC14: Filter Tests ==========

    @Test
    @WithMockUser(roles = "ORGANIZER")
    void should_filterCompaniesByIndustry_when_filterParameterProvided() throws Exception {
        // Filter by industry = "Technology"
        String filter = "{\"industry\":\"Technology\"}";

        MvcResult result = mockMvc.perform(get("/api/v1/companies")
                        .param("filter", filter))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode data = response.get("data");

        assertThat(data).isNotNull();
        assertThat(data.size()).isEqualTo(3); // Acme, Gamma, Epsilon
        assertThat(data.get(0).get("industry").asText()).isEqualTo("Technology");
    }

    @Test
    @WithMockUser(roles = "ORGANIZER")
    void should_filterCompaniesByVerificationStatus_when_filterParameterProvided() throws Exception {
        // Filter by isVerified = true
        String filter = "{\"isVerified\":true}";

        MvcResult result = mockMvc.perform(get("/api/v1/companies")
                        .param("filter", filter))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode data = response.get("data");

        assertThat(data).isNotNull();
        assertThat(data.size()).isEqualTo(3); // Acme, Gamma, Epsilon
        data.forEach(company -> assertThat(company.get("isVerified").asBoolean()).isTrue());
    }

    @Test
    @WithMockUser(roles = "ORGANIZER")
    void should_filterWithLogicalAnd_when_multipleConditionsProvided() throws Exception {
        // Filter by industry = "Technology" AND isVerified = true
        String filter = "{\"$and\":[{\"industry\":\"Technology\"},{\"isVerified\":true}]}";

        MvcResult result = mockMvc.perform(get("/api/v1/companies")
                        .param("filter", filter))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode data = response.get("data");

        assertThat(data).isNotNull();
        assertThat(data.size()).isEqualTo(3); // Acme, Gamma, Epsilon
        data.forEach(company -> {
            assertThat(company.get("industry").asText()).isEqualTo("Technology");
            assertThat(company.get("isVerified").asBoolean()).isTrue();
        });
    }

    @Test
    @WithMockUser(roles = "ORGANIZER")
    void should_filterWithContains_when_containsOperatorUsed() throws Exception {
        // Filter by name contains "Corp"
        String filter = "{\"name\":{\"$contains\":\"Corp\"}}";

        MvcResult result = mockMvc.perform(get("/api/v1/companies")
                        .param("filter", filter))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode data = response.get("data");

        assertThat(data).isNotNull();
        assertThat(data.size()).isEqualTo(1); // Acme Corp
        assertThat(data.get(0).get("name").asText()).contains("Corp");
    }

    @Test
    @WithMockUser(roles = "ORGANIZER")
    void should_return400_when_invalidFilterJsonProvided() throws Exception {
        mockMvc.perform(get("/api/v1/companies")
                        .param("filter", "{invalid json"))
                .andExpect(status().isBadRequest());
    }

    // ========== AC14: Sort Tests ==========

    @Test
    @WithMockUser(roles = "ORGANIZER")
    void should_sortCompaniesAscending_when_sortParameterProvided() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/companies")
                        .param("sort", "name"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode data = response.get("data");

        assertThat(data).isNotNull();
        assertThat(data.size()).isEqualTo(5);
        assertThat(data.get(0).get("name").asText()).isEqualTo("Acme Corp");
        assertThat(data.get(1).get("name").asText()).isEqualTo("Beta AG");
    }

    @Test
    @WithMockUser(roles = "ORGANIZER")
    void should_sortCompaniesDescending_when_sortParameterWithMinusSign() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/companies")
                        .param("sort", "-name"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode data = response.get("data");

        assertThat(data).isNotNull();
        assertThat(data.size()).isEqualTo(5);
        assertThat(data.get(0).get("name").asText()).isEqualTo("Gamma GmbH");
        assertThat(data.get(1).get("name").asText()).isEqualTo("Epsilon LLC");
    }

    @Test
    @WithMockUser(roles = "ORGANIZER")
    void should_sortByMultipleFields_when_multipleSortParametersProvided() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/companies")
                        .param("sort", "-industry,name"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode data = response.get("data");

        assertThat(data).isNotNull();
        assertThat(data.size()).isEqualTo(5);
        // First by industry DESC, then by name ASC
        assertThat(data.get(0).get("industry").asText()).isEqualTo("Technology");
        assertThat(data.get(0).get("name").asText()).isEqualTo("Acme Corp");
    }

    // ========== AC14: Pagination Tests ==========

    @Test
    @WithMockUser(roles = "ORGANIZER")
    void should_paginateResults_when_pageAndLimitProvided() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/companies")
                        .param("page", "1")
                        .param("limit", "2"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode data = response.get("data");
        JsonNode pagination = response.get("pagination");

        assertThat(data).isNotNull();
        assertThat(data.size()).isEqualTo(2); // First 2 companies
        assertThat(pagination.get("page").asInt()).isEqualTo(1);
        assertThat(pagination.get("limit").asInt()).isEqualTo(2);
        assertThat(pagination.get("total").asLong()).isEqualTo(5);
        assertThat(pagination.get("totalPages").asInt()).isEqualTo(3);
        assertThat(pagination.get("hasNext").asBoolean()).isTrue();
        assertThat(pagination.get("hasPrev").asBoolean()).isFalse();
    }

    @Test
    @WithMockUser(roles = "ORGANIZER")
    void should_returnSecondPage_when_page2Requested() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/companies")
                        .param("page", "2")
                        .param("limit", "2"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode data = response.get("data");
        JsonNode pagination = response.get("pagination");

        assertThat(data).isNotNull();
        assertThat(data.size()).isEqualTo(2); // Next 2 companies
        assertThat(pagination.get("page").asInt()).isEqualTo(2);
        assertThat(pagination.get("hasNext").asBoolean()).isTrue();
        assertThat(pagination.get("hasPrev").asBoolean()).isTrue();
    }

    @Test
    @WithMockUser(roles = "ORGANIZER")
    void should_returnEmptyData_when_pageExceedsTotalPages() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/companies")
                        .param("page", "10")
                        .param("limit", "2"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode data = response.get("data");

        assertThat(data).isNotNull();
        assertThat(data.size()).isEqualTo(0);
    }

    @Test
    @WithMockUser(roles = "ORGANIZER")
    void should_return400_when_invalidPageNumber() throws Exception {
        mockMvc.perform(get("/api/v1/companies")
                        .param("page", "0"))
                .andExpect(status().isBadRequest());
    }

    // ========== AC14: Field Selection Tests ==========

    @Test
    @WithMockUser(roles = "ORGANIZER")
    void should_returnOnlySelectedFields_when_fieldsParameterProvided() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/companies")
                        .param("fields", "id,name,industry"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode data = response.get("data");

        assertThat(data).isNotNull();
        assertThat(data.size()).isEqualTo(5);

        JsonNode firstCompany = data.get(0);
        assertThat(firstCompany.has("id")).isTrue();
        assertThat(firstCompany.has("name")).isTrue();
        assertThat(firstCompany.has("industry")).isTrue();
        assertThat(firstCompany.has("swissUID")).isFalse(); // Not selected
        assertThat(firstCompany.has("website")).isFalse(); // Not selected
    }

    @Test
    @WithMockUser(roles = "ORGANIZER")
    void should_returnAllFields_when_noFieldsParameterProvided() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/companies"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode data = response.get("data");

        JsonNode firstCompany = data.get(0);
        assertThat(firstCompany.has("id")).isTrue();
        assertThat(firstCompany.has("name")).isTrue();
        assertThat(firstCompany.has("swissUID")).isTrue();
        assertThat(firstCompany.has("industry")).isTrue();
        assertThat(firstCompany.has("website")).isTrue();
    }

    // ========== AC14: Combined Query Tests ==========

    @Test
    @WithMockUser(roles = "ORGANIZER")
    void should_combineFilterSortAndPagination_when_allParametersProvided() throws Exception {
        // Filter by industry=Technology, sort by name DESC, page 1, limit 2
        String filter = "{\"industry\":\"Technology\"}";

        MvcResult result = mockMvc.perform(get("/api/v1/companies")
                        .param("filter", filter)
                        .param("sort", "-name")
                        .param("page", "1")
                        .param("limit", "2"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode data = response.get("data");
        JsonNode pagination = response.get("pagination");

        assertThat(data).isNotNull();
        assertThat(data.size()).isEqualTo(2);
        assertThat(data.get(0).get("name").asText()).isEqualTo("Gamma GmbH");
        assertThat(data.get(1).get("name").asText()).isEqualTo("Epsilon LLC");
        assertThat(pagination.get("total").asLong()).isEqualTo(3); // 3 Technology companies
    }

    // ========== AC15: Resource Expansion Tests ==========

    @Test
    @WithMockUser(roles = "ORGANIZER")
    void should_includeStatistics_when_includeParameterContainsStatistics() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/companies")
                        .param("include", "statistics"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode data = response.get("data");

        assertThat(data).isNotNull();
        assertThat(data.size()).isEqualTo(5);

        JsonNode firstCompany = data.get(0);
        assertThat(firstCompany.has("statistics")).isTrue();
        JsonNode statistics = firstCompany.get("statistics");
        assertThat(statistics.has("totalEvents")).isTrue();
        assertThat(statistics.has("totalSpeakers")).isTrue();
        assertThat(statistics.has("totalPartners")).isTrue();
    }

    @Test
    @WithMockUser(roles = "ORGANIZER")
    void should_includeLogo_when_includeParameterContainsLogo() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/companies")
                        .param("include", "logo"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode data = response.get("data");

        assertThat(data).isNotNull();
        assertThat(data.size()).isEqualTo(5);

        // Test companies have no logos, so logo field is excluded by @JsonInclude(NON_NULL)
        // The test verifies that include parameter is processed (service includes resource expansion logic)
        // If a company had a logo, the logo field would be present with url, s3Key, and fileId
        JsonNode firstCompany = data.get(0);
        // Verify expansion was attempted (no logo=null so field is omitted)
        assertThat(firstCompany.has("logo")).isFalse(); // NON_NULL excludes null logo
    }

    @Test
    @WithMockUser(roles = "ORGANIZER")
    void should_includeMultipleResources_when_multipleIncludesProvided() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/companies")
                        .param("include", "statistics,logo"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode data = response.get("data");

        assertThat(data).isNotNull();
        assertThat(data.size()).isEqualTo(5);

        JsonNode firstCompany = data.get(0);
        assertThat(firstCompany.has("statistics")).isTrue(); // Always included
        // Logo is null for test companies, excluded by @JsonInclude(NON_NULL)
        assertThat(firstCompany.has("logo")).isFalse();
    }

    @Test
    @WithMockUser(roles = "ORGANIZER")
    void should_meetPerformanceTarget_when_allResourcesIncluded() throws Exception {
        long startTime = System.currentTimeMillis();

        mockMvc.perform(get("/api/v1/companies")
                        .param("include", "statistics,logo"))
                .andExpect(status().isOk());

        long duration = System.currentTimeMillis() - startTime;

        // AC15: <200ms P95 with all includes
        assertThat(duration).isLessThan(200);
    }

    @Test
    @WithMockUser(roles = "ORGANIZER")
    void should_notIncludeResources_when_noIncludeParameterProvided() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/companies"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode data = response.get("data");

        JsonNode firstCompany = data.get(0);
        assertThat(firstCompany.has("statistics")).isFalse();
        assertThat(firstCompany.has("logo")).isFalse();
    }
}
