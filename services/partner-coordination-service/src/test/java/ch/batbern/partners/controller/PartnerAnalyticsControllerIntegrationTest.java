package ch.batbern.partners.controller;

import ch.batbern.partners.config.CumsInternalUsers;
import ch.batbern.partners.config.TestAwsConfig;
import ch.batbern.partners.config.TestSecurityConfig;
import ch.batbern.partners.domain.Partner;
import ch.batbern.partners.domain.PartnershipLevel;
import ch.batbern.partners.repository.PartnerRepository;
import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.shared.test.contract.ContractServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
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

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private CacheManager cacheManager;

    /** EMS behind the real EventManagementClient, answering per event-analytics-api (Story 17.0b1). */
    private ContractServer ems;


    private Partner googlePartner;
    private Partner microsoftPartner;

    @BeforeEach
    void setUp() {
        partnerRepository.deleteAll();
        cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
        // @WithMockUser callers carry no JWT to forward, hence ignoringMissingAuthorization.
        // One RestTemplate, two providers: EMS analytics and the CUMS user lookup (Story 17.0b2).
        ems = ContractServer.bindTo(restTemplate, "docs/api/event-analytics-api.openapi.yml",
                CumsInternalUsers.CUMS_INTERNAL).ignoringMissingAuthorization();

        googlePartner = createPartner("GoogleZH", PartnershipLevel.GOLD, new BigDecimal("10000.00"));
        microsoftPartner = createPartner("MicrosoftZH", PartnershipLevel.PLATINUM, new BigDecimal("20000.00"));

        // Associate "partner.google" with GoogleZH via CUMS
        CumsInternalUsers.answerOn(ems).put("partner.google", "partner.google", "GoogleZH");
    }

    @AfterEach
    void releaseEms() {
        ems.close();
    }

    /** EMS answers the attendance summary for {@code company} with {@code json} (checked against the spec). */
    private void emsAnswers(String company, String json) {
        ems.expect(HttpMethod.GET, "/api/v1/events/attendance-summary").withQueryParam("companyName", company)
                .respondWith(HttpStatus.OK, json);
    }

    private static String summary(String code, String title, String date, int total, int company) {
        return "{\"eventCode\": \"" + code + "\", \"eventTitle\": \"" + title + "\", \"eventDate\": \""
                + date + "\", \"totalAttendees\": " + total + ", \"companyAttendees\": " + company + "}";
    }

    // ─── AC1/AC2: Dashboard returns per-event attendance data ────────────────

    @Test
    @WithMockUser(username = "partner.google", roles = "PARTNER")
    void should_returnDashboard_when_partnerRequestsOwnCompany() throws Exception {
        emsAnswers("GoogleZH", "[" + summary("BATbern57", "BATbern 57", "2024-06-01T00:00:00Z", 100, 10)
                + ", " + summary("BATbern56", "BATbern 56", "2023-06-01T00:00:00Z", 80, 8) + "]");

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
        emsAnswers("MicrosoftZH", "[" + summary("BATbern57", "BATbern 57", "2024-06-01T00:00:00Z", 100, 5) + "]");

        mockMvc.perform(get("/api/v1/partners/MicrosoftZH/analytics/dashboard")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attendanceSummary", hasSize(1)));
    }

    // ─── AC2: fromYear parameter filtering ────────────────────────────────────

    @Test
    @WithMockUser(username = "organizer.user", roles = "ORGANIZER")
    void should_passFromYearToClient_when_fromYearParamProvided() throws Exception {
        ems.expect(HttpMethod.GET, "/api/v1/events/attendance-summary").withQueryParam("companyName", "GoogleZH")
                .withQueryParam("fromYear", "2020")
                .respondWith(HttpStatus.OK, "[" + summary("BATbern57", "BATbern 57", "2024-06-01T00:00:00Z", 100, 10) + "]");

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
        emsAnswers("GoogleZH", "[" + summary("BATbern57", "BATbern 57", "2024-06-01T00:00:00Z", 100, 10)
                + ", " + summary("BATbern56", "BATbern 56", "2023-06-01T00:00:00Z", 80, 8) + "]");

        mockMvc.perform(get("/api/v1/partners/GoogleZH/analytics/dashboard")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.costPerAttendee").value(closeTo(555.56, 0.01)));
    }

    @Test
    @WithMockUser(username = "partner.google", roles = "PARTNER")
    void should_returnNullCostPerAttendee_when_noAttendees() throws Exception {
        emsAnswers("GoogleZH", "[]");

        mockMvc.perform(get("/api/v1/partners/GoogleZH/analytics/dashboard")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.costPerAttendee", is(nullValue())));
    }

    // ─── AC4: Excel export ────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "partner.google", roles = "PARTNER")
    void should_returnXlsx_when_exportRequested() throws Exception {
        emsAnswers("GoogleZH", "[" + summary("BATbern57", "BATbern 57", "2024-06-01T00:00:00Z", 100, 10) + "]");

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
    void should_return401_when_notAuthenticated() throws Exception {
        // The production chain answers 401 before the controller (17.0b3).
        mockMvc.perform(get("/api/v1/partners/GoogleZH/analytics/dashboard")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
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
