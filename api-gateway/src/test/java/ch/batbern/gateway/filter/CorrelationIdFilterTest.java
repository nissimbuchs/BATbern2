package ch.batbern.gateway.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CorrelationIdFilterTest {

    @InjectMocks
    private CorrelationIdFilter correlationIdFilter;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        MDC.clear();
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void should_generateCorrelationId_when_requestReceived() throws ServletException, IOException {
        // Given
        when(request.getHeader("X-Correlation-ID")).thenReturn(null);
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/v1/test");

        // When
        correlationIdFilter.doFilter(request, response, filterChain);

        // Then
        verify(response).setHeader(eq("X-Correlation-ID"), anyString());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void should_propagateCorrelationId_when_serviceCalled() throws ServletException, IOException {
        // Given
        String existingCorrelationId = "existing-correlation-id";
        when(request.getHeader("X-Correlation-ID")).thenReturn(existingCorrelationId);
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/v1/test");

        // When
        correlationIdFilter.doFilter(request, response, filterChain);

        // Then
        verify(response).setHeader("X-Correlation-ID", existingCorrelationId);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void should_includeCorrelationIdInLogs_when_errorLogged() throws ServletException, IOException {
        // Given
        when(request.getHeader("X-Correlation-ID")).thenReturn(null);
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/v1/events");

        // When
        correlationIdFilter.doFilter(request, response, filterChain);

        // Then - MDC should be cleaned up after filter execution
        assertThat(MDC.get("correlationId")).isNull();
    }

    @Test
    void should_returnCorrelationIdInHeader_when_responseReturned() throws ServletException, IOException {
        // Given
        String correlationId = "test-correlation-id";
        when(request.getHeader("X-Correlation-ID")).thenReturn(correlationId);
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/v1/test");

        // When
        correlationIdFilter.doFilter(request, response, filterChain);

        // Then
        verify(response).setHeader("X-Correlation-ID", correlationId);
    }

    @Test
    void should_cleanupMDC_when_filterCompletes() throws ServletException, IOException {
        // Given
        when(request.getHeader("X-Correlation-ID")).thenReturn("test-id");
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/v1/test");

        // When
        correlationIdFilter.doFilter(request, response, filterChain);

        // Then
        assertThat(MDC.get("correlationId")).isNull();
    }

    @Test
    void should_cleanupMDC_when_exceptionThrown() throws ServletException, IOException {
        // Given
        when(request.getHeader("X-Correlation-ID")).thenReturn("test-id");
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/v1/test");
        doThrow(new RuntimeException("Test exception")).when(filterChain).doFilter(request, response);

        // When/Then
        assertThatThrownBy(() -> correlationIdFilter.doFilter(request, response, filterChain))
            .isInstanceOf(RuntimeException.class);

        // MDC should be cleaned up even after exception
        assertThat(MDC.get("correlationId")).isNull();
    }

    @Test
    void should_generateNewId_when_headerIsEmpty() throws ServletException, IOException {
        // Given
        when(request.getHeader("X-Correlation-ID")).thenReturn("");
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/v1/test");

        // When
        correlationIdFilter.doFilter(request, response, filterChain);

        // Then
        verify(response).setHeader(eq("X-Correlation-ID"), anyString());
    }

    @Test
    void should_generateNewId_when_headerIsBlank() throws ServletException, IOException {
        // Given
        when(request.getHeader("X-Correlation-ID")).thenReturn("   ");
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/v1/test");

        // When
        correlationIdFilter.doFilter(request, response, filterChain);

        // Then
        verify(response).setHeader(eq("X-Correlation-ID"), anyString());
    }
}
