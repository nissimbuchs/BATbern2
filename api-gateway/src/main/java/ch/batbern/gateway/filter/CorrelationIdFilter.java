package ch.batbern.gateway.filter;

import ch.batbern.shared.util.CorrelationIdGenerator;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class CorrelationIdFilter implements Filter {

    private static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    private static final String MDC_CORRELATION_ID = "correlationId";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        try {
            // Get or generate correlation ID
            String correlationId = httpRequest.getHeader(CORRELATION_ID_HEADER);
            if (correlationId == null || correlationId.trim().isEmpty()) {
                correlationId = CorrelationIdGenerator.generate();
            }

            // Set in MDC for logging
            MDC.put(MDC_CORRELATION_ID, correlationId);

            // Add to response header
            httpResponse.setHeader(CORRELATION_ID_HEADER, correlationId);

            log.debug("Processing request [{}]: {} {}",
                correlationId, httpRequest.getMethod(), httpRequest.getRequestURI());

            chain.doFilter(request, response);

        } finally {
            // Clean up MDC
            MDC.remove(MDC_CORRELATION_ID);
        }
    }
}
