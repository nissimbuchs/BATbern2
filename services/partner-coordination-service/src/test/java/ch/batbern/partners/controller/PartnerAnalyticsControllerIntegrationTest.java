package ch.batbern.partners.controller;

import ch.batbern.partners.client.EventManagementClient;
import ch.batbern.partners.client.dto.AttendanceSummaryDTO;
import ch.batbern.partners.config.TestAwsConfig;
import ch.batbern.partners.config.TestSecurityConfig;
import ch.batbern.partners.client.UserServiceClient;
import ch.batbern.partners.client.user.dto.UserResponse;
import ch.batbern.partners.domain.Partner;
import ch.batbern.partners.domain.PartnershipLevel;
import ch.batbern.partners.repository.PartnerRepository;
import ch.batbern.shared.test.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for PartnerAnalyticsController.
 * Story 8.1: Partner Attendance Dashboard - Task 10 (AC1, 2, 3, 6)
 *
 * TDD: Written before implementation to drive the controller design.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import({TestSecurityConfig.class, TestAwsConfig.class})
class PartnerAnalyticsControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PartnerRepository partnerRepository;

    @MockitoBean
    private EventManagementClient eventManagementClient;

    @MockitoBean
    private UserServiceClient userServiceClient;

    private Partner googlePartner;
    private Partner microsoftPartner;

    @BeforeEach
    void setUp() {
        partnerRepository.deleteAll();

        googlePartner = createPartner("GoogleZH", PartnershipLevel.GOLD, new BigDecimal("10000.00"));
        microsoftPartner = createPartner("MicrosoftZH", PartnershipLevel.PLATINUM, new BigDecimal("20000.00"));

        // Associate "partner.google" with GoogleZH via User Service
        UserResponse googleUser = new UserResponse();
        googleUser.setCompanyId("GoogleZH");
        when(userServiceClient.getUserByUsername("partner.google")).thenReturn(googleUser);
    }

    // ─── AC1/AC2: Dashboard returns per-event attendance data ────────────────

    @Test
    @WithMockUser(username = "partner.google", roles = "PARTNER")
    void should_returnDashboard_when_partnerRequestsOwnCompany() throws Exception {
        List<AttendanceSummaryDTO> summaries = List.of(
            new AttendanceSummaryDTO("BATbern57", "BATbern 57", Instant.parse("2024-06-01T00:00:00Z"), 100, 10),
            new AttendanceSummaryDTO("BATbern56", "BATbern 56", Instant.parse("2023-06-01T00:00:00Z"), 80, 8)
        );
        when(eventManagementClient.getAttendanceSummary(eq("GoogleZH"), anyInt()))
            .thenReturn(summaries);

        mockMvc.perform(get("/api/v1/partners/GoogleZH/analytics/dashboard")
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attendanceSummary", hasSize(2)))
                .andExpect(jsonPath("$.attendanceSummary[0].eventCode").value("BATbern57"))
                .andExpect(jsonPath("$.attendanceSummary[0].totalAttendees").value(100))
                .andExpect(jsonPath("$.attendanceSummary[0].companyAttendees").value(10));
    }

    @Test
    @WithMockUser(username = "partner.google", roles = "PARTNER")
    void should_return403_when_partnerRequestsOtherCompany() throws Exception {
        mockMvc.perform(get("/api/v1/partners/MicrosoftZH/analytics/dashboard")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "organizer.user", roles = "ORGANIZER")
    void should_returnDashboard_when_organizerRequestsAnyCompany() throws Exception {
        List<AttendanceSummaryDTO> summaries = List.of(
            new AttendanceSummaryDTO("BATbern57", "BATbern 57", Instant.parse("2024-06-01T00:00:00Z"), 100, 5)
        );
        when(eventManagementClient.getAttendanceSummary(eq("MicrosoftZH"), anyInt()))
            .thenReturn(summaries);

        mockMvc.perform(get("/api/v1/partners/MicrosoftZH/analytics/dashboard")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attendanceSummary", hasSize(1)));
    }

    // ─── AC2: fromYear parameter filtering ────────────────────────────────────

    @Test
    @WithMockUser(username = "organizer.user", roles = "ORGANIZER")
    void should_passFromYearToClient_when_fromYearParamProvided() throws Exception {
        List<AttendanceSummaryDTO> recent = List.of(
            new AttendanceSummaryDTO("BATbern57", "BATbern 57", Instant.parse("2024-06-01T00:00:00Z"), 100, 10)
        );
        when(eventManagementClient.getAttendanceSummary(eq("GoogleZH"), eq(2020)))
            .thenReturn(recent);

        mockMvc.perform(get("/api/v1/partners/GoogleZH/analytics/dashboard")
                        .param("fromYear", "2020")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attendanceSummary", hasSize(1)));
    }

    // ─── AC3: Cost per attendee ────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "partner.google", roles = "PARTNER")
    void should_computeCostPerAttendee_when_attendeesExist() throws Exception {
        // partnership_cost = 10000, total company attendees = 10+8 = 18
        // cost per attendee = 10000 / 18 ≈ 555.56
        List<AttendanceSummaryDTO> summaries = List.of(
            new AttendanceSummaryDTO("BATbern57", "BATbern 57", Instant.parse("2024-06-01T00:00:00Z"), 100, 10),
            new AttendanceSummaryDTO("BATbern56", "BATbern 56", Instant.parse("2023-06-01T00:00:00Z"), 80, 8)
        );
        when(eventManagementClient.getAttendanceSummary(eq("GoogleZH"), anyInt()))
            .thenReturn(summaries);

        mockMvc.perform(get("/api/v1/partners/GoogleZH/analytics/dashboard")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.costPerAttendee").value(closeTo(555.56, 0.01)));
    }

    @Test
    @WithMockUser(username = "partner.google", roles = "PARTNER")
    void should_returnNullCostPerAttendee_when_noAttendees() throws Exception {
        when(eventManagementClient.getAttendanceSummary(anyString(), anyInt()))
            .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/partners/GoogleZH/analytics/dashboard")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.costPerAttendee", is(nullValue())));
    }

    // ─── AC4: Excel export ────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "partner.google", roles = "PARTNER")
    void should_returnXlsx_when_exportRequested() throws Exception {
        List<AttendanceSummaryDTO> summaries = List.of(
            new AttendanceSummaryDTO("BATbern57", "BATbern 57", Instant.parse("2024-06-01T00:00:00Z"), 100, 10)
        );
        when(eventManagementClient.getAttendanceSummary(anyString(), anyInt()))
            .thenReturn(summaries);

        mockMvc.perform(get("/api/v1/partners/GoogleZH/analytics/export")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(header().exists("Content-Disposition"));
    }

    @Test
    @WithMockUser(username = "partner.google", roles = "PARTNER")
    void should_return403_when_partnerExportsOtherCompany() throws Exception {
        mockMvc.perform(get("/api/v1/partners/MicrosoftZH/analytics/export")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    // ─── AC6: Unauthenticated access ─────────────────────────────────────────

    @Test
    void should_return403_when_notAuthenticated() throws Exception {
        // Test filter chain uses permitAll (test profile), so anonymous user passes the filter.
        // @PreAuthorize method security evaluates → no required role → AccessDeniedException → 403.
        // Production behavior (API Gateway strips unauthenticated requests before reaching this service).
        mockMvc.perform(get("/api/v1/partners/GoogleZH/analytics/dashboard")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    // ─── Helper methods ──────────────────────────────────────────────────────

    private Partner createPartner(String companyName, PartnershipLevel level, BigDecimal cost) {
        Partner partner = new Partner();
        partner.setCompanyName(companyName);
        partner.setPartnershipLevel(level);
        partner.setPartnershipStartDate(LocalDate.now());
        partner.setPartnershipCost(cost);
        return partnerRepository.save(partner);
    }

}
