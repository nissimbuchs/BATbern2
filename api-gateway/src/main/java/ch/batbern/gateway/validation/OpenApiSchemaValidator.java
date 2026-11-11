package ch.batbern.gateway.validation;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class OpenApiSchemaValidator {

    public boolean validateRequest(HttpServletRequest request, String schemaPath) throws Exception {
        log.debug("Validating request against schema: {}", schemaPath);

        // For now, implement basic validation
        // In production, this would validate against actual OpenAPI schema
        String contentType = request.getContentType();
        String method = request.getMethod();

        // Basic validation rules
        if (("POST".equals(method) || "PUT".equals(method)) && contentType != null) {
            if (!contentType.startsWith("application/json")) {
                return false;
            }
        }

        // Mock validation based on content
        if (request.getContentLength() > 0) {
            try {
                // In real implementation, would parse and validate JSON against schema
                byte[] content = request.getInputStream().readAllBytes();
                String body = new String(content);

                // Simple validation - check for required fields based on endpoint
                if (request.getRequestURI().contains("/events/create")) {
                    return body.contains("title") && body.contains("date");
                }
            } catch (Exception e) {
                log.error("Error validating request content", e);
                return false;
            }
        }

        return true;
    }

    public Map<String, String> getValidationErrors(HttpServletRequest request) throws Exception {
        Map<String, String> errors = new HashMap<>();

        // Mock validation errors for testing
        if (request.getContentLength() > 0) {
            try {
                // The body must be readable; the field rules of the former /speakers/invite are gone
                // with that endpoint (Story 4.10).
                request.getInputStream().readAllBytes();
            } catch (Exception e) {
                errors.put("general", "Error parsing request body");
            }
        }

        return errors;
    }
}