package ch.batbern.gateway.routing.integration;

import ch.batbern.gateway.auth.model.UserContext;
import ch.batbern.gateway.routing.DomainRouter;
import ch.batbern.gateway.routing.RequestTransformer;
import ch.batbern.gateway.routing.ResponseStandardizer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import jakarta.servlet.http.HttpServletRequest;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.*;

class RequestRoutingIntegrationTest {

    @Test
    @DisplayName("should_routeCompleteRequest_when_endToEndFlowExecuted")
    void should_routeCompleteRequest_when_endToEndFlowExecuted() {
        // Given
        DomainRouter domainRouter = new DomainRouter();
        RequestTransformer requestTransformer = new RequestTransformer();
        ResponseStandardizer responseStandardizer = new ResponseStandardizer();

        MockHttpServletRequest originalRequest = new MockHttpServletRequest();
        originalRequest.setRequestURI("/api/events/list");
        originalRequest.setMethod("GET");
        originalRequest.addHeader("Authorization", "Bearer valid-jwt-token");

        UserContext userContext = UserContext.builder()
            .userId("user-123")
            .email("organizer@batbern.ch")
            .role("organizer")
            .companyId("company-456")
            .build();

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
    }

    @Test
    @DisplayName("should_handleRoutingErrors_when_serviceUnavailable")
    void should_handleRoutingErrors_when_serviceUnavailable() {
        // Given
        DomainRouter domainRouter = new DomainRouter();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/events/create");
        String unavailableService = "unavailable-service";

        // When
        CompletableFuture<ResponseEntity<String>> routingResult =
            domainRouter.routeRequest(unavailableService, request);

        // Then
        assertThat(routingResult).isNotNull();
        // Should handle service unavailability gracefully
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