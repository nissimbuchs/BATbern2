package ch.batbern.companyuser.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

/**
 * Request correlation filter for distributed tracing.
 *
 * Extracts or generates a correlation ID for each request and propagates it through:
 * - MDC (Mapped Diagnostic Context) for structured logging
 * - Response headers for downstream services
 *
 * This enables end-to-end tracing across all microservices in the BATbern platform.
 */
@Component
@Order(1)
@Slf4j
public class RequestCorrelationFilter implements Filter {

    private static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    private static final String MDC_CORRELATION_ID = "correlationId";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        // Extract correlation ID from request header or generate a new one
        String correlationId = httpRequest.getHeader(CORRELATION_ID_HEADER);
        if (correlationId == null || correlationId.trim().isEmpty()) {
            correlationId = UUID.randomUUID().toString();
            log.debug("Generated new correlation ID: {}", correlationId);
        } else {
            log.debug("Using existing correlation ID: {}", correlationId);
        }

        // Add correlation ID to MDC for logging
        MDC.put(MDC_CORRELATION_ID, correlationId);

        // Add correlation ID to response headers for downstream services
        httpResponse.setHeader(CORRELATION_ID_HEADER, correlationId);

        try {
            chain.doFilter(request, response);
        } finally {
            // Clear MDC to prevent memory leaks in thread pools
            MDC.clear();
        }
    }
}
