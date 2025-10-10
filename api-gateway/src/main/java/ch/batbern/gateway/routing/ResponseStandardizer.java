package ch.batbern.gateway.routing;

import ch.batbern.gateway.routing.model.ErrorInfo;
import ch.batbern.gateway.routing.model.StandardResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class ResponseStandardizer {

    public ResponseEntity<StandardResponse<Object>> standardizeResponse(Object data, String requestId,
            HttpStatus status) {
        log.debug("Standardizing response for request: {}", requestId);

        StandardResponse<Object> response = StandardResponse.success(data, requestId);
        HttpHeaders headers = createSecurityHeaders();

        return new ResponseEntity<>(response, headers, status);
    }

    public ResponseEntity<StandardResponse<Object>> standardizeResponseWithMetadata(
            Object data, String requestId, Map<String, Object> metadata, HttpStatus status) {
        log.debug("Standardizing response with metadata for request: {}", requestId);

        StandardResponse<Object> response = StandardResponse.successWithMetadata(data, requestId, metadata);
        HttpHeaders headers = createSecurityHeaders();

        return new ResponseEntity<>(response, headers, status);
    }

    public ResponseEntity<StandardResponse<Object>> standardizeErrorResponse(
            ErrorInfo errorInfo, String requestId, HttpStatus status) {
        log.debug("Standardizing error response for request: {}", requestId);

        StandardResponse<Object> response = StandardResponse.error(errorInfo, requestId);
        HttpHeaders headers = createSecurityHeaders();

        return new ResponseEntity<>(response, headers, status);
    }

    public ResponseEntity<StandardResponse<Object>> standardizeValidationErrorResponse(
            Map<String, String> validationErrors, String requestId) {
        log.debug("Standardizing validation error response for request: {}", requestId);

        Map<String, Object> errorDetails = new HashMap<>();
        errorDetails.put("fieldErrors", validationErrors);

        ErrorInfo errorInfo = ErrorInfo.withDetails(
            "VALIDATION_FAILED",
            "Request validation failed",
            errorDetails
        );

        StandardResponse<Object> response = StandardResponse.error(errorInfo, requestId);
        HttpHeaders headers = createSecurityHeaders();

        return new ResponseEntity<>(response, headers, HttpStatus.BAD_REQUEST);
    }

    private HttpHeaders createSecurityHeaders() {
        HttpHeaders headers = new HttpHeaders();

        // Security headers
        headers.add("X-Content-Type-Options", "nosniff");
        headers.add("X-Frame-Options", "DENY");
        headers.add("X-XSS-Protection", "1; mode=block");

        return headers;
    }

    // Add data size to metadata for large responses
    private Map<String, Object> addDataSizeMetadata(Object data) {
        Map<String, Object> metadata = new HashMap<>();

        if (data != null) {
            // Simple estimation of data size
            String dataString = data.toString();
            metadata.put("dataSize", dataString.length() + " characters");
        }

        return metadata;
    }
}