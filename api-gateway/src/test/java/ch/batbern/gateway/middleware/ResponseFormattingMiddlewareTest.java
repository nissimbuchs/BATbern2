package ch.batbern.gateway.middleware;

import ch.batbern.shared.api.PaginationMetadata;
import ch.batbern.shared.dto.PaginatedResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for ResponseFormattingMiddleware.
 *
 * Verifies that collection responses are properly wrapped with pagination metadata.
 */
@ExtendWith(MockitoExtension.class)
class ResponseFormattingMiddlewareTest {

    @InjectMocks
    private ResponseFormattingMiddleware middleware;

    @Mock
    private MethodParameter returnType;

    @Mock
    private ServerHttpResponse response;

    @Mock
    private HttpMessageConverter<Object> selectedConverter;

    private MockHttpServletRequest httpRequest;
    private ServerHttpRequest serverRequest;

    @BeforeEach
    void setUp() {
        httpRequest = new MockHttpServletRequest();
        serverRequest = new ServletServerHttpRequest(httpRequest);
    }

    @Test
    void should_wrapCollection_when_listReturned() {
        // Given
        List<Map<String, String>> data = List.of(
            Map.of("id", "1", "title", "Item 1"),
            Map.of("id", "2", "title", "Item 2"),
            Map.of("id", "3", "title", "Item 3")
        );

        httpRequest.setParameter("page", "1");
        httpRequest.setParameter("limit", "20");
        httpRequest.setAttribute("totalCount", 3L);

        // When
        Object result = middleware.beforeBodyWrite(
            data,
            returnType,
            MediaType.APPLICATION_JSON,
            (Class) selectedConverter.getClass(),
            serverRequest,
            response
        );

        // Then
        assertThat(result).isInstanceOf(PaginatedResponse.class);
        PaginatedResponse<?> paginatedResponse = (PaginatedResponse<?>) result;
        assertThat(paginatedResponse.getData()).hasSize(3);
        assertThat(paginatedResponse.getPagination()).isNotNull();
    }

    @Test
    void should_includePaginationMetadata_when_paginatedResponse() {
        // Given
        List<Map<String, String>> data = List.of(
            Map.of("id", "1", "title", "Item 1"),
            Map.of("id", "2", "title", "Item 2")
        );

        httpRequest.setParameter("page", "2");
        httpRequest.setParameter("limit", "2");
        httpRequest.setAttribute("totalCount", 10L);

        // When
        Object result = middleware.beforeBodyWrite(
            data,
            returnType,
            MediaType.APPLICATION_JSON,
            (Class) selectedConverter.getClass(),
            serverRequest,
            response
        );

        // Then
        assertThat(result).isInstanceOf(PaginatedResponse.class);
        PaginatedResponse<?> paginatedResponse = (PaginatedResponse<?>) result;

        PaginationMetadata pagination = paginatedResponse.getPagination();
        assertThat(pagination.getPage()).isEqualTo(2);
        assertThat(pagination.getLimit()).isEqualTo(2);
        assertThat(pagination.getTotal()).isEqualTo(10L);
        assertThat(pagination.getTotalPages()).isEqualTo(5);
        assertThat(pagination.isHasNext()).isTrue();
        assertThat(pagination.isHasPrev()).isTrue();
    }

    @Test
    void should_notWrap_when_nonListResponse() {
        // Given
        Map<String, String> singleObject = Map.of("id", "1", "title", "Single Item");

        // When
        Object result = middleware.beforeBodyWrite(
            singleObject,
            returnType,
            MediaType.APPLICATION_JSON,
            (Class) selectedConverter.getClass(),
            serverRequest,
            response
        );

        // Then
        assertThat(result).isEqualTo(singleObject);
        assertThat(result).isNotInstanceOf(PaginatedResponse.class);
    }

    @Test
    void should_notWrap_when_noPaginationParams() {
        // Given
        List<Map<String, String>> data = List.of(
            Map.of("id", "1", "title", "Item 1")
        );

        // No pagination parameters set

        // When
        Object result = middleware.beforeBodyWrite(
            data,
            returnType,
            MediaType.APPLICATION_JSON,
            (Class) selectedConverter.getClass(),
            serverRequest,
            response
        );

        // Then
        // Should return original list when no pagination params
        assertThat(result).isEqualTo(data);
    }

    @Test
    void should_useDefaultPagination_when_paramsOmitted() {
        // Given
        List<Map<String, String>> data = List.of(
            Map.of("id", "1", "title", "Item 1")
        );

        // Only totalCount is set, no page/limit params
        httpRequest.setAttribute("totalCount", 1L);
        httpRequest.setParameter("page", ""); // Empty but present
        httpRequest.setParameter("limit", ""); // Empty but present

        // When
        Object result = middleware.beforeBodyWrite(
            data,
            returnType,
            MediaType.APPLICATION_JSON,
            (Class) selectedConverter.getClass(),
            serverRequest,
            response
        );

        // Then
        // Should handle empty params gracefully
        assertThat(result).isEqualTo(data);
    }

    @Test
    void should_calculateCorrectPagination_when_lastPage() {
        // Given
        List<Map<String, String>> data = List.of(
            Map.of("id", "9", "title", "Item 9"),
            Map.of("id", "10", "title", "Item 10")
        );

        httpRequest.setParameter("page", "5");
        httpRequest.setParameter("limit", "2");
        httpRequest.setAttribute("totalCount", 10L);

        // When
        Object result = middleware.beforeBodyWrite(
            data,
            returnType,
            MediaType.APPLICATION_JSON,
            (Class) selectedConverter.getClass(),
            serverRequest,
            response
        );

        // Then
        assertThat(result).isInstanceOf(PaginatedResponse.class);
        PaginatedResponse<?> paginatedResponse = (PaginatedResponse<?>) result;

        PaginationMetadata pagination = paginatedResponse.getPagination();
        assertThat(pagination.getPage()).isEqualTo(5);
        assertThat(pagination.getTotalPages()).isEqualTo(5);
        assertThat(pagination.isHasNext()).isFalse();
        assertThat(pagination.isHasPrev()).isTrue();
    }

    @Test
    void should_calculateCorrectPagination_when_firstPage() {
        // Given
        List<Map<String, String>> data = List.of(
            Map.of("id", "1", "title", "Item 1"),
            Map.of("id", "2", "title", "Item 2")
        );

        httpRequest.setParameter("page", "1");
        httpRequest.setParameter("limit", "2");
        httpRequest.setAttribute("totalCount", 10L);

        // When
        Object result = middleware.beforeBodyWrite(
            data,
            returnType,
            MediaType.APPLICATION_JSON,
            (Class) selectedConverter.getClass(),
            serverRequest,
            response
        );

        // Then
        assertThat(result).isInstanceOf(PaginatedResponse.class);
        PaginatedResponse<?> paginatedResponse = (PaginatedResponse<?>) result;

        PaginationMetadata pagination = paginatedResponse.getPagination();
        assertThat(pagination.getPage()).isEqualTo(1);
        assertThat(pagination.isHasNext()).isTrue();
        assertThat(pagination.isHasPrev()).isFalse();
    }
}
