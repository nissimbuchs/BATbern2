package ch.batbern.gateway.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;

import static org.mockito.Mockito.*;

/**
 * Unit tests for ApiVersionHeaderFilter.
 *
 * Verifies that the filter correctly extracts API version from request paths
 * and adds the API-Version header to responses.
 */
@ExtendWith(MockitoExtension.class)
class ApiVersionHeaderFilterTest {

    @InjectMocks
    private ApiVersionHeaderFilter filter;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        filter = new ApiVersionHeaderFilter();
    }

    @Test
    void should_includeVersionHeader_when_v1PathRequested() throws ServletException, IOException {
        // Given
        when(request.getRequestURI()).thenReturn("/api/v1/events");

        // When
        filter.doFilter(request, response, filterChain);

        // Then
        verify(response).setHeader("API-Version", "v1");
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void should_includeVersionHeader_when_v2PathRequested() throws ServletException, IOException {
        // Given
        when(request.getRequestURI()).thenReturn("/api/v2/speakers");

        // When
        filter.doFilter(request, response, filterChain);

        // Then
        verify(response).setHeader("API-Version", "v2");
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void should_notIncludeVersionHeader_when_noVersionInPath() throws ServletException, IOException {
        // Given
        when(request.getRequestURI()).thenReturn("/api/events");

        // When
        filter.doFilter(request, response, filterChain);

        // Then
        verify(response, never()).setHeader(eq("API-Version"), anyString());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void should_notIncludeVersionHeader_when_invalidVersionFormat() throws ServletException, IOException {
        // Given
        when(request.getRequestURI()).thenReturn("/api/version1/events");

        // When
        filter.doFilter(request, response, filterChain);

        // Then
        verify(response, never()).setHeader(eq("API-Version"), anyString());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void should_includeVersionHeader_when_nestedPath() throws ServletException, IOException {
        // Given
        when(request.getRequestURI()).thenReturn("/api/v1/events/123/sessions");

        // When
        filter.doFilter(request, response, filterChain);

        // Then
        verify(response).setHeader("API-Version", "v1");
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void should_continueFilterChain_when_filterProcessed() throws ServletException, IOException {
        // Given
        when(request.getRequestURI()).thenReturn("/api/v1/test");

        // When
        filter.doFilter(request, response, filterChain);

        // Then
        verify(filterChain).doFilter(request, response);
    }
}
