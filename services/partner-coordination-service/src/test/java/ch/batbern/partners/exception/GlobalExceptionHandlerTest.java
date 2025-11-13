package ch.batbern.partners.exception;

import ch.batbern.shared.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for GlobalExceptionHandler using shared-kernel ErrorResponse.
 * Tests are written BEFORE implementation (TDD RED phase).
 */
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler exceptionHandler;
    private HttpServletRequest servletRequest;

    @BeforeEach
    void setUp() {
        exceptionHandler = new GlobalExceptionHandler();
        MockHttpServletRequest mockRequest = new MockHttpServletRequest();
        mockRequest.setRequestURI("/api/v1/partners/GoogleZH");
        servletRequest = mockRequest;
    }

    @Test
    void should_returnErrorResponse_when_partnerNotFoundExceptionThrown() {
        // Given
        PartnerNotFoundException exception = new PartnerNotFoundException("Partner not found: GoogleZH");

        // When
        ResponseEntity<ErrorResponse> response = exceptionHandler.handlePartnerNotFoundException(exception, servletRequest);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).contains("GoogleZH");
        assertThat(response.getBody().getCorrelationId()).isNotNull();
        assertThat(response.getBody().getPath()).isEqualTo("/api/v1/partners/GoogleZH");
    }

    @Test
    void should_returnErrorResponse_when_partnerAlreadyExistsExceptionThrown() {
        // Given
        PartnerAlreadyExistsException exception = new PartnerAlreadyExistsException("Partner already exists for company: GoogleZH");

        // When
        ResponseEntity<ErrorResponse> response = exceptionHandler.handlePartnerAlreadyExistsException(exception, servletRequest);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).contains("GoogleZH");
        assertThat(response.getBody().getCorrelationId()).isNotNull();
    }

    @Test
    void should_returnErrorResponse_when_companyNotFoundExceptionThrown() {
        // Given
        CompanyNotFoundException exception = new CompanyNotFoundException("Company not found: GoogleZH");

        // When
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleCompanyNotFoundException(exception, servletRequest);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).contains("GoogleZH");
    }

    @Test
    void should_includeCorrelationIdInError_when_exceptionOccurs() {
        // Given
        PartnerNotFoundException exception = new PartnerNotFoundException("Partner not found");

        // When
        ResponseEntity<ErrorResponse> response = exceptionHandler.handlePartnerNotFoundException(exception, servletRequest);

        // Then
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCorrelationId()).isNotNull();
        assertThat(response.getBody().getCorrelationId()).isNotEmpty();
    }

    @Test
    void should_includePath_when_exceptionOccurs() {
        // Given
        PartnerAlreadyExistsException exception = new PartnerAlreadyExistsException("Already exists");

        // When
        ResponseEntity<ErrorResponse> response = exceptionHandler.handlePartnerAlreadyExistsException(exception, servletRequest);

        // Then
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getPath()).isEqualTo("/api/v1/partners/GoogleZH");
    }

    @Test
    void should_handleGenericException_when_unexpectedErrorOccurs() {
        // Given
        RuntimeException exception = new RuntimeException("Unexpected error");

        // When
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleGenericException(exception, servletRequest);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).contains("unexpected error");
        assertThat(response.getBody().getCorrelationId()).isNotNull();
    }
}
