package ch.batbern.partners.client;

import ch.batbern.partners.client.dto.AttendanceSummaryDTO;
import ch.batbern.partners.client.dto.EventSummaryDTO;
import ch.batbern.shared.test.contract.ContractServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.restclient.test.autoconfigure.RestClientTest;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpMethod.GET;

/**
 * Contract test of {@link EventManagementClient} against the EMS specs (Story 17.0b1):
 * {@code GET /events/attendance-summary} (event-analytics-api) and
 * {@code GET /events/{eventCode}?include=sessions} (events-core-api).
 */
@RestClientTest(components = {
    ch.batbern.partners.client.impl.EventManagementClientImpl.class,
    ch.batbern.partners.config.RestClientConfig.class,
    ch.batbern.partners.config.CacheConfig.class
})
@TestPropertySource(properties = "event-management-service.base-url=http://event-management:8080")
class EventManagementClientContractTest {

    private static final String ANALYTICS = "docs/api/event-analytics-api.openapi.yml";
    private static final String EVENTS = "docs/api/events-core-api.openapi.yml";

    @Autowired
    private EventManagementClient client;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private CacheManager cacheManager;

    private ContractServer server;

    @BeforeEach
    void setUp() {
        Jwt jwt = Jwt.withTokenValue("partner-token").header("alg", "RS256").claim("sub", "partner.user").build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
        cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        if (server != null) {
            server.close();
        }
    }

    @Test
    void should_mapAttendanceSummary_fromSpecExample() {
        server = ContractServer.bindTo(restTemplate, ANALYTICS);
        server.expect(GET, "/api/v1/events/attendance-summary")
                .withQueryParam("companyName", "GoogleZH")
                .andExpect(r -> assertThat(r.getHeaders().getFirst(HttpHeaders.AUTHORIZATION))
                        .isEqualTo("Bearer partner-token"))
                .respondWithExample();

        List<AttendanceSummaryDTO> summary = client.getAttendanceSummary("GoogleZH", 2021);

        assertThat(summary).hasSize(2);
        assertThat(summary.get(0)).isEqualTo(new AttendanceSummaryDTO("BATbern142", "BATbern 142 - Cloud Native",
                Instant.parse("2026-06-11T16:00:00Z"), 180, 6));
        server.verify();
    }

    @Test
    void should_encodeCompanyNameStrictly_inAttendanceQuery() {
        server = ContractServer.bindTo(restTemplate, ANALYTICS);
        server.expect(GET, "/api/v1/events/attendance-summary")
                .withQueryParam("companyName", "Meier+Partner & Co")
                .andExpect(r -> assertThat(r.getURI().getRawQuery())
                        .isEqualTo("companyName=Meier%2BPartner%20%26%20Co&fromYear=2021"))
                .respondWithExample();

        assertThat(client.getAttendanceSummary("Meier+Partner & Co", 2021)).hasSize(2);
        server.verify();
    }

    @Test
    void should_buildEventSummary_fromSpecExample() {
        server = ContractServer.bindTo(restTemplate, EVENTS);
        server.expect(GET, "/api/v1/events/{eventCode}").withQueryParam("include", "sessions")
                .respondWithExample("expandedEvent");

        EventSummaryDTO summary = client.getEventSummary("BATbern142");

        assertThat(summary.eventCode()).isEqualTo("BATbern142");
        assertThat(summary.title()).isEqualTo("Spring Conference 2025");
        assertThat(summary.eventDate()).isEqualTo(LocalDate.of(2025, 5, 15));
        // No sessions in the example: the evening default applies.
        assertThat(summary.startTime()).isEqualTo(LocalTime.of(18, 0));
        assertThat(summary.venue()).isEqualTo("Kursaal Bern, Kornhausstrasse 3, 3013 Bern");
        server.verify();
    }

    @Test
    @DisplayName("event times come from the earliest session start and latest session end, in Zurich time")
    void should_deriveEventTimes_fromSessions() {
        server = ContractServer.bindTo(restTemplate, EVENTS);
        server.expect(GET, "/api/v1/events/{eventCode}").withQueryParam("include", "sessions")
                .respondWith(HttpStatus.OK, """
                        {"eventCode": "BATbern142", "title": "Spring Conference 2026", "eventNumber": 142,
                         "date": "2026-06-11T16:00:00Z", "registrationDeadline": "2026-06-04T16:00:00Z",
                         "venueName": "Kursaal Bern", "venueAddress": "Kornhausstrasse 3, 3013 Bern",
                         "venueCapacity": 500, "eventType": "EVENING",
                         "sessions": [
                           {"sessionSlug": "keynote", "eventCode": "BATbern142", "title": "Keynote",
                            "startTime": "2026-06-11T15:30:00Z", "endTime": "2026-06-11T16:15:00Z"},
                           {"sessionSlug": "closing", "eventCode": "BATbern142", "title": "Closing",
                            "startTime": "2026-06-11T18:00:00Z", "endTime": "2026-06-11T19:45:00Z"}
                         ]}""");

        EventSummaryDTO summary = client.getEventSummary("BATbern142");

        // Zurich is UTC+2 in June: 15:30Z -> 17:30, 19:45Z -> 21:45.
        assertThat(summary.startTime()).isEqualTo(LocalTime.of(17, 30));
        assertThat(summary.endTime()).isEqualTo(LocalTime.of(21, 45));
        assertThat(summary.eventDate()).isEqualTo(LocalDate.of(2026, 6, 11));
        server.verify();
    }

    @Test
    void should_rejectUnknownEvent_when_emsAnswers404() {
        server = ContractServer.bindTo(restTemplate, EVENTS);
        server.expect(GET, "/api/v1/events/{eventCode}").respondWithStatus(HttpStatus.NOT_FOUND);

        assertThatThrownBy(() -> client.getEventSummary("BATbern999"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("BATbern999");
        server.verify();
    }
}
