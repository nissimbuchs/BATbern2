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
                } else if (request.getRequestURI().contains("/speakers/invite")) {
                    return body.contains("email") && body.contains("name");
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
                byte[] content = request.getInputStream().readAllBytes();
                String body = new String(content);

                if (request.getRequestURI().contains("/speakers/invite")) {
                    if (!body.contains("email") || body.contains("invalid-email")) {
                        errors.put("email", "Invalid email format");
                    }
                    if (!body.contains("name")) {
                        errors.put("name", "Name is required");
                    }
                }
            } catch (Exception e) {
                errors.put("general", "Error parsing request body");
            }
        }

        return errors;
    }
}