package ch.batbern.gateway.exception;

import ch.batbern.shared.dto.ErrorResponse;
import ch.batbern.shared.exception.NotFoundException;
import ch.batbern.shared.exception.ServiceException;
import ch.batbern.shared.exception.UnauthorizedException;
import ch.batbern.shared.exception.ValidationException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    @InjectMocks
    private GlobalExceptionHandler exceptionHandler;

    @Mock
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        when(request.getRequestURI()).thenReturn("/api/v1/test");
        MDC.put("correlationId", "test-correlation-id");
        ReflectionTestUtils.setField(exceptionHandler, "activeProfile", "dev");
    }

    @Test
    void should_returnStandardErrorResponse_when_BATbernExceptionThrown() {
        // Given
        ValidationException exception = new ValidationException("Validation failed");

        // When
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleBATbernException(exception, request);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getError()).isEqualTo("ERR_VALIDATION");
        assertThat(response.getBody().getMessage()).isEqualTo("Validation failed");
        assertThat(response.getBody().getCorrelationId()).isEqualTo("test-correlation-id");
        assertThat(response.getBody().getPath()).isEqualTo("/api/v1/test");
    }

    @Test
    void should_return400_when_ValidationExceptionThrown() {
        // Given
        ValidationException exception = new ValidationException("email", "invalid format");

        // When
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleBATbernException(exception, request);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(400);
        assertThat(response.getBody().getSeverity()).isEqualTo("MEDIUM");
    }

    @Test
    void should_return404_when_NotFoundExceptionThrown() {
        // Given
        NotFoundException exception = new NotFoundException("Event", "123");

        // When
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleBATbernException(exception, request);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(404);
        assertThat(response.getBody().getError()).isEqualTo("ERR_NOT_FOUND");
    }

    @Test
    void should_return401_when_UnauthorizedExceptionThrown() {
        // Given
        UnauthorizedException exception = new UnauthorizedException("Invalid token");

        // When
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleBATbernException(exception, request);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(401);
        assertThat(response.getBody().getSeverity()).isEqualTo("HIGH");
    }

    @Test
    void should_return500_when_ServiceExceptionThrown() {
        // Given
        ServiceException exception = new ServiceException("Database connection failed");

        // When
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleBATbernException(exception, request);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(500);
        assertThat(response.getBody().getSeverity()).isEqualTo("CRITICAL");
    }

    @Test
    void should_includeCorrelationId_when_errorResponseGenerated() {
        // Given
        MDC.put("correlationId", "custom-correlation-id");
        ValidationException exception = new ValidationException("test");

        // When
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleBATbernException(exception, request);

        // Then
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCorrelationId()).isEqualTo("custom-correlation-id");
    }

    @Test
    void should_handleValidationException_when_requestBodyInvalid() {
        // Given
        MethodArgumentNotValidException exception = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);
        FieldError fieldError = new FieldError("user", "email", "must be valid");

        when(exception.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(List.of(fieldError));

        // When
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleValidationException(exception, request);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getError()).isEqualTo("ERR_VALIDATION");
        assertThat(response.getBody().getMessage()).isEqualTo("Request validation failed");
    }

    @Test
    void should_hideStackTrace_when_productionEnvironment() {
        // Given
        ReflectionTestUtils.setField(exceptionHandler, "activeProfile", "prod");
        ServiceException exception = new ServiceException("Internal error");

        // When
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleBATbernException(exception, request);

        // Then
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStackTrace()).isNull();
    }

    @Test
    void should_includeStackTrace_when_developmentEnvironment() {
        // Given
        ReflectionTestUtils.setField(exceptionHandler, "activeProfile", "dev");
        ServiceException exception = new ServiceException("Internal error");

        // When
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleBATbernException(exception, request);

        // Then
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStackTrace()).isNotNull();
    }

    @Test
    void should_sanitizeDetails_when_sensitiveDataPresent() {
        // Given
        ValidationException exception = new ValidationException("Validation failed",
            java.util.Map.of(
                "field", "email",
                "password", "secret123",
                "token", "abc123"
            ));

        // When
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleBATbernException(exception, request);

        // Then
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getDetails()).containsKey("field");
        assertThat(response.getBody().getDetails()).doesNotContainKey("password");
        assertThat(response.getBody().getDetails()).doesNotContainKey("token");
    }

    @Test
    void should_handleGenericException_when_unexpectedErrorOccurs() {
        // Given
        Exception exception = new RuntimeException("Unexpected error");

        // When
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleGenericException(exception, request);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getError()).isEqualTo("ERR_SERVICE");
        assertThat(response.getBody().getMessage()).isEqualTo("An unexpected error occurred. Please try again later.");
        assertThat(response.getBody().getSeverity()).isEqualTo("CRITICAL");
    }
}
