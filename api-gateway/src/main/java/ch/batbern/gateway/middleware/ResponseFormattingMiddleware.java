package ch.batbern.gateway.middleware;

import ch.batbern.shared.api.PaginationMetadata;
import ch.batbern.shared.api.PaginationUtils;
import ch.batbern.shared.dto.PaginatedResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.util.List;

/**
 * Response formatting middleware that wraps collection responses with pagination metadata.
 *
 * Implements Spring's ResponseBodyAdvice to intercept responses before they are written
 * to the HTTP response body. When a controller returns a List and pagination parameters
 * are present, this middleware automatically wraps the response in a PaginatedResponse.
 *
 * Expected request parameters:
 * - page: Current page number (1-indexed)
 * - limit: Number of items per page
 *
 * Expected request attribute:
 * - totalCount: Total number of items across all pages (set by controller)
 *
 * Example transformation:
 * Controller returns: [{"id": "1"}, {"id": "2"}]
 * Middleware transforms to:
 * {
 *   "data": [{"id": "1"}, {"id": "2"}],
 *   "pagination": {
 *     "page": 1,
 *     "limit": 20,
 *     "total": 45,
 *     "totalPages": 3,
 *     "hasNext": true,
 *     "hasPrev": false
 *   }
 * }
 */
@RestControllerAdvice
@Slf4j
public class ResponseFormattingMiddleware implements ResponseBodyAdvice<Object> {

    private static final String TOTAL_COUNT_ATTRIBUTE = "totalCount";

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        // Support all return types - we'll check instance type in beforeBodyWrite
        return true;
    }

    @Override
    public Object beforeBodyWrite(
            Object body,
            MethodParameter returnType,
            MediaType selectedContentType,
            Class<? extends HttpMessageConverter<?>> selectedConverterType,
            ServerHttpRequest request,
            ServerHttpResponse response) {

        // Only process List responses
        if (!(body instanceof List)) {
            return body;
        }

        // Get the underlying HttpServletRequest
        HttpServletRequest servletRequest = ((ServletServerHttpRequest) request).getServletRequest();

        try {
            // Parse pagination parameters (with defaults if not present)
            int page = parsePage(servletRequest);
            int limit = parseLimit(servletRequest);

            // Get total count from request attribute (set by controller)
            Long totalCount = (Long) servletRequest.getAttribute(TOTAL_COUNT_ATTRIBUTE);
            if (totalCount == null) {
                // If no totalCount attribute, use the size of the current list as total
                // This handles cases where controllers don't set the attribute
                totalCount = (long) ((List<?>) body).size();
            }

            // Generate pagination metadata
            PaginationMetadata pagination = PaginationUtils.generateMetadata(page, limit, totalCount);

            // Wrap response
            @SuppressWarnings("unchecked")
            PaginatedResponse<Object> paginatedResponse = PaginatedResponse.builder()
                    .data((List<Object>) body)
                    .pagination(pagination)
                    .build();

            return paginatedResponse;

        } catch (NumberFormatException e) {
            log.warn("Invalid pagination parameters: {}", e.getMessage());
            return body;
        }
    }

    /**
     * Parses page parameter with default value.
     *
     * @param request the HTTP servlet request
     * @return parsed page number, or default (1)
     */
    private int parsePage(HttpServletRequest request) {
        String pageParam = request.getParameter("page");
        if (pageParam == null || pageParam.isEmpty()) {
            return 1; // Default page
        }
        return Integer.parseInt(pageParam);
    }

    /**
     * Parses limit parameter with default value.
     *
     * @param request the HTTP servlet request
     * @return parsed limit, or default (20)
     */
    private int parseLimit(HttpServletRequest request) {
        String limitParam = request.getParameter("limit");
        if (limitParam == null || limitParam.isEmpty()) {
            return 20; // Default limit
        }
        return Integer.parseInt(limitParam);
    }
}
