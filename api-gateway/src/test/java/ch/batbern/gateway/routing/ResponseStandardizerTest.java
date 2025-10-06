package ch.batbern.gateway.routing;

import ch.batbern.gateway.routing.model.StandardResponse;
import ch.batbern.gateway.routing.model.ErrorInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class ResponseStandardizerTest {

    private ResponseStandardizer responseStandardizer;

    @BeforeEach
    void setUp() {
        responseStandardizer = new ResponseStandardizer();
    }

    @Test
    @DisplayName("should_standardizeSuccessResponse_when_validDataProvided")
    void should_standardizeSuccessResponse_when_validDataProvided() {
        // Given
        Object responseData = Map.of("eventId", "123", "title", "BATbern 2024");
        String requestId = "req-123";

        // When
        ResponseEntity<StandardResponse<Object>> standardizedResponse =
            responseStandardizer.standardizeResponse(responseData, requestId, HttpStatus.OK);

        // Then
        assertThat(standardizedResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        StandardResponse<Object> body = standardizedResponse.getBody();
        assertThat(body).isNotNull();
        assertThat(body.isSuccess()).isTrue();
        assertThat(body.getData()).isEqualTo(responseData);
        assertThat(body.getError()).isNull();
        assertThat(body.getRequestId()).isEqualTo(requestId);
        assertThat(body.getTimestamp()).isNotNull();
    }

    @Test
    @DisplayName("should_standardizeErrorResponse_when_errorOccurs")
    void should_standardizeErrorResponse_when_errorOccurs() {
        // Given
        ErrorInfo errorInfo = ErrorInfo.builder()
            .code("AUTH_FAILED")
            .message("Authentication failed")
            .field("authorization")
            .build();
        String requestId = "req-456";

        // When
        ResponseEntity<StandardResponse<Object>> standardizedResponse =
            responseStandardizer.standardizeErrorResponse(errorInfo, requestId, HttpStatus.UNAUTHORIZED);

        // Then
        assertThat(standardizedResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        StandardResponse<Object> body = standardizedResponse.getBody();
        assertThat(body).isNotNull();
        assertThat(body.isSuccess()).isFalse();
        assertThat(body.getData()).isNull();
        assertThat(body.getError()).isEqualTo(errorInfo);
        assertThat(body.getRequestId()).isEqualTo(requestId);
        assertThat(body.getTimestamp()).isNotNull();
    }

    @Test
    @DisplayName("should_addResponseMetadata_when_metadataProvided")
    void should_addResponseMetadata_when_metadataProvided() {
        // Given
        Object responseData = Map.of("status", "created");
        String requestId = "req-789";
        Map<String, Object> metadata = Map.of(
            "processingTime", "150ms",
            "serverInstance", "gateway-01"
        );

        // When
        ResponseEntity<StandardResponse<Object>> standardizedResponse =
            responseStandardizer.standardizeResponseWithMetadata(responseData, requestId, metadata, HttpStatus.CREATED);

        // Then
        StandardResponse<Object> body = standardizedResponse.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getMetadata()).containsEntry("processingTime", "150ms");
        assertThat(body.getMetadata()).containsEntry("serverInstance", "gateway-01");
    }

    @Test
    @DisplayName("should_handleNullData_when_noDataProvided")
    void should_handleNullData_when_noDataProvided() {
        // Given
        String requestId = "req-null";

        // When
        ResponseEntity<StandardResponse<Object>> standardizedResponse =
            responseStandardizer.standardizeResponse(null, requestId, HttpStatus.NO_CONTENT);

        // Then
        assertThat(standardizedResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        StandardResponse<Object> body = standardizedResponse.getBody();
        assertThat(body).isNotNull();
        assertThat(body.isSuccess()).isTrue();
        assertThat(body.getData()).isNull();
    }

    @Test
    @DisplayName("should_includeSecurityHeaders_when_responseGenerated")
    void should_includeSecurityHeaders_when_responseGenerated() {
        // Given
        Object responseData = Map.of("message", "Success");
        String requestId = "req-security";

        // When
        ResponseEntity<StandardResponse<Object>> standardizedResponse =
            responseStandardizer.standardizeResponse(responseData, requestId, HttpStatus.OK);

        // Then
        assertThat(standardizedResponse.getHeaders().get("X-Content-Type-Options"))
            .containsExactly("nosniff");
        assertThat(standardizedResponse.getHeaders().get("X-Frame-Options"))
            .containsExactly("DENY");
        assertThat(standardizedResponse.getHeaders().get("X-XSS-Protection"))
            .containsExactly("1; mode=block");
    }

    @Test
    @DisplayName("should_formatValidationErrors_when_validationFails")
    void should_formatValidationErrors_when_validationFails() {
        // Given
        Map<String, String> validationErrors = Map.of(
            "email", "Invalid email format",
            "age", "Must be between 18 and 100"
        );
        String requestId = "req-validation";

        // When
        ResponseEntity<StandardResponse<Object>> standardizedResponse =
            responseStandardizer.standardizeValidationErrorResponse(validationErrors, requestId);

        // Then
        assertThat(standardizedResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        StandardResponse<Object> body = standardizedResponse.getBody();
        assertThat(body).isNotNull();
        assertThat(body.isSuccess()).isFalse();
        assertThat(body.getError().getCode()).isEqualTo("VALIDATION_FAILED");
        assertThat(body.getError().getDetails()).containsKey("fieldErrors");
    }

    @Test
    @DisplayName("should_handleLargeResponseData_when_bigDatasetProvided")
    void should_handleLargeResponseData_when_bigDatasetProvided() {
        // Given
        Map<String, Object> largeData = Map.of(
            "events", java.util.Collections.nCopies(1000, Map.of("id", "event-123")),
            "totalCount", 1000
        );
        String requestId = "req-large";

        // When
        ResponseEntity<StandardResponse<Object>> standardizedResponse =
            responseStandardizer.standardizeResponse(largeData, requestId, HttpStatus.OK);

        // Then
        StandardResponse<Object> body = standardizedResponse.getBody();
        assertThat(body).isNotNull();
        assertThat(body.isSuccess()).isTrue();
        assertThat(body.getData()).isEqualTo(largeData);
        // Note: metadata for data size is not automatically added in basic standardizeResponse
    }
}