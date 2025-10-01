package ch.batbern.gateway.routing;

import ch.batbern.gateway.routing.exception.RoutingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class DomainRouterTest {

    private DomainRouter domainRouter;

    @BeforeEach
    void setUp() {
        domainRouter = new DomainRouter();
    }

    // Test 5.1: should_routeToEventService_when_eventsEndpointCalled
    @Test
    @DisplayName("should_routeToEventService_when_eventsEndpointCalled")
    void should_routeToEventService_when_eventsEndpointCalled() {
        // Given
        String requestPath = "/api/events/create";

        // When
        String targetService = domainRouter.determineTargetService(requestPath);

        // Then
        assertThat(targetService).isEqualTo("event-management-service");
    }

    // Test 5.2: should_routeToSpeakerService_when_speakersEndpointCalled
    @Test
    @DisplayName("should_routeToSpeakerService_when_speakersEndpointCalled")
    void should_routeToSpeakerService_when_speakersEndpointCalled() {
        // Given
        String requestPath = "/api/speakers/invite";

        // When
        String targetService = domainRouter.determineTargetService(requestPath);

        // Then
        assertThat(targetService).isEqualTo("speaker-coordination-service");
    }

    // Test 5.3: should_routeToPartnerService_when_partnersEndpointCalled
    @Test
    @DisplayName("should_routeToPartnerService_when_partnersEndpointCalled")
    void should_routeToPartnerService_when_partnersEndpointCalled() {
        // Given
        String requestPath = "/api/partners/coordination";

        // When
        String targetService = domainRouter.determineTargetService(requestPath);

        // Then
        assertThat(targetService).isEqualTo("partner-coordination-service");
    }

    // Test 5.4: should_routeToAttendeeService_when_contentEndpointCalled
    @Test
    @DisplayName("should_routeToAttendeeService_when_contentEndpointCalled")
    void should_routeToAttendeeService_when_contentEndpointCalled() {
        // Given
        String requestPath = "/api/content/search";

        // When
        String targetService = domainRouter.determineTargetService(requestPath);

        // Then
        assertThat(targetService).isEqualTo("attendee-experience-service");
    }

    @Test
    @DisplayName("should_throwRoutingException_when_unknownPathProvided")
    void should_throwRoutingException_when_unknownPathProvided() {
        // Given
        String unknownPath = "/api/unknown/endpoint";

        // When / Then
        assertThatThrownBy(() -> domainRouter.determineTargetService(unknownPath))
            .isInstanceOf(RoutingException.class)
            .hasMessageContaining("No route found for path");
    }

    @Test
    @DisplayName("should_routeRequestToTargetService_when_validServiceProvided")
    void should_routeRequestToTargetService_when_validServiceProvided() {
        // Given
        String targetService = "event-management-service";
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/events/list");
        request.setMethod("GET");

        // When
        CompletableFuture<ResponseEntity<String>> response = domainRouter.routeRequest(targetService, request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response).succeedsWithin(java.time.Duration.ofSeconds(5));
    }

    @Test
    @DisplayName("should_handleNestedPaths_when_deepPathProvided")
    void should_handleNestedPaths_when_deepPathProvided() {
        // Given
        String nestedPath = "/api/events/2024/batbern/speakers";

        // When
        String targetService = domainRouter.determineTargetService(nestedPath);

        // Then
        assertThat(targetService).isEqualTo("event-management-service");
    }

    @Test
    @DisplayName("should_handleQueryParameters_when_pathWithQueryProvided")
    void should_handleQueryParameters_when_pathWithQueryProvided() {
        // Given
        String pathWithQuery = "/api/speakers/search?name=John&role=keynote";

        // When
        String targetService = domainRouter.determineTargetService(pathWithQuery);

        // Then
        assertThat(targetService).isEqualTo("speaker-coordination-service");
    }

    @Test
    @DisplayName("should_throwException_when_nullPathProvided")
    void should_throwException_when_nullPathProvided() {
        // When / Then
        assertThatThrownBy(() -> domainRouter.determineTargetService(null))
            .isInstanceOf(RoutingException.class)
            .hasMessageContaining("Request path cannot be null or empty");
    }

    @Test
    @DisplayName("should_throwException_when_emptyPathProvided")
    void should_throwException_when_emptyPathProvided() {
        // When / Then
        assertThatThrownBy(() -> domainRouter.determineTargetService(""))
            .isInstanceOf(RoutingException.class)
            .hasMessageContaining("Request path cannot be null or empty");
    }
}