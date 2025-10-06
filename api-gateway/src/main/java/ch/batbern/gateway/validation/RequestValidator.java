package ch.batbern.gateway.validation;

import ch.batbern.gateway.validation.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class RequestValidator {

    private final OpenApiSchemaValidator schemaValidator;

    private static final int MAX_REQUEST_SIZE = 5 * 1024 * 1024; // 5MB
    private static final Pattern PATH_PARAM_PATTERN = Pattern.compile("/api/(\\w+)/(\\d+)(?:/(\\w+)/(\\d+))?");

    public boolean validateRequest(HttpServletRequest request) throws Exception {
        log.debug("Validating request: {} {}", request.getMethod(), request.getRequestURI());

        return schemaValidator.validateRequest(request, extractSchemaPath(request));
    }

    public void enforceValidation(HttpServletRequest request) throws Exception {
        if (!validateRequest(request)) {
            throw new ValidationException("Request validation failed for: " + request.getRequestURI());
        }
    }

    public Map<String, String> getValidationErrors(HttpServletRequest request) throws Exception {
        return schemaValidator.getValidationErrors(request);
    }

    public Map<String, String> extractPathParameters(String path) {
        Map<String, String> params = new HashMap<>();
        Matcher matcher = PATH_PARAM_PATTERN.matcher(path);

        if (matcher.matches()) {
            String resource1 = matcher.group(1);
            String id1 = matcher.group(2);
            String resource2 = matcher.group(3);
            String id2 = matcher.group(4);

            if ("events".equals(resource1)) {
                params.put("eventId", id1);
            } else if ("speakers".equals(resource1)) {
                params.put("speakerId", id1);
            } else if ("partners".equals(resource1)) {
                params.put("partnerId", id1);
            }

            if (resource2 != null && id2 != null) {
                if ("speakers".equals(resource2)) {
                    params.put("speakerId", id2);
                } else if ("events".equals(resource2)) {
                    params.put("eventId", id2);
                }
            }
        }

        return params;
    }

    public boolean validateQueryParameters(HttpServletRequest request) {
        String page = request.getParameter("page");
        String size = request.getParameter("size");
        String sort = request.getParameter("sort");

        // Validate page parameter
        if (page != null) {
            try {
                int pageNum = Integer.parseInt(page);
                if (pageNum < 0) {
                    log.warn("Invalid page parameter: {}", page);
                    return false;
                }
            } catch (NumberFormatException e) {
                log.warn("Invalid page parameter format: {}", page);
                return false;
            }
        }

        // Validate size parameter
        if (size != null) {
            try {
                int sizeNum = Integer.parseInt(size);
                if (sizeNum < 1 || sizeNum > 100) {
                    log.warn("Invalid size parameter: {}", size);
                    return false;
                }
            } catch (NumberFormatException e) {
                log.warn("Invalid size parameter format: {}", size);
                return false;
            }
        }

        // Validate sort parameter
        if (sort != null) {
            String[] validSortFields = {"name", "date", "title", "created", "updated"};
            boolean validSort = false;
            for (String validField : validSortFields) {
                if (sort.equals(validField) || sort.equals("-" + validField)) {
                    validSort = true;
                    break;
                }
            }
            if (!validSort) {
                log.warn("Invalid sort parameter: {}", sort);
                return false;
            }
        }

        return true;
    }

    public boolean validateContentType(HttpServletRequest request) {
        String method = request.getMethod();
        String contentType = request.getContentType();

        // POST and PUT requests should have application/json content type
        if (("POST".equals(method) || "PUT".equals(method)) && contentType != null) {
            return contentType.startsWith("application/json");
        }

        return true;
    }

    public boolean validateRequestSize(HttpServletRequest request) {
        try {
            if (request.getInputStream() != null) {
                int contentLength = request.getContentLength();
                if (contentLength > MAX_REQUEST_SIZE) {
                    log.warn("Request size too large: {} bytes", contentLength);
                    return false;
                }
            }
        } catch (IOException e) {
            log.error("Error checking request size", e);
            return false;
        }

        return true;
    }

    public boolean validateRequiredHeaders(HttpServletRequest request) {
        String method = request.getMethod();
        String uri = request.getRequestURI();

        // Authentication required for most endpoints
        if (!uri.startsWith("/api/content") || !"GET".equals(method)) {
            String authorization = request.getHeader("Authorization");
            if (authorization == null || !authorization.startsWith("Bearer ")) {
                log.warn("Missing or invalid Authorization header for: {} {}", method, uri);
                return false;
            }
        }

        // Content-Type required for POST/PUT
        if (("POST".equals(method) || "PUT".equals(method))) {
            String contentType = request.getHeader("Content-Type");
            if (contentType == null) {
                log.warn("Missing Content-Type header for: {} {}", method, uri);
                return false;
            }
        }

        return true;
    }

    private String extractSchemaPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String method = request.getMethod();

        // Convert path to OpenAPI schema reference
        if (uri.startsWith("/api/events")) {
            return "/paths/api/events/" + method.toLowerCase();
        } else if (uri.startsWith("/api/speakers")) {
            return "/paths/api/speakers/" + method.toLowerCase();
        } else if (uri.startsWith("/api/partners")) {
            return "/paths/api/partners/" + method.toLowerCase();
        } else if (uri.startsWith("/api/content")) {
            return "/paths/api/content/" + method.toLowerCase();
        }

        return "/paths/default/" + method.toLowerCase();
    }
}