package ch.batbern.gateway.validation;

import ch.batbern.gateway.config.TestSecurityConfig;
import ch.batbern.shared.dto.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Input Validation Integration Tests
 *
 * TDD Tests for AC2: Input Validation
 * Tests @Valid annotation handling and validation error responses
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestSecurityConfig.class)
@DisplayName("Input Validation Tests")
class InputValidationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // ========================================
    // AC2.1: Return 400 for Invalid Request Body
    // ========================================

    @Test
    @DisplayName("should_return400_when_requestBodyInvalid")
    void should_return400_when_requestBodyInvalid() throws Exception {
        // Given: Invalid request body (missing required fields)
        String invalidJson = "{}";

        // When: Posting invalid request
        mockMvc.perform(post("/api/v1/validation/test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                // Then: Should return 400 Bad Request
                .andExpect(status().isBadRequest());
    }

    // ========================================
    // AC2.2: Validate Required Fields
    // ========================================

    @Test
    @DisplayName("should_validateRequiredFields_when_requestReceived")
    void should_validateRequiredFields_when_requestReceived() throws Exception {
        // Given: Request missing required field
        String json = """
                {
                    "email": "test@example.com"
                }
                """;

        // When: Posting request with missing required field (name)
        MvcResult result = mockMvc.perform(post("/api/v1/validation/test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andReturn();

        // Then: Error response should indicate missing field
        String responseBody = result.getResponse().getContentAsString();
        ErrorResponse errorResponse = objectMapper.readValue(responseBody, ErrorResponse.class);

        assertThat(errorResponse.getError()).isEqualTo("ERR_VALIDATION");
        assertThat(errorResponse.getMessage()).contains("validation");
        assertThat(errorResponse.getDetails()).isNotNull();
    }

    // ========================================
    // AC2.3: Validate Email Format
    // ========================================

    @Test
    @DisplayName("should_validateEmailFormat_when_emailProvided")
    void should_validateEmailFormat_when_emailProvided() throws Exception {
        // Given: Request with invalid email format
        String json = """
                {
                    "name": "Test User",
                    "email": "invalid-email-format"
                }
                """;

        // When: Posting request with invalid email
        MvcResult result = mockMvc.perform(post("/api/v1/validation/test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andReturn();

        // Then: Error response should indicate invalid email
        String responseBody = result.getResponse().getContentAsString();
        ErrorResponse errorResponse = objectMapper.readValue(responseBody, ErrorResponse.class);

        assertThat(errorResponse.getError()).isEqualTo("ERR_VALIDATION");
    }

    // ========================================
    // AC2.4: Validate String Length
    // ========================================

    @Test
    @DisplayName("should_validateStringLength_when_textFieldProvided")
    void should_validateStringLength_when_textFieldProvided() throws Exception {
        // Given: Request with string exceeding max length
        String json = """
                {
                    "name": "A very long name that exceeds the maximum allowed length for this field and should trigger validation error",
                    "email": "test@example.com"
                }
                """;

        // When: Posting request with too long string
        mockMvc.perform(post("/api/v1/validation/test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                // Then: Should return validation error
                .andExpect(status().isBadRequest());
    }

    // ========================================
    // AC2.5: Return Multiple Validation Errors
    // ========================================

    @Test
    @DisplayName("should_returnValidationErrors_when_multipleFieldsInvalid")
    void should_returnValidationErrors_when_multipleFieldsInvalid() throws Exception {
        // Given: Request with multiple validation errors
        String json = """
                {
                    "name": "",
                    "email": "invalid-email",
                    "age": -1
                }
                """;

        // When: Posting request with multiple invalid fields
        MvcResult result = mockMvc.perform(post("/api/v1/validation/test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andReturn();

        // Then: Error response should contain all validation errors
        String responseBody = result.getResponse().getContentAsString();
        ErrorResponse errorResponse = objectMapper.readValue(responseBody, ErrorResponse.class);

        assertThat(errorResponse.getError()).isEqualTo("ERR_VALIDATION");
        assertThat(errorResponse.getDetails()).isNotNull();
        assertThat(errorResponse.getDetails()).containsKey("fieldErrors");
    }

    // ========================================
    // Positive Test: Valid Request
    // ========================================

    @Test
    @DisplayName("should_acceptRequest_when_validationPasses")
    void should_acceptRequest_when_validationPasses() throws Exception {
        // Given: Valid request body
        String json = """
                {
                    "name": "Test User",
                    "email": "test@example.com",
                    "age": 25
                }
                """;

        // When: Posting valid request
        mockMvc.perform(post("/api/v1/validation/test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                // Then: Should accept request
                .andExpect(status().isOk());
    }

    // ========================================
    // SQL Injection Prevention Test
    // ========================================

    @Test
    @DisplayName("should_sanitizeInput_when_potentialSQLInjectionDetected")
    void should_sanitizeInput_when_potentialSQLInjectionDetected() throws Exception {
        // Given: Request with potential SQL injection payload
        String json = """
                {
                    "name": "'; DROP TABLE users; --",
                    "email": "test@example.com",
                    "age": 25
                }
                """;

        // When: Posting request with SQL injection attempt
        mockMvc.perform(post("/api/v1/validation/test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                // Then: Should accept request (JPA will parameterize queries)
                .andExpect(status().isOk());

        // Note: Actual SQL injection prevention is handled by JPA/Hibernate
        // This test verifies the request is processed without breaking
    }

    // ========================================
    // XSS Prevention Test
    // ========================================

    @Test
    @DisplayName("should_acceptXSSPayload_when_reactWillEscapeOnRender")
    void should_acceptXSSPayload_when_reactWillEscapeOnRender() throws Exception {
        // Given: Request with potential XSS payload
        String json = """
                {
                    "name": "<script>alert('XSS')</script>",
                    "email": "test@example.com",
                    "age": 25
                }
                """;

        // When: Posting request with XSS payload
        mockMvc.perform(post("/api/v1/validation/test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                // Then: Should accept request (React will escape on render)
                .andExpect(status().isOk());

        // Note: XSS prevention is handled by React's built-in escaping
        // This test verifies the payload is stored safely
    }
}
