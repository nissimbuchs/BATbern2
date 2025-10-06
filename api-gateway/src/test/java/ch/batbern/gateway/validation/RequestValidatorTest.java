package ch.batbern.gateway.validation;

import ch.batbern.gateway.validation.exception.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RequestValidatorTest {

    private RequestValidator requestValidator;

    @Mock
    private OpenApiSchemaValidator schemaValidator;

    @BeforeEach
    void setUp() {
        requestValidator = new RequestValidator(schemaValidator);
    }

    @Test
    @DisplayName("should_validateRequestSchema_when_validRequestProvided")
    void should_validateRequestSchema_when_validRequestProvided() throws Exception {
        // Given
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setRequestURI("/api/events/create");
        request.setContentType("application/json");
        request.setContent("{\"title\":\"BATbern 2024\",\"date\":\"2024-06-15\"}".getBytes());

        when(schemaValidator.validateRequest(any(), any())).thenReturn(true);

        // When
        boolean isValid = requestValidator.validateRequest(request);

        // Then
        assertThat(isValid).isTrue();
        verify(schemaValidator).validateRequest(eq(request), any());
    }

    @Test
    @DisplayName("should_rejectInvalidSchema_when_invalidRequestProvided")
    void should_rejectInvalidSchema_when_invalidRequestProvided() throws Exception {
        // Given
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setRequestURI("/api/events/create");
        request.setContentType("application/json");
        request.setContent("{\"invalid\":\"data\"}".getBytes());

        when(schemaValidator.validateRequest(any(), any())).thenReturn(false);

        // When
        boolean isValid = requestValidator.validateRequest(request);

        // Then
        assertThat(isValid).isFalse();
    }

    @Test
    @DisplayName("should_extractValidationErrors_when_validationFails")
    void should_extractValidationErrors_when_validationFails() throws Exception {
        // Given
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setRequestURI("/api/speakers/invite");
        request.setContent("{\"email\":\"invalid-email\"}".getBytes());

        Map<String, String> expectedErrors = Map.of(
            "email", "Invalid email format",
            "name", "Name is required"
        );

        when(schemaValidator.getValidationErrors(any())).thenReturn(expectedErrors);

        // When
        Map<String, String> validationErrors = requestValidator.getValidationErrors(request);

        // Then
        assertThat(validationErrors).containsEntry("email", "Invalid email format");
        assertThat(validationErrors).containsEntry("name", "Name is required");
    }

    @Test
    @DisplayName("should_validatePathParameters_when_pathContainsParameters")
    void should_validatePathParameters_when_pathContainsParameters() {
        // Given
        String path = "/api/events/123/speakers/456";
        Map<String, String> expectedParams = Map.of(
            "eventId", "123",
            "speakerId", "456"
        );

        // When
        Map<String, String> pathParams = requestValidator.extractPathParameters(path);

        // Then
        assertThat(pathParams).containsEntry("eventId", "123");
        assertThat(pathParams).containsEntry("speakerId", "456");
    }

    @Test
    @DisplayName("should_validateQueryParameters_when_queryParametersProvided")
    void should_validateQueryParameters_when_queryParametersProvided() {
        // Given
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("page", "1");
        request.setParameter("size", "10");
        request.setParameter("sort", "name");

        // When
        boolean isValid = requestValidator.validateQueryParameters(request);

        // Then
        assertThat(isValid).isTrue();
    }

    @Test
    @DisplayName("should_rejectInvalidQueryParameters_when_invalidValuesProvided")
    void should_rejectInvalidQueryParameters_when_invalidValuesProvided() {
        // Given
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("page", "-1");  // Invalid: negative page
        request.setParameter("size", "1000"); // Invalid: too large
        request.setParameter("sort", "invalid_field"); // Invalid: unknown field

        // When
        boolean isValid = requestValidator.validateQueryParameters(request);

        // Then
        assertThat(isValid).isFalse();
    }

    @Test
    @DisplayName("should_validateContentType_when_jsonContentTypeRequired")
    void should_validateContentType_when_jsonContentTypeRequired() {
        // Given
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setContentType("application/json");

        // When
        boolean isValid = requestValidator.validateContentType(request);

        // Then
        assertThat(isValid).isTrue();
    }

    @Test
    @DisplayName("should_rejectInvalidContentType_when_wrongContentTypeProvided")
    void should_rejectInvalidContentType_when_wrongContentTypeProvided() {
        // Given
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setContentType("text/plain");

        // When
        boolean isValid = requestValidator.validateContentType(request);

        // Then
        assertThat(isValid).isFalse();
    }

    @Test
    @DisplayName("should_enforceRequestValidation_when_strictModeEnabled")
    void should_enforceRequestValidation_when_strictModeEnabled() throws Exception {
        // Given
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setRequestURI("/api/events/create");
        request.setContent("{\"invalid\":\"data\"}".getBytes());

        when(schemaValidator.validateRequest(any(), any())).thenReturn(false);

        // When / Then
        assertThatThrownBy(() -> requestValidator.enforceValidation(request))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("Request validation failed");
    }

    @Test
    @DisplayName("should_validateRequestSize_when_largePayloadProvided")
    void should_validateRequestSize_when_largePayloadProvided() {
        // Given
        MockHttpServletRequest request = new MockHttpServletRequest();
        byte[] largeContent = new byte[10 * 1024 * 1024]; // 10MB
        request.setContent(largeContent);

        // When
        boolean isValid = requestValidator.validateRequestSize(request);

        // Then
        assertThat(isValid).isFalse(); // Should reject large payloads
    }

    @Test
    @DisplayName("should_validateRequiredHeaders_when_authenticationHeaderMissing")
    void should_validateRequiredHeaders_when_authenticationHeaderMissing() {
        // Given
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setRequestURI("/api/events/create");
        // Missing Authorization header

        // When
        boolean isValid = requestValidator.validateRequiredHeaders(request);

        // Then
        assertThat(isValid).isFalse();
    }

    @Test
    @DisplayName("should_passValidation_when_allRequiredHeadersPresent")
    void should_passValidation_when_allRequiredHeadersPresent() {
        // Given
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setRequestURI("/api/events/create");
        request.addHeader("Authorization", "Bearer valid-token");
        request.addHeader("Content-Type", "application/json");

        // When
        boolean isValid = requestValidator.validateRequiredHeaders(request);

        // Then
        assertThat(isValid).isTrue();
    }
}