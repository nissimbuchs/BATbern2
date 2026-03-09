package ch.batbern.gateway.routing;

import ch.batbern.gateway.routing.exception.RoutingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DomainRouterTest {

    @Mock
    private RestTemplate restTemplate;

    private DomainRouter domainRouter;

    @BeforeEach
    void setUp() {
        domainRouter = new DomainRouter(restTemplate);

        // Set service URL properties using ReflectionTestUtils
        ReflectionTestUtils.setField(domainRouter, "eventManagementUrl", "http://localhost:8081");
        ReflectionTestUtils.setField(domainRouter, "speakerCoordinationUrl", "http://localhost:8082");
        ReflectionTestUtils.setField(domainRouter, "partnerCoordinationUrl", "http://localhost:8083");
        ReflectionTestUtils.setField(domainRouter, "attendeeExperienceUrl", "http://localhost:8084");
        ReflectionTestUtils.setField(domainRouter, "companyUserManagementUrl", "http://localhost:8085");
    }

    // Test 5.1: should_routeToEventService_when_eventsEndpointCalled
    @Test
    @DisplayName("should_routeToEventService_when_eventsEndpointCalled")
    void should_routeToEventService_when_eventsEndpointCalled() {
        // Given
        String requestPath = "/api/v1/events/create";

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
        String requestPath = "/api/v1/speakers/invite";

        // When
        String targetService = domainRouter.determineTargetService(requestPath);

        // Then
        assertThat(targetService).isEqualTo("event-management-service");
    }

    // Test 5.3: should_routeToPartnerService_when_partnersEndpointCalled
    @Test
    @DisplayName("should_routeToPartnerService_when_partnersEndpointCalled")
    void should_routeToPartnerService_when_partnersEndpointCalled() {
        // Given
        String requestPath = "/api/v1/partners/coordination";

        // When
        String targetService = domainRouter.determineTargetService(requestPath);

        // Then
        assertThat(targetService).isEqualTo("partner-coordination-service");
    }

    // Test 5.3b: Story 8.3 — partner-meetings must route to partner-coordination-service
    @Test
    @DisplayName("should_routeToPartnerService_when_partnerMeetingsEndpointCalled")
    void should_routeToPartnerService_when_partnerMeetingsEndpointCalled() {
        // Given
        String requestPath = "/api/v1/partner-meetings";

        // When
        String targetService = domainRouter.determineTargetService(requestPath);

        // Then
        assertThat(targetService).isEqualTo("partner-coordination-service");
    }

    @Test
    @DisplayName("should_routeToPartnerService_when_partnerMeetingsWithIdCalled")
    void should_routeToPartnerService_when_partnerMeetingsWithIdCalled() {
        // Given
        String requestPath = "/api/v1/partner-meetings/550e8400-e29b-41d4-a716-446655440000";

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
        String requestPath = "/api/v1/content/search";

        // When
        String targetService = domainRouter.determineTargetService(requestPath);

        // Then
        assertThat(targetService).isEqualTo("attendee-experience-service");
    }

    // Test 5.5: should_routeToCompanyService_when_companiesEndpointCalled
    @Test
    @DisplayName("should_routeToCompanyService_when_companiesEndpointCalled")
    void should_routeToCompanyService_when_companiesEndpointCalled() {
        // Given
        String requestPath = "/api/v1/companies/search";

        // When
        String targetService = domainRouter.determineTargetService(requestPath);

        // Then
        assertThat(targetService).isEqualTo("company-user-management-service");
    }

    // Test 5.6: should_routeToCompanyService_when_publicOrganizersEndpointCalled
    @Test
    @DisplayName("should_routeToCompanyService_when_publicOrganizersEndpointCalled")
    void should_routeToCompanyService_when_publicOrganizersEndpointCalled() {
        // Given
        String requestPath = "/api/v1/public/organizers";

        // When
        String targetService = domainRouter.determineTargetService(requestPath);

        // Then
        assertThat(targetService).isEqualTo("company-user-management-service");
    }

    // Test Story 10.5: analytics must route to event-management-service
    @Test
    @DisplayName("should_routeToEventService_when_analyticsEndpointCalled")
    void should_routeToEventService_when_analyticsEndpointCalled() {
        // Given
        String requestPath = "/api/v1/analytics/overview";

        // When
        String targetService = domainRouter.determineTargetService(requestPath);

        // Then
        assertThat(targetService).isEqualTo("event-management-service");
    }

    @Test
    @DisplayName("should_routeToEventService_when_analyticsCompaniesDistributionCalled")
    void should_routeToEventService_when_analyticsCompaniesDistributionCalled() {
        // Given
        String requestPath = "/api/v1/analytics/companies/distribution";

        // When
        String targetService = domainRouter.determineTargetService(requestPath);

        // Then
        assertThat(targetService).isEqualTo("event-management-service");
    }

    // Test Story 10.20: admin export/import must route to event-management-service
    @Test
    @DisplayName("should_routeToEventService_when_adminExportLegacyCalled")
    void should_routeToEventService_when_adminExportLegacyCalled() {
        // Given
        String requestPath = "/api/v1/admin/export/legacy";

        // When
        String targetService = domainRouter.determineTargetService(requestPath);

        // Then
        assertThat(targetService).isEqualTo("event-management-service");
    }

    @Test
    @DisplayName("should_routeToEventService_when_adminImportLegacyCalled")
    void should_routeToEventService_when_adminImportLegacyCalled() {
        // Given
        String requestPath = "/api/v1/admin/import/legacy";

        // When
        String targetService = domainRouter.determineTargetService(requestPath);

        // Then
        assertThat(targetService).isEqualTo("event-management-service");
    }

    @Test
    @DisplayName("should_throwRoutingException_when_unknownPathProvided")
    void should_throwRoutingException_when_unknownPathProvided() {
        // Given
        String unknownPath = "/api/v1/unknown/endpoint";

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
        request.setRequestURI("/api/v1/events/list");
        request.setMethod("GET");

        // Mock RestTemplate response - now accepts URI instead of String
        when(restTemplate.exchange(
            any(URI.class),
            any(),
            any(),
            eq(byte[].class)
        )).thenReturn(ResponseEntity.ok("{\"status\":\"success\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8)));

        // When
        CompletableFuture<ResponseEntity<byte[]>> response = domainRouter.routeRequest(targetService, request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response).succeedsWithin(java.time.Duration.ofSeconds(5));
        assertThat(response.join().getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("should_handleNestedPaths_when_deepPathProvided")
    void should_handleNestedPaths_when_deepPathProvided() {
        // Given
        String nestedPath = "/api/v1/events/2024/batbern/speakers";

        // When
        String targetService = domainRouter.determineTargetService(nestedPath);

        // Then
        assertThat(targetService).isEqualTo("event-management-service");
    }

    // Story 5.4: Speaker Status Management Routing Tests
    // Epic 5: Organizer manages speakers on their behalf, routes to event-management-service
    @Test
    @DisplayName("should_routeToEventService_when_speakerStatusUpdateEndpointCalled")
    void should_routeToEventService_when_speakerStatusUpdateEndpointCalled() {
        // Given - PUT /api/v1/events/{code}/speakers/{speakerId}/status
        String requestPath = "/api/v1/events/BAT123/speakers/550e8400-e29b-41d4-a716-446655440000/status";

        // When
        String targetService = domainRouter.determineTargetService(requestPath);

        // Then
        assertThat(targetService).isEqualTo("event-management-service");
    }

    @Test
    @DisplayName("should_routeToEventService_when_speakerStatusHistoryEndpointCalled")
    void should_routeToEventService_when_speakerStatusHistoryEndpointCalled() {
        // Given - GET /api/v1/events/{code}/speakers/{speakerId}/status/history
        String requestPath = "/api/v1/events/BAT123/speakers/550e8400-e29b-41d4-a716-446655440000/status/history";

        // When
        String targetService = domainRouter.determineTargetService(requestPath);

        // Then
        assertThat(targetService).isEqualTo("event-management-service");
    }

    @Test
    @DisplayName("should_routeToEventService_when_statusSummaryEndpointCalled")
    void should_routeToEventService_when_statusSummaryEndpointCalled() {
        // Given - GET /api/v1/events/{code}/speakers/status-summary
        String requestPath = "/api/v1/events/BAT123/speakers/status-summary";

        // When
        String targetService = domainRouter.determineTargetService(requestPath);

        // Then
        assertThat(targetService).isEqualTo("event-management-service");
    }

    @Test
    @DisplayName("should_handleQueryParameters_when_pathWithQueryProvided")
    void should_handleQueryParameters_when_pathWithQueryProvided() {
        // Given
        String pathWithQuery = "/api/v1/speakers/search?name=John&role=keynote";

        // When
        String targetService = domainRouter.determineTargetService(pathWithQuery);

        // Then
        assertThat(targetService).isEqualTo("event-management-service");
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

    @Test
    @DisplayName("should_handleJsonInQueryParameters_when_filterParameterProvided")
    void should_handleJsonInQueryParameters_when_filterParameterProvided() {
        // Given - simulate the actual frontend request:
        // http://localhost:8000/api/v1/events?page=1&limit=20&filter=%7B%22year%22%3A2025%7D
        String targetService = "event-management-service";
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/events");
        request.setMethod("GET");

        // Servlet container decodes URL parameters, so getParameterMap() returns decoded values
        request.setParameter("page", "1");
        request.setParameter("limit", "20");
        request.setParameter("filter", "{\"year\":2025}");  // Decoded by servlet from %7B%22year%22%3A2025%7D

        // Mock RestTemplate to capture the actual URI being called
        ArgumentCaptor<URI> uriCaptor = ArgumentCaptor.forClass(URI.class);
        when(restTemplate.exchange(
            uriCaptor.capture(),
            any(),
            any(),
            eq(byte[].class)
        )).thenReturn(ResponseEntity.ok("{\"data\":[]}".getBytes(java.nio.charset.StandardCharsets.UTF_8)));

        // When
        CompletableFuture<ResponseEntity<byte[]>> response =
            domainRouter.routeRequest(targetService, request);

        // Then
        assertThat(response.join().getStatusCode()).isEqualTo(HttpStatus.OK);

        // Verify the URI is properly encoded and RestTemplate doesn't treat {} as template variables
        URI capturedUri = uriCaptor.getValue();
        String queryString = capturedUri.getRawQuery();

        // Should be URL-encoded: {"year":2025} -> %7B%22year%22:2025%7D
        // Note: colon doesn't need encoding in query params, so : not %3A
        assertThat(queryString).contains("filter=%7B%22year%22:2025%7D");
        assertThat(queryString).contains("page=1");
        assertThat(queryString).contains("limit=20");

        // Should NOT be double-encoded: %7B -> %257B
        assertThat(queryString).doesNotContain("%257B");

        // Verify full target URL is correct
        assertThat(capturedUri.toString()).startsWith("http://localhost:8081/api/v1/events?");
    }

    // Trailing slash normalization tests (ZAP fix: prevent 500 on /api/v1/events/)
    @Test
    @DisplayName("should_stripTrailingSlash_when_requestUriEndsWithSlash")
    void should_stripTrailingSlash_when_requestUriEndsWithSlash() {
        // Given
        String targetService = "event-management-service";
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/events/");
        request.setMethod("GET");

        ArgumentCaptor<URI> uriCaptor = ArgumentCaptor.forClass(URI.class);
        when(restTemplate.exchange(
            uriCaptor.capture(),
            any(),
            any(),
            eq(byte[].class)
        )).thenReturn(ResponseEntity.ok("[]".getBytes(java.nio.charset.StandardCharsets.UTF_8)));

        // When
        domainRouter.routeRequest(targetService, request).join();

        // Then: proxied URI must NOT end with trailing slash
        assertThat(uriCaptor.getValue().getPath()).doesNotEndWith("/");
        assertThat(uriCaptor.getValue().getPath()).isEqualTo("/api/v1/events");
    }

    @Test
    @DisplayName("should_notModifyPath_when_requestUriHasNoTrailingSlash")
    void should_notModifyPath_when_requestUriHasNoTrailingSlash() {
        // Given
        String targetService = "event-management-service";
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/events/BATbern42");
        request.setMethod("GET");

        ArgumentCaptor<URI> uriCaptor = ArgumentCaptor.forClass(URI.class);
        when(restTemplate.exchange(
            uriCaptor.capture(),
            any(),
            any(),
            eq(byte[].class)
        )).thenReturn(ResponseEntity.ok("{}".getBytes(java.nio.charset.StandardCharsets.UTF_8)));

        // When
        domainRouter.routeRequest(targetService, request).join();

        // Then: path must remain unchanged
        assertThat(uriCaptor.getValue().getPath()).isEqualTo("/api/v1/events/BATbern42");
    }

    @Test
    @DisplayName("should_notModifyRootPath_when_requestUriIsSlashOnly")
    void should_notModifyRootPath_when_requestUriIsSlashOnly() {
        // Given — root "/" is the only single-char path ending with slash; it must not be stripped
        String targetService = "event-management-service";
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/");
        request.setMethod("GET");

        ArgumentCaptor<URI> uriCaptor = ArgumentCaptor.forClass(URI.class);
        when(restTemplate.exchange(
            uriCaptor.capture(),
            any(),
            any(),
            eq(byte[].class)
        )).thenReturn(ResponseEntity.ok("{}".getBytes(java.nio.charset.StandardCharsets.UTF_8)));

        // When
        domainRouter.routeRequest(targetService, request).join();

        // Then: root path "/" is preserved as-is (length == 1, not stripped)
        assertThat(uriCaptor.getValue().getPath()).isEqualTo("/");
    }
}