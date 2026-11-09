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
 * {@code GET /internal/events/{eventCode}/schedule} (ems-internal; before the 17.0 deferred work the
 * public {@code GET /events/{eventCode}?include=sessions}, which shows sessions to organizers only).
 */
@RestClientTest(components = {
    ch.batbern.partners.client.impl.EventManagementClientImpl.class,
    ch.batbern.partners.config.RestClientConfig.class,
    ch.batbern.partners.config.CacheConfig.class
})
@TestPropertySource(properties = "event-management-service.base-url=http://event-management:8080")
class EventManagementClientContractTest {

    private static final String ANALYTICS = "docs/api/event-analytics-api.openapi.yml";
    private static final String EMS_INTERNAL = "docs/api/internal/ems-internal.openapi.yml";

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
    @DisplayName("event summary: date, venue and EMS's start and end, in Zurich time")
    void should_buildEventSummary_fromSpecExample() {
        server = ContractServer.bindTo(restTemplate, EMS_INTERNAL);
        server.expect(GET, "/internal/events/{eventCode}/schedule").respondWithExample("agendaPublished");

        EventSummaryDTO summary = client.getEventSummary("BATbern142");

        assertThat(summary.eventCode()).isEqualTo("BATbern142");
        assertThat(summary.title()).isEqualTo("BATbern 142 - Cloud Native");
        assertThat(summary.eventDate()).isEqualTo(LocalDate.of(2026, 6, 11));
        // Zurich is UTC+2 in June: 15:30Z -> 17:30, 19:45Z -> 21:45.
        assertThat(summary.startTime()).isEqualTo(LocalTime.of(17, 30));
        assertThat(summary.endTime()).isEqualTo(LocalTime.of(21, 45));
        assertThat(summary.venue()).isEqualTo("Kursaal Bern, Kornhausstrasse 3, 3013 Bern");
        server.verify();
    }

    @Test
    @DisplayName("before the agenda: the times EMS configures for the event, not a copy of the type defaults in PCS")
    void should_useEmsTimes_when_agendaNotPublished() {
        server = ContractServer.bindTo(restTemplate, EMS_INTERNAL);
        server.expect(GET, "/internal/events/{eventCode}/schedule").respondWithExample("beforeAgenda");

        EventSummaryDTO summary = client.getEventSummary("BATbern143");

        // EVENING as EMS configures it (V32: 16:00-19:00); Zurich is UTC+1 in November. The copy
        // PCS kept gave 18:00-22:00.
        assertThat(summary.startTime()).isEqualTo(LocalTime.of(16, 0));
        assertThat(summary.endTime()).isEqualTo(LocalTime.of(19, 0));
        assertThat(summary.eventDate()).isEqualTo(LocalDate.of(2026, 11, 5));
        assertThat(summary.venue()).isEqualTo("Kursaal Bern");
        server.verify();
    }

    @Test
    @DisplayName("without a JWT (scheduler, partner caller) the call works: it needs none")
    void should_buildEventSummary_when_noJwtToForward() {
        SecurityContextHolder.clearContext();
        server = ContractServer.bindTo(restTemplate, EMS_INTERNAL);
        server.expect(GET, "/internal/events/{eventCode}/schedule")
                .andExpect(r -> assertThat(r.getHeaders().containsHeader(HttpHeaders.AUTHORIZATION)).isFalse())
                .respondWithExample("agendaPublished");

        EventSummaryDTO summary = client.getEventSummary("BATbern142");

        assertThat(summary.startTime()).isEqualTo(LocalTime.of(17, 30));
        assertThat(summary.endTime()).isEqualTo(LocalTime.of(21, 45));
        server.verify();
    }

    @Test
    void should_rejectUnknownEvent_when_emsAnswers404() {
        server = ContractServer.bindTo(restTemplate, EMS_INTERNAL);
        server.expect(GET, "/internal/events/{eventCode}/schedule").respondWithStatus(HttpStatus.NOT_FOUND);

        assertThatThrownBy(() -> client.getEventSummary("BATbern999"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("BATbern999");
        server.verify();
    }
}
