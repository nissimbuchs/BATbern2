package ch.batbern.gateway.routing.integration;

import ch.batbern.gateway.auth.model.UserContext;
import ch.batbern.gateway.routing.DomainRouter;
import ch.batbern.gateway.routing.RequestTransformer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import jakarta.servlet.http.HttpServletRequest;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RequestRoutingIntegrationTest {

    @Mock
    private RestTemplate restTemplate;

    private DomainRouter domainRouter;

    @BeforeEach
    void setUp() {
        domainRouter = new DomainRouter(restTemplate);

        // Set service URL properties
        ReflectionTestUtils.setField(domainRouter, "eventManagementUrl", "http://localhost:8081");
        ReflectionTestUtils.setField(domainRouter, "speakerCoordinationUrl", "http://localhost:8082");
        ReflectionTestUtils.setField(domainRouter, "partnerCoordinationUrl", "http://localhost:8083");
        ReflectionTestUtils.setField(domainRouter, "attendeeExperienceUrl", "http://localhost:8084");
        ReflectionTestUtils.setField(domainRouter, "companyManagementUrl", "http://localhost:8085");
    }

    @Test
    @DisplayName("should_routeCompleteRequest_when_endToEndFlowExecuted")
    void should_routeCompleteRequest_when_endToEndFlowExecuted() {
        // Given
        RequestTransformer requestTransformer = new RequestTransformer();

        MockHttpServletRequest originalRequest = new MockHttpServletRequest();
        originalRequest.setRequestURI("/api/v1/events/list");
        originalRequest.setMethod("GET");
        originalRequest.addHeader("Authorization", "Bearer valid-jwt-token");

        UserContext userContext = UserContext.builder()
            .userId("user-123")
            .email("organizer@batbern.ch")
            .role("organizer")
            .companyId("company-456")
            .build();

        // Mock RestTemplate response
        when(restTemplate.exchange(
            anyString(),
            any(),
            any(),
            eq(String.class)
        )).thenReturn(ResponseEntity.ok("{\"status\":\"success\"}"));

        // When
        String targetService = domainRouter.determineTargetService(originalRequest.getRequestURI());
        HttpServletRequest transformedRequest = requestTransformer.addUserContext(originalRequest, userContext);
        transformedRequest = requestTransformer.addRequestId(transformedRequest);

        CompletableFuture<ResponseEntity<String>> routingResult =
            domainRouter.routeRequest(targetService, transformedRequest);

        // Then
        assertThat(targetService).isEqualTo("event-management-service");
        assertThat(transformedRequest.getHeader("X-User-Id")).isEqualTo("user-123");
        assertThat(transformedRequest.getHeader("X-User-Role")).isEqualTo("organizer");
        assertThat(transformedRequest.getHeader("X-Request-Id")).isNotNull();
        assertThat(routingResult).isNotNull();
        assertThat(routingResult.join().getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("should_handleRoutingErrors_when_serviceUnavailable")
    void should_handleRoutingErrors_when_serviceUnavailable() {
        // Given
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/events/create");
        String unavailableService = "unavailable-service";

        // When / Then
        assertThatThrownBy(() -> domainRouter.routeRequest(unavailableService, request).join())
            .hasCauseInstanceOf(ch.batbern.gateway.routing.exception.RoutingException.class);
    }

    @Test
    @DisplayName("should_preserveRequestContext_when_routingThroughPipeline")
    void should_preserveRequestContext_when_routingThroughPipeline() {
        // Given
        RequestTransformer requestTransformer = new RequestTransformer();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Content-Type", "application/json");
        request.addHeader("Accept", "application/json");

        UserContext userContext = UserContext.builder()
            .userId("speaker-456")
            .role("speaker")
            .build();

        // When
        HttpServletRequest transformedRequest = requestTransformer.addUserContext(request, userContext);
        transformedRequest = requestTransformer.addCorrelationHeaders(transformedRequest);

        // Then
        assertThat(transformedRequest.getHeader("Content-Type")).isEqualTo("application/json");
        assertThat(transformedRequest.getHeader("Accept")).isEqualTo("application/json");
        assertThat(transformedRequest.getHeader("X-User-Id")).isEqualTo("speaker-456");
        assertThat(transformedRequest.getHeader("X-User-Role")).isEqualTo("speaker");
        assertThat(transformedRequest.getHeader("X-Correlation-Id")).isNotNull();
    }
}