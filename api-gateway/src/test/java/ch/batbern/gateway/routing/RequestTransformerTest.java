package ch.batbern.gateway.routing;

import ch.batbern.gateway.auth.model.UserContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class RequestTransformerTest {

    private RequestTransformer requestTransformer;

    @BeforeEach
    void setUp() {
        requestTransformer = new RequestTransformer();
    }

    @Test
    @DisplayName("should_addUserContextHeaders_when_userContextProvided")
    void should_addUserContextHeaders_when_userContextProvided() {
        // Given
        MockHttpServletRequest request = new MockHttpServletRequest();
        UserContext userContext = UserContext.builder()
            .userId("user-123")
            .email("test@example.com")
            .role("organizer")
            .companyId("company-456")
            .build();

        // When
        HttpServletRequest transformedRequest = requestTransformer.addUserContext(request, userContext);

        // Then
        assertThat(transformedRequest.getHeader("X-User-Id")).isEqualTo("user-123");
        assertThat(transformedRequest.getHeader("X-User-Email")).isEqualTo("test@example.com");
        assertThat(transformedRequest.getHeader("X-User-Role")).isEqualTo("organizer");
        assertThat(transformedRequest.getHeader("X-Company-Id")).isEqualTo("company-456");
    }

    @Test
    @DisplayName("should_addRequestIdHeader_when_requestProcessed")
    void should_addRequestIdHeader_when_requestProcessed() {
        // Given
        MockHttpServletRequest request = new MockHttpServletRequest();

        // When
        HttpServletRequest transformedRequest = requestTransformer.addRequestId(request);

        // Then
        String requestId = transformedRequest.getHeader("X-Request-Id");
        assertThat(requestId).isNotNull();
        assertThat(requestId).hasSize(36); // UUID format
        assertThat(requestId).matches("[a-f0-9-]{36}");
    }

    @Test
    @DisplayName("should_preserveOriginalHeaders_when_transformationApplied")
    void should_preserveOriginalHeaders_when_transformationApplied() {
        // Given
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Content-Type", "application/json");
        request.addHeader("Authorization", "Bearer token123");

        UserContext userContext = UserContext.builder()
            .userId("user-123")
            .role("speaker")
            .build();

        // When
        HttpServletRequest transformedRequest = requestTransformer.addUserContext(request, userContext);

        // Then
        assertThat(transformedRequest.getHeader("Content-Type")).isEqualTo("application/json");
        assertThat(transformedRequest.getHeader("Authorization")).isEqualTo("Bearer token123");
        assertThat(transformedRequest.getHeader("X-User-Id")).isEqualTo("user-123");
        assertThat(transformedRequest.getHeader("X-User-Role")).isEqualTo("speaker");
    }

    @Test
    @DisplayName("should_transformPathForTargetService_when_routingToMicroservice")
    void should_transformPathForTargetService_when_routingToMicroservice() {
        // Given
        String originalPath = "/api/events/create";
        String targetService = "event-management-service";

        // When
        String transformedPath = requestTransformer.transformPath(originalPath, targetService);

        // Then
        assertThat(transformedPath).isEqualTo("/events/create");
    }

    @Test
    @DisplayName("should_addCorrelationHeaders_when_requestProcessed")
    void should_addCorrelationHeaders_when_requestProcessed() {
        // Given
        MockHttpServletRequest request = new MockHttpServletRequest();

        // When
        HttpServletRequest transformedRequest = requestTransformer.addCorrelationHeaders(request);

        // Then
        assertThat(transformedRequest.getHeader("X-Correlation-Id")).isNotNull();
        assertThat(transformedRequest.getHeader("X-Gateway-Timestamp")).isNotNull();
        assertThat(transformedRequest.getHeader("X-Request-Source")).isEqualTo("api-gateway");
    }

    @Test
    @DisplayName("should_extractRequestMetadata_when_requestProvided")
    void should_extractRequestMetadata_when_requestProvided() {
        // Given
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setRequestURI("/api/events/create");
        request.addHeader("User-Agent", "BATbern-Frontend/1.0");
        request.setRemoteAddr("192.168.1.100");

        // When
        Map<String, Object> metadata = requestTransformer.extractRequestMetadata(request);

        // Then
        assertThat(metadata).containsEntry("method", "POST");
        assertThat(metadata).containsEntry("path", "/api/events/create");
        assertThat(metadata).containsEntry("userAgent", "BATbern-Frontend/1.0");
        assertThat(metadata).containsEntry("clientIp", "192.168.1.100");
        assertThat(metadata).containsKey("timestamp");
    }

    @Test
    @DisplayName("should_handleNullUserContext_when_noUserProvided")
    void should_handleNullUserContext_when_noUserProvided() {
        // Given
        MockHttpServletRequest request = new MockHttpServletRequest();

        // When / Then
        assertThatCode(() -> requestTransformer.addUserContext(request, null))
            .doesNotThrowAnyException();
    }
}