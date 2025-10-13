package ch.batbern.gateway.integration;

import ch.batbern.gateway.config.TestSecurityConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * E2E Integration Tests for API Consolidation Foundation (Story 1.15a)
 *
 * Tests cover all query parameters, middleware, and standardized responses.
 *
 * Story 1.15a is COMPLETE - these tests verify the implementation.
 *
 * Acceptance Criteria Coverage:
 * - AC1: API Versioning Infrastructure
 * - AC2: Filter Parser Utility
 * - AC3: Sort Parser Utility
 * - AC4: Pagination Utilities
 * - AC5: Field Selection Parser
 * - AC6: Include/Expand Parser
 * - AC7: Request Validation Middleware
 * - AC8: Error Handling Middleware
 * - AC9: Response Formatting Middleware
 * - AC10: Pagination Helper
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestSecurityConfig.class)
@DisplayName("API Consolidation Foundation E2E Integration Tests")
class ApiConsolidationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // ========================================
    // AC1: API Versioning Tests
    // ========================================

    @Test
    @DisplayName("should_routeToV1Controller_when_v1PathRequested")
    void should_routeToV1Controller_when_v1PathRequested() throws Exception {
        // Given: A v1 API endpoint exists

        // When: Request is made to v1 endpoint
        MvcResult result = mockMvc.perform(get("/api/v1/test-resources"))
                .andExpect(status().isOk())
                .andReturn();

        // Then: Response should indicate v1 routing
        assertThat(result.getResponse().getHeader("API-Version"))
                .as("API version header must be present")
                .isEqualTo("v1");
    }

    @Test
    @DisplayName("should_return404_when_unsupportedVersionRequested")
    void should_return404_when_unsupportedVersionRequested() throws Exception {
        // When: Request is made to unsupported version
        mockMvc.perform(get("/api/v99/test-resources"))
                // Then: Should return 404
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("should_includeVersionHeader_when_responseReturned")
    void should_includeVersionHeader_when_responseReturned() throws Exception {
        // When: Making a versioned API request
        MvcResult result = mockMvc.perform(get("/api/v1/test-resources"))
                .andReturn();

        // Then: Response should include version header
        assertThat(result.getResponse().getHeader("API-Version"))
                .isNotNull();
    }

    // ========================================
    // AC2-6: Query Parameters E2E Tests
    // ========================================

    @Test
    @DisplayName("should_handleAllQueryParameters_when_getRequestMade")
    void should_handleAllQueryParameters_when_getRequestMade() throws Exception {
        // Given: All query parameters are provided
        String filterJson = "{\"status\":\"published\",\"votes\":{\"$gte\":10}}";
        String sort = "-votes,+createdAt";
        int page = 2;
        int limit = 10;
        String fields = "id,title,votes";
        String include = "author,comments";

        // When: Request is made with all query parameters
        MvcResult result = mockMvc.perform(get("/api/v1/test-resources")
                        .param("filter", filterJson)
                        .param("sort", sort)
                        .param("page", String.valueOf(page))
                        .param("limit", String.valueOf(limit))
                        .param("fields", fields)
                        .param("include", include))
                .andExpect(status().isOk())
                .andReturn();

        // Then: Response should be properly formatted with pagination
        String responseBody = result.getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(responseBody);

        assertThat(json.has("data"))
                .as("Response must have data field")
                .isTrue();

        assertThat(json.has("pagination"))
                .as("Response must have pagination metadata")
                .isTrue();

        JsonNode pagination = json.get("pagination");
        assertThat(pagination.get("page").asInt()).isEqualTo(page);
        assertThat(pagination.get("limit").asInt()).isEqualTo(limit);
        assertThat(pagination.has("total")).isTrue();
        assertThat(pagination.has("totalPages")).isTrue();
        assertThat(pagination.has("hasNext")).isTrue();
        assertThat(pagination.has("hasPrev")).isTrue();
    }

    @Test
    @DisplayName("should_filterData_when_filterParameterProvided")
    void should_filterData_when_filterParameterProvided() throws Exception {
        // Given: Filter for published items with votes >= 10
        String filterJson = "{\"status\":\"published\",\"votes\":{\"$gte\":10}}";

        // When: Request is made with filter
        MvcResult result = mockMvc.perform(get("/api/v1/test-resources")
                        .param("filter", filterJson))
                .andExpect(status().isOk())
                .andReturn();

        // Then: Only matching items should be returned
        String responseBody = result.getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(responseBody);
        JsonNode data = json.get("data");

        // All returned items should match filter criteria
        assertThat(data.isArray()).isTrue();
        for (JsonNode item : data) {
            assertThat(item.get("status").asText())
                    .as("All items must have status=published")
                    .isEqualTo("published");
            assertThat(item.get("votes").asInt())
                    .as("All items must have votes >= 10")
                    .isGreaterThanOrEqualTo(10);
        }
    }

    @Test
    @DisplayName("should_sortData_when_sortParameterProvided")
    void should_sortData_when_sortParameterProvided() throws Exception {
        // Given: Sort by votes descending, then createdAt ascending
        String sort = "-votes,+createdAt";

        // When: Request is made with sort
        MvcResult result = mockMvc.perform(get("/api/v1/test-resources")
                        .param("sort", sort))
                .andExpect(status().isOk())
                .andReturn();

        // Then: Data should be sorted correctly
        String responseBody = result.getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(responseBody);
        JsonNode data = json.get("data");

        assertThat(data.isArray()).isTrue();
        assertThat(data.size()).isGreaterThan(1);

        // Verify descending votes order
        for (int i = 0; i < data.size() - 1; i++) {
            int currentVotes = data.get(i).get("votes").asInt();
            int nextVotes = data.get(i + 1).get("votes").asInt();
            assertThat(currentVotes)
                    .as("Votes should be in descending order")
                    .isGreaterThanOrEqualTo(nextVotes);
        }
    }

    @Test
    @DisplayName("should_paginateData_when_paginationParametersProvided")
    void should_paginateData_when_paginationParametersProvided() throws Exception {
        // Given: Request for page 1 with limit 5
        int page = 1;
        int limit = 5;

        // When: Request is made with pagination
        MvcResult result = mockMvc.perform(get("/api/v1/test-resources")
                        .param("page", String.valueOf(page))
                        .param("limit", String.valueOf(limit)))
                .andExpect(status().isOk())
                .andReturn();

        // Then: Response should contain exactly 5 items and correct pagination
        String responseBody = result.getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(responseBody);

        JsonNode data = json.get("data");
        assertThat(data.size())
                .as("Page should contain exactly limit items (or less on last page)")
                .isLessThanOrEqualTo(limit);

        JsonNode pagination = json.get("pagination");
        assertThat(pagination.get("page").asInt()).isEqualTo(page);
        assertThat(pagination.get("limit").asInt()).isEqualTo(limit);
    }

    @Test
    @DisplayName("should_useDefaultPagination_when_paginationParametersOmitted")
    void should_useDefaultPagination_when_paginationParametersOmitted() throws Exception {
        // When: Request is made without pagination parameters
        MvcResult result = mockMvc.perform(get("/api/v1/test-resources"))
                .andExpect(status().isOk())
                .andReturn();

        // Then: Should use default pagination (page=1, limit=20)
        String responseBody = result.getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(responseBody);
        JsonNode pagination = json.get("pagination");

        assertThat(pagination.get("page").asInt())
                .as("Default page should be 1")
                .isEqualTo(1);
        assertThat(pagination.get("limit").asInt())
                .as("Default limit should be 20")
                .isEqualTo(20);
    }

    @Test
    @DisplayName("should_enforceMaxLimit_when_excessiveLimitRequested")
    void should_enforceMaxLimit_when_excessiveLimitRequested() throws Exception {
        // Given: Request with limit exceeding maximum (100)
        int requestedLimit = 500;

        // When: Request is made with excessive limit
        MvcResult result = mockMvc.perform(get("/api/v1/test-resources")
                        .param("limit", String.valueOf(requestedLimit)))
                .andExpect(status().isOk())
                .andReturn();

        // Then: Should enforce max limit of 100
        String responseBody = result.getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(responseBody);
        JsonNode pagination = json.get("pagination");

        assertThat(pagination.get("limit").asInt())
                .as("Limit should be capped at maximum of 100")
                .isLessThanOrEqualTo(100);
    }

    @Test
    @DisplayName("should_selectFields_when_fieldsParameterProvided")
    void should_selectFields_when_fieldsParameterProvided() throws Exception {
        // Given: Request for specific fields only
        String fields = "id,title";

        // When: Request is made with field selection
        MvcResult result = mockMvc.perform(get("/api/v1/test-resources")
                        .param("fields", fields))
                .andExpect(status().isOk())
                .andReturn();

        // Then: Response should only contain requested fields
        String responseBody = result.getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(responseBody);
        JsonNode data = json.get("data");

        assertThat(data.isArray()).isTrue();
        if (data.size() > 0) {
            JsonNode firstItem = data.get(0);
            assertThat(firstItem.has("id")).isTrue();
            assertThat(firstItem.has("title")).isTrue();
            // Should NOT contain other fields like votes, status, etc.
            assertThat(firstItem.size())
                    .as("Should only contain requested fields")
                    .isLessThanOrEqualTo(2);
        }
    }

    @Test
    @DisplayName("should_includeRelations_when_includeParameterProvided")
    void should_includeRelations_when_includeParameterProvided() throws Exception {
        // Given: Request to include related resources
        String include = "author,comments";

        // When: Request is made with include parameter
        MvcResult result = mockMvc.perform(get("/api/v1/test-resources")
                        .param("include", include))
                .andExpect(status().isOk())
                .andReturn();

        // Then: Response should include expanded relations
        String responseBody = result.getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(responseBody);
        JsonNode data = json.get("data");

        if (data.size() > 0) {
            JsonNode firstItem = data.get(0);
            assertThat(firstItem.has("author"))
                    .as("Should include author relation")
                    .isTrue();
            assertThat(firstItem.has("comments"))
                    .as("Should include comments relation")
                    .isTrue();
        }
    }

    // ========================================
    // AC7: Request Validation Tests
    // ========================================

    @Test
    @DisplayName("should_return400_when_invalidFilterSyntaxProvided")
    void should_return400_when_invalidFilterSyntaxProvided() throws Exception {
        // Given: Invalid JSON filter syntax
        String invalidFilter = "{invalid-json}";

        // When: Request is made with invalid filter
        mockMvc.perform(get("/api/v1/test-resources")
                        .param("filter", invalidFilter))
                // Then: Should return 400 Bad Request
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("should_return400_when_negativePageRequested")
    void should_return400_when_negativePageRequested() throws Exception {
        // When: Request with negative page number
        mockMvc.perform(get("/api/v1/test-resources")
                        .param("page", "-1"))
                // Then: Should return 400 Bad Request
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("should_return400_when_invalidSortFormatProvided")
    void should_return400_when_invalidSortFormatProvided() throws Exception {
        // Given: Invalid sort format
        String invalidSort = "+++invalidField";

        // When: Request is made with invalid sort
        mockMvc.perform(get("/api/v1/test-resources")
                        .param("sort", invalidSort))
                // Then: Should return 400 Bad Request
                .andExpect(status().isBadRequest());
    }

    // ========================================
    // AC8: Error Handling Tests
    // ========================================

    @Test
    @DisplayName("should_returnStandardErrorFormat_when_validationFails")
    void should_returnStandardErrorFormat_when_validationFails() throws Exception {
        // Given: Request that will fail validation
        String invalidFilter = "{invalid}";

        // When: Request is made
        MvcResult result = mockMvc.perform(get("/api/v1/test-resources")
                        .param("filter", invalidFilter))
                .andExpect(status().isBadRequest())
                .andReturn();

        // Then: Error response should follow standard format
        String responseBody = result.getResponse().getContentAsString();
        JsonNode error = objectMapper.readTree(responseBody);

        assertThat(error.has("timestamp"))
                .as("Error must include timestamp")
                .isTrue();
        assertThat(error.has("path"))
                .as("Error must include request path")
                .isTrue();
        assertThat(error.has("status"))
                .as("Error must include HTTP status")
                .isTrue();
        assertThat(error.has("error"))
                .as("Error must include error code")
                .isTrue();
        assertThat(error.has("message"))
                .as("Error must include error message")
                .isTrue();
        assertThat(error.has("correlationId"))
                .as("Error must include correlation ID for tracing")
                .isTrue();
    }

    @Test
    @DisplayName("should_includeCorrelationId_when_errorOccurs")
    void should_includeCorrelationId_when_errorOccurs() throws Exception {
        // When: Request triggers an error
        MvcResult result = mockMvc.perform(get("/api/v1/test-resources")
                        .param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andReturn();

        // Then: Response should include correlation ID
        String responseBody = result.getResponse().getContentAsString();
        JsonNode error = objectMapper.readTree(responseBody);

        assertThat(error.get("correlationId").asText())
                .as("Correlation ID must be non-empty")
                .isNotEmpty();
    }

    // ========================================
    // AC9: Response Formatting Tests
    // ========================================

    @Test
    @DisplayName("should_wrapCollection_when_listReturned")
    void should_wrapCollection_when_listReturned() throws Exception {
        // When: Request returns a collection
        MvcResult result = mockMvc.perform(get("/api/v1/test-resources"))
                .andExpect(status().isOk())
                .andReturn();

        // Then: Response should be wrapped with data + pagination
        String responseBody = result.getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(responseBody);

        assertThat(json.has("data"))
                .as("Collection response must have data field")
                .isTrue();
        assertThat(json.has("pagination"))
                .as("Collection response must have pagination metadata")
                .isTrue();
    }

    @Test
    @DisplayName("should_includePaginationMetadata_when_paginatedResponse")
    void should_includePaginationMetadata_when_paginatedResponse() throws Exception {
        // When: Request is made for paginated resource
        MvcResult result = mockMvc.perform(get("/api/v1/test-resources")
                        .param("page", "1")
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andReturn();

        // Then: Pagination metadata should be complete
        String responseBody = result.getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(responseBody);
        JsonNode pagination = json.get("pagination");

        // All required pagination fields
        assertThat(pagination.has("page")).isTrue();
        assertThat(pagination.has("limit")).isTrue();
        assertThat(pagination.has("total")).isTrue();
        assertThat(pagination.has("totalPages")).isTrue();
        assertThat(pagination.has("hasNext")).isTrue();
        assertThat(pagination.has("hasPrev")).isTrue();

        // Validate values are correct
        assertThat(pagination.get("page").asInt()).isEqualTo(1);
        assertThat(pagination.get("hasPrev").asBoolean())
                .as("First page should have hasPrev=false")
                .isFalse();
    }

    // ========================================
    // AC10: Pagination Helper Tests (via E2E)
    // ========================================

    @Test
    @DisplayName("should_calculateHasNext_when_morePagesExist")
    void should_calculateHasNext_when_morePagesExist() throws Exception {
        // Given: Request for first page with small limit
        // When: Request is made
        MvcResult result = mockMvc.perform(get("/api/v1/test-resources")
                        .param("page", "1")
                        .param("limit", "2"))
                .andExpect(status().isOk())
                .andReturn();

        // Then: If total > limit, hasNext should be true
        String responseBody = result.getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(responseBody);
        JsonNode pagination = json.get("pagination");

        long total = pagination.get("total").asLong();
        int limit = pagination.get("limit").asInt();
        boolean hasNext = pagination.get("hasNext").asBoolean();

        if (total > limit) {
            assertThat(hasNext)
                    .as("hasNext should be true when more pages exist")
                    .isTrue();
        }
    }

    @Test
    @DisplayName("should_calculateTotalPages_when_totalProvided")
    void should_calculateTotalPages_when_totalProvided() throws Exception {
        // When: Request is made
        MvcResult result = mockMvc.perform(get("/api/v1/test-resources")
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andReturn();

        // Then: totalPages should be calculated correctly
        String responseBody = result.getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(responseBody);
        JsonNode pagination = json.get("pagination");

        long total = pagination.get("total").asLong();
        int limit = pagination.get("limit").asInt();
        int totalPages = pagination.get("totalPages").asInt();

        int expectedPages = (int) Math.ceil((double) total / limit);
        assertThat(totalPages)
                .as("totalPages should equal ceil(total/limit)")
                .isEqualTo(expectedPages);
    }

    // ========================================
    // Combined Query Parameters Tests
    // ========================================

    @Test
    @DisplayName("should_handleFilterSortPagination_when_combinedParametersProvided")
    void should_handleFilterSortPagination_when_combinedParametersProvided() throws Exception {
        // Given: Filter + Sort + Pagination combined
        String filter = "{\"status\":\"published\"}";
        String sort = "-votes";
        int page = 1;
        int limit = 5;

        // When: Request is made with combined parameters
        MvcResult result = mockMvc.perform(get("/api/v1/test-resources")
                        .param("filter", filter)
                        .param("sort", sort)
                        .param("page", String.valueOf(page))
                        .param("limit", String.valueOf(limit)))
                .andExpect(status().isOk())
                .andReturn();

        // Then: All parameters should be applied correctly
        String responseBody = result.getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(responseBody);
        JsonNode data = json.get("data");

        // Verify filter applied
        for (JsonNode item : data) {
            assertThat(item.get("status").asText()).isEqualTo("published");
        }

        // Verify pagination
        JsonNode pagination = json.get("pagination");
        assertThat(pagination.get("page").asInt()).isEqualTo(page);
        assertThat(pagination.get("limit").asInt()).isEqualTo(limit);

        // Verify sort (descending votes)
        if (data.size() > 1) {
            for (int i = 0; i < data.size() - 1; i++) {
                assertThat(data.get(i).get("votes").asInt())
                        .isGreaterThanOrEqualTo(data.get(i + 1).get("votes").asInt());
            }
        }
    }
}
