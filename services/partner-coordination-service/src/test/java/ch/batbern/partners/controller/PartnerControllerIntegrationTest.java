package ch.batbern.partners.controller;

import ch.batbern.partners.config.CumsInternalUsers;
import ch.batbern.partners.config.TestAwsConfig;
import ch.batbern.partners.config.TestSecurityConfig;
import ch.batbern.partners.domain.Partner;
import ch.batbern.partners.domain.PartnershipLevel;
import ch.batbern.partners.repository.PartnerRepository;
import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.shared.test.contract.ContractServer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.web.client.RestTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;

/**
 * Integration tests for Partner CRUD API endpoints.
 * Tests extend AbstractIntegrationTest for PostgreSQL Testcontainer.
 * Tests are written BEFORE implementation (TDD RED phase).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import({TestSecurityConfig.class, TestAwsConfig.class})
class PartnerControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PartnerRepository partnerRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private CacheManager cacheManager;

    /**
     * CUMS behind the real CompanyServiceClient and UserServiceClient, per cums-internal (17.0b1,
     * 17.17; the company read is {@code /internal/companies/{name}} since 6b).
     */
    private ContractServer cums;

    /** The {@code /internal/users/{username}} lookup ({@code /partners/me}, ownership checks). */
    private CumsInternalUsers users;

    @BeforeEach
    void setUp() {
        partnerRepository.deleteAll();
        cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());

        cums = ContractServer.bindTo(restTemplate, CumsInternalUsers.CUMS_INTERNAL);
        users = CumsInternalUsers.answerOn(cums);
        cums.expect(HttpMethod.GET, "/internal/companies/{name}").times(ExpectedCount.manyTimes())
                .respondWith(HttpStatus.OK, """
                        {"name": "GoogleZH", "displayName": "Google Zurich", "isVerified": true,
                         "createdAt": "2025-01-15T10:00:00Z", "updatedAt": "2025-01-15T10:00:00Z", "createdBy": "lead.organizer"}""");
    }

    @AfterEach
    void releaseCums() {
        cums.close();
    }

    @Test
    void should_returnPartnerList_when_listEndpointCalled() throws Exception {
        // Given
        createTestPartner("GoogleZH", PartnershipLevel.GOLD);
        createTestPartner("MicrosoftZH", PartnershipLevel.PLATINUM);

        // When/Then
        mockMvc.perform(get("/api/v1/partners")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].companyName").exists())
                .andExpect(jsonPath("$.data[0].partnershipLevel").exists())
                .andExpect(jsonPath("$.metadata").exists());
    }

    @Test
    void should_filterByPartnershipLevel_when_levelParameterProvided() throws Exception {
        // Given
        createTestPartner("GoogleZH", PartnershipLevel.GOLD);
        createTestPartner("MicrosoftZH", PartnershipLevel.PLATINUM);

        // When/Then
        mockMvc.perform(get("/api/v1/partners")
                        .param("filter", "partnershipLevel=gold")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].partnershipLevel").value("GOLD"));
    }

    @Test
    void should_filterByActiveStatus_when_isActiveParameterProvided() throws Exception {
        // Given
        Partner activePartner = createTestPartner("GoogleZH", PartnershipLevel.GOLD);

        // Create inactive partner with end date in the past
        Partner inactivePartner = new Partner();
        inactivePartner.setCompanyName("MicrosoftZH");
        inactivePartner.setPartnershipLevel(PartnershipLevel.PLATINUM);
        inactivePartner.setPartnershipStartDate(LocalDate.now().minusYears(2));
        inactivePartner.setPartnershipEndDate(LocalDate.now().minusYears(1)); // Ended 1 year ago
        partnerRepository.save(inactivePartner);

        // When/Then
        mockMvc.perform(get("/api/v1/partners")
                        .param("filter", "isActive=true")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].companyName").value("GoogleZH"))
                .andExpect(jsonPath("$.data[0].isActive").value(true));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Partner directory "Sort by" (17.0 deferred work): sort=name|-name|tier|-tier
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    void should_sortByTierHighestFirst_when_sortIsMinusTier() throws Exception {
        createTestPartner("alpha", PartnershipLevel.BRONZE);
        createTestPartner("bravo", PartnershipLevel.STRATEGIC);
        createTestPartner("charlie", PartnershipLevel.GOLD);
        createTestPartner("delta", PartnershipLevel.GOLD);
        createTestPartner("echo", PartnershipLevel.PLATINUM);
        createTestPartner("foxtrot", PartnershipLevel.SILVER);

        // Tier rank, not the alphabet of the stored enum name; equal tiers by slug.
        mockMvc.perform(get("/api/v1/partners").param("sort", "-tier"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].companyName",
                        contains("bravo", "echo", "charlie", "delta", "foxtrot", "alpha")));
    }

    @Test
    void should_sortByTierLowestFirst_when_sortIsTier() throws Exception {
        createTestPartner("alpha", PartnershipLevel.STRATEGIC);
        createTestPartner("bravo", PartnershipLevel.BRONZE);
        createTestPartner("charlie", PartnershipLevel.SILVER);

        mockMvc.perform(get("/api/v1/partners").param("sort", "tier"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].companyName", contains("bravo", "charlie", "alpha")));
    }

    @Test
    void should_pageInTierOrder_when_sortIsMinusTierAndPaged() throws Exception {
        createTestPartner("alpha", PartnershipLevel.BRONZE);
        createTestPartner("bravo", PartnershipLevel.STRATEGIC);
        createTestPartner("charlie", PartnershipLevel.GOLD);

        // The order holds across pages and the count ignores it.
        mockMvc.perform(get("/api/v1/partners").param("sort", "-tier").param("page", "1").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].companyName", contains("alpha")))
                .andExpect(jsonPath("$.metadata.totalItems").value(3));
    }

    @Test
    void should_sortBySlugDescending_when_sortIsMinusName() throws Exception {
        createTestPartner("alpha", PartnershipLevel.GOLD);
        createTestPartner("charlie", PartnershipLevel.GOLD);
        createTestPartner("bravo", PartnershipLevel.GOLD);

        mockMvc.perform(get("/api/v1/partners").param("sort", "-name"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].companyName", contains("charlie", "bravo", "alpha")));
        mockMvc.perform(get("/api/v1/partners").param("sort", "name"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].companyName", contains("alpha", "bravo", "charlie")));
    }

    @Test
    void should_return400_when_sortFieldUnknown() throws Exception {
        mockMvc.perform(get("/api/v1/partners").param("sort", "engagement"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("sort")));
    }

    @Test
    void should_returnPartnerByCompanyName_when_validCompanyNameProvided() throws Exception {
        // Given
        createTestPartner("GoogleZH", PartnershipLevel.GOLD);

        // Anonymous: production (and its chain, run here since 17.0b3) and the gateway answer 401.
        mockMvc.perform(get("/api/v1/partners/GoogleZH")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());

        // When/Then
        mockMvc.perform(get("/api/v1/partners/GoogleZH")
                        .with(user("some.attendee").roles("ATTENDEE"))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companyName").value("GoogleZH"))
                .andExpect(jsonPath("$.partnershipLevel").value("GOLD"))
                .andExpect(jsonPath("$.isActive").value(true));
    }

    @Test
    void should_return404_when_companyNameNotFound() throws Exception {
        // Anonymous: production (and its chain, run here since 17.0b3) and the gateway answer 401.
        mockMvc.perform(get("/api/v1/partners/NotFoundCo")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());

        // When/Then - Use valid company name length (≤12 chars per VARCHAR(12) schema)
        mockMvc.perform(get("/api/v1/partners/NotFoundCo")
                        .with(user("some.attendee").roles("ATTENDEE"))
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void should_enrichWithCompanyData_when_includeCompanyRequested() throws Exception {
        // Given
        createTestPartner("GoogleZH", PartnershipLevel.GOLD);

        // Anonymous: production (and its chain, run here since 17.0b3) and the gateway answer 401.
        mockMvc.perform(get("/api/v1/partners/GoogleZH")
                        .param("include", "company")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());

        // When/Then
        mockMvc.perform(get("/api/v1/partners/GoogleZH")
                        .with(user("some.attendee").roles("ATTENDEE"))
                        .param("include", "company")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companyName").value("GoogleZH"))
                .andExpect(jsonPath("$.company").exists())
                .andExpect(jsonPath("$.company.companyName").value("GoogleZH"))
                .andExpect(jsonPath("$.company.displayName").value("Google Zurich"))
                // CUMS has no company location; the field was declared but never filled
                // (removed 2026-11-09, owner decision).
                .andExpect(jsonPath("$.company", not(hasKey("location"))));
    }

    @Test
    @WithMockUser(roles = "ORGANIZER")
    void should_createPartner_when_validRequestProvided() throws Exception {
        // Given
        Map<String, Object> request = new HashMap<>();
        request.put("companyName", "GoogleZH");
        request.put("partnershipLevel", "GOLD");
        request.put("partnershipStartDate", LocalDate.now().toString());

        // When/Then - ADR-003: companyName is the meaningful ID, no UUID in response
        mockMvc.perform(post("/api/v1/partners")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.companyName").value("GoogleZH"))
                .andExpect(jsonPath("$.partnershipLevel").value("GOLD"))
                .andExpect(jsonPath("$.isActive").value(true));
    }

    @Test
    @WithMockUser(roles = "ORGANIZER")
    void should_return400_when_partnerAlreadyExistsForCompany() throws Exception {
        // Given
        createTestPartner("GoogleZH", PartnershipLevel.GOLD);

        Map<String, Object> request = new HashMap<>();
        request.put("companyName", "GoogleZH");
        request.put("partnershipLevel", "PLATINUM");
        request.put("partnershipStartDate", LocalDate.now().toString());

        // When/Then
        mockMvc.perform(post("/api/v1/partners")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("GoogleZH")))
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    @WithMockUser(roles = "ORGANIZER")
    void should_updatePartnershipLevel_when_patchRequestValid() throws Exception {
        // Given
        createTestPartner("GoogleZH", PartnershipLevel.GOLD);

        Map<String, Object> updateRequest = new HashMap<>();
        updateRequest.put("partnershipLevel", "PLATINUM");

        // When/Then
        mockMvc.perform(patch("/api/v1/partners/GoogleZH")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companyName").value("GoogleZH"))
                .andExpect(jsonPath("$.partnershipLevel").value("PLATINUM"));
    }

    @Test
    @WithMockUser(roles = "ORGANIZER")
    void should_updatePartnershipEndDate_when_patchRequestValid() throws Exception {
        // Given
        createTestPartner("GoogleZH", PartnershipLevel.GOLD);

        Map<String, Object> updateRequest = new HashMap<>();
        LocalDate endDate = LocalDate.now().plusYears(2);
        updateRequest.put("partnershipEndDate", endDate.toString());

        // When/Then
        mockMvc.perform(patch("/api/v1/partners/GoogleZH")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.partnershipEndDate").value(endDate.toString()));
    }

    @Test
    @WithMockUser(roles = "ORGANIZER")
    void should_return404_when_updatingNonExistentPartner() throws Exception {
        // Given
        Map<String, Object> updateRequest = new HashMap<>();
        updateRequest.put("partnershipLevel", "PLATINUM");

        // When/Then - Use valid company name length (≤12 chars per VARCHAR(12) schema)
        mockMvc.perform(patch("/api/v1/partners/NotFoundCo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @WithMockUser(roles = "ORGANIZER")
    void should_softDeletePartner_when_deleteEndpointCalled() throws Exception {
        // Given
        createTestPartner("GoogleZH", PartnershipLevel.GOLD);

        // When/Then
        mockMvc.perform(delete("/api/v1/partners/GoogleZH")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNoContent());

        // Verify soft delete
        mockMvc.perform(get("/api/v1/partners/GoogleZH")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isActive").value(false));
    }

    // ── Reactivation (issue #821) ────────────────────────────────────────────────
    //
    // The Settings-tab Active toggle was a no-op. Deactivation already worked via DELETE
    // (sets partnershipEndDate = today), but there was no way back: isActive is DERIVED
    // from partnershipEndDate, and updatePartner ignores a null end date because a partial
    // update cannot distinguish an absent property from an explicit null. Hence a dedicated
    // operation whose whole job is to clear the field.

    @Test
    @WithMockUser(roles = "ORGANIZER")
    void should_reactivatePartner_when_reactivateEndpointCalledOnDeactivatedPartner()
            throws Exception {
        // Given: a partner that has been soft-deleted
        createTestPartner("GoogleZH", PartnershipLevel.GOLD);
        mockMvc.perform(delete("/api/v1/partners/GoogleZH")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/partners/GoogleZH")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.isActive").value(false));

        // When: reactivated
        mockMvc.perform(post("/api/v1/partners/GoogleZH/reactivate")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isActive").value(true))
                // The end date is CLEARED, not pushed out — the partnership is open-ended
                // again, which is the state isActive() treats as unconditionally active.
                .andExpect(jsonPath("$.partnershipEndDate").doesNotExist());

        // Then: the change persists on a fresh read
        mockMvc.perform(get("/api/v1/partners/GoogleZH")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isActive").value(true));
    }

    @Test
    @WithMockUser(roles = "ORGANIZER")
    void should_beIdempotent_when_reactivatingAnAlreadyActivePartner() throws Exception {
        // Given: an active partner (no end date)
        createTestPartner("GoogleZH", PartnershipLevel.GOLD);

        // When/Then: reactivating twice is harmless — the toggle must not be able to break
        // state if the UI is out of date or the organizer double-clicks.
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/v1/partners/GoogleZH/reactivate")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.isActive").value(true));
        }
    }

    @Test
    @WithMockUser(roles = "ORGANIZER")
    void should_return404_when_reactivatingNonExistentPartner() throws Exception {
        mockMvc.perform(post("/api/v1/partners/NotFoundCo/reactivate")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @WithMockUser(roles = "ORGANIZER")
    void should_return404_when_deletingNonExistentPartner() throws Exception {
        // When/Then - Use valid company name length (≤12 chars per VARCHAR(12) schema)
        mockMvc.perform(delete("/api/v1/partners/NotFoundCo")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void should_return400_when_paginationParamIsNotAnInteger() throws Exception {
        // ZAP finding: ?page=c:/Windows/system.ini caused 500 — must return 400
        mockMvc.perform(get("/api/v1/partners")
                        .param("page", "not-an-integer")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(containsString("page")));
    }

    @Test
    void should_return400_when_sizeParamIsNotAnInteger() throws Exception {
        // ZAP finding: non-integer size param caused 500 — must return 400
        mockMvc.perform(get("/api/v1/partners")
                        .param("size", "c:/Windows/system.ini")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(containsString("size")));
    }

    @Test
    @WithMockUser(username = "partner.user", roles = "PARTNER")
    void should_returnTypedCompanyName_when_getMyPartnerCompanyCalledByPartner() throws Exception {
        // Given: CUMS /internal/users/{username} knows the caller's company (6b: no /users/me)
        users.put("partner.user", "partner.user", "GoogleZH");

        // When/Then: typed MyPartnerCompanyResponse (no raw Map)
        mockMvc.perform(get("/api/v1/partners/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companyName").value("GoogleZH"));
    }

    @Test
    @DisplayName("6b: /partners/me reads the caller by the JWT's custom:username, not by its subject")
    void should_resolveCompany_byCustomUsernameClaim_withForwardedJwt() throws Exception {
        users.put("partner.jwt", "partner.jwt", "MicrosoftZH");

        mockMvc.perform(get("/api/v1/partners/me")
                        .with(jwt().jwt(j -> j.tokenValue("partner-jwt").subject("6f1c-cognito-sub")
                                        .claim("custom:username", "partner.jwt")
                                        .expiresAt(Instant.now().plusSeconds(3600)))
                                .authorities(new SimpleGrantedAuthority("ROLE_PARTNER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companyName").value("MicrosoftZH"));
    }

    @Test
    @WithMockUser(username = "partner.nocompany", roles = "PARTNER")
    void should_return404_when_partnerHasNoCompany() throws Exception {
        users.put("partner.nocompany", "partner.nocompany", null);

        mockMvc.perform(get("/api/v1/partners/me"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(containsString("not linked to any partner company")));
    }

    @Test
    @DisplayName("a token without custom:username falls back to the subject, which CUMS does not know: not linked")
    void should_return404_when_tokenHasNoUsernameClaim() throws Exception {
        // Positive control: the same lookup with the claim resolves (registry answers that name only).
        users.put("partner.claim", "partner.claim", "GoogleZH");
        mockMvc.perform(get("/api/v1/partners/me")
                        .with(jwt().jwt(j -> j.subject("partner.claim-sub").claim("custom:username", "partner.claim"))
                                .authorities(new SimpleGrantedAuthority("ROLE_PARTNER"))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/partners/me")
                        .with(jwt().jwt(j -> j.subject("partner.claim-sub"))
                                .authorities(new SimpleGrantedAuthority("ROLE_PARTNER"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(containsString("not linked to any partner company")));
    }

    @Test
    void should_return401or403_when_getMyPartnerCompanyCalledWithoutAuth() throws Exception {
        mockMvc.perform(get("/api/v1/partners/me"))
                .andExpect(status().is4xxClientError());
    }

    // Helper methods
    // GHSA-qhqv: the About page shows "partner since"; when a contract ends is commercial data.

    @Test
    void should_omitPartnershipEndDate_when_anonymousListsPartners() throws Exception {
        Partner partner = createTestPartner("GoogleZH", PartnershipLevel.GOLD);
        partner.setPartnershipEndDate(LocalDate.now().plusYears(1));
        partnerRepository.save(partner);

        mockMvc.perform(get("/api/v1/partners"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].partnershipStartDate").exists())
                .andExpect(jsonPath("$.data[0].partnershipEndDate").doesNotExist());
    }

    @Test
    @WithMockUser(username = "some.partner", roles = {"PARTNER"})
    void should_omitPartnershipEndDate_when_partnerListsAllPartners() throws Exception {
        Partner partner = createTestPartner("GoogleZH", PartnershipLevel.GOLD);
        partner.setPartnershipEndDate(LocalDate.now().plusYears(1));
        partnerRepository.save(partner);

        mockMvc.perform(get("/api/v1/partners"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].partnershipEndDate").doesNotExist());
    }

    @Test
    @WithMockUser(username = "test-organizer", roles = {"ORGANIZER"})
    void should_includePartnershipEndDate_when_organizerListsPartners() throws Exception {
        LocalDate end = LocalDate.now().plusYears(1);
        Partner partner = createTestPartner("GoogleZH", PartnershipLevel.GOLD);
        partner.setPartnershipEndDate(end);
        partnerRepository.save(partner);

        mockMvc.perform(get("/api/v1/partners"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].partnershipEndDate").value(end.toString()));
    }

    private Partner createTestPartner(String companyName, PartnershipLevel level) {
        Partner partner = new Partner();
        partner.setCompanyName(companyName);
        partner.setPartnershipLevel(level);
        partner.setPartnershipStartDate(LocalDate.now());
        // Note: isActive() is calculated based on dates, no setActive() method
        return partnerRepository.save(partner);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Issue #961 — authorization on partner mutations, and the contacts include
    // ═══════════════════════════════════════════════════════════════════════════
    //
    // These endpoints previously fell through to `.anyRequest().authenticated()` in
    // SecurityConfig AND carried no @PreAuthorize, so ANY authenticated principal — an
    // ATTENDEE, a SPEAKER, a PARTNER of a different company — could create, re-level or
    // deactivate a partnership. Only the client-side half of the rule existed: the Settings
    // tab is organizer-only in the UI, which is exactly the protection that looks sufficient
    // until someone calls the API directly.
    //
    // Since 17.0b3 these tests run the production chain, so both layers are under test: the
    // SecurityConfig matchers reject earlier, @PreAuthorize on the controller methods is the
    // second wall.

    @Test
    @WithMockUser(username = "some.attendee", roles = "ATTENDEE")
    void should_return403_when_attendeeCreatesPartner() throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("companyName", "EvilCo");
        request.put("partnershipLevel", "GOLD");
        request.put("partnershipStartDate", LocalDate.now().toString());

        mockMvc.perform(post("/api/v1/partners")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "some.attendee", roles = "ATTENDEE")
    void should_return403_when_attendeeUpdatesPartner() throws Exception {
        createTestPartner("GoogleZH", PartnershipLevel.GOLD);
        Map<String, Object> request = new HashMap<>();
        request.put("partnershipLevel", "BRONZE");

        mockMvc.perform(patch("/api/v1/partners/GoogleZH")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "some.attendee", roles = "ATTENDEE")
    void should_return403_when_attendeeDeactivatesPartner() throws Exception {
        createTestPartner("GoogleZH", PartnershipLevel.GOLD);

        mockMvc.perform(delete("/api/v1/partners/GoogleZH")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    // A PARTNER is not an organizer. This is the case the UI guard cannot cover at all, since
    // a partner legitimately has a portal and a valid token.
    @Test
    @WithMockUser(username = "partner.google", roles = "PARTNER")
    void should_return403_when_partnerDeactivatesAnotherPartnership() throws Exception {
        createTestPartner("MicrosoftZH", PartnershipLevel.GOLD);

        mockMvc.perform(delete("/api/v1/partners/MicrosoftZH")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "some.attendee", roles = "ATTENDEE")
    void should_return403_when_attendeeReadsPartnerStatistics() throws Exception {
        mockMvc.perform(get("/api/v1/partners/statistics")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "organizer.user", roles = "ORGANIZER")
    void should_allowStatistics_when_callerIsOrganizer() throws Exception {
        createTestPartner("GoogleZH", PartnershipLevel.GOLD);

        // recentPartners and expiringPartnerships were declared but never computed, so always
        // null; removed 2026-11-09 (owner decision).
        mockMvc.perform(get("/api/v1/partners/statistics")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPartners").value(1))
                .andExpect(jsonPath("$.activePartners").value(1))
                .andExpect(jsonPath("$.tierCounts.GOLD").value(1))
                .andExpect(jsonPath("$", not(hasKey("recentPartners"))))
                .andExpect(jsonPath("$", not(hasKey("expiringPartnerships"))));
    }

    // ── the unauthenticated contacts exposure ──────────────────────────────────
    //
    // GET /api/v1/partners is permitAll on purpose — it backs the public partner list on the
    // homepage. But `include` was passed straight through to enrichWithContacts(), so
    // `GET /api/v1/partners?include=contacts` returned email, firstName, lastName, username
    // and profilePictureUrl for every partner contact TO ANONYMOUS CALLERS. Verified against
    // production on 2026-08-26: HTTP 200, 9 partners, 10 contact objects, 10 real addresses.
    //
    // The list stays public; the contacts enrichment is now dropped unless the caller is
    // entitled to it. Dropped rather than 403 so that a future public page adding the
    // parameter degrades instead of breaking the homepage.

    @Test
    void should_omitContacts_when_anonymousCallerRequestsContactsInclude() throws Exception {
        createTestPartner("GoogleZH", PartnershipLevel.GOLD);
        stubOneContactFor("GoogleZH");

        mockMvc.perform(get("/api/v1/partners")
                        .param("include", "contacts")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].contacts").isEmpty());
    }

    @Test
    @WithMockUser(username = "some.attendee", roles = "ATTENDEE")
    void should_omitContacts_when_attendeeRequestsContactsInclude() throws Exception {
        createTestPartner("GoogleZH", PartnershipLevel.GOLD);
        stubOneContactFor("GoogleZH");

        mockMvc.perform(get("/api/v1/partners")
                        .param("include", "contacts")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].contacts").isEmpty());
    }

    @Test
    @WithMockUser(username = "organizer.user", roles = "ORGANIZER")
    void should_includeContacts_when_organizerRequestsContactsInclude() throws Exception {
        createTestPartner("GoogleZH", PartnershipLevel.GOLD);

        stubOneContactFor("GoogleZH");

        mockMvc.perform(get("/api/v1/partners")
                        .param("include", "contacts")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].contacts", hasSize(1)));
    }

    // Same leak on the detail endpoint, which takes the same `include`.
    @Test
    void should_omitContacts_when_attendeeRequestsDetailWithContacts() throws Exception {
        createTestPartner("GoogleZH", PartnershipLevel.GOLD);
        stubOneContactFor("GoogleZH");

        // Anonymous: production (and its chain, run here since 17.0b3) and the gateway answer 401.
        mockMvc.perform(get("/api/v1/partners/GoogleZH")
                        .param("include", "contacts")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/partners/GoogleZH")
                        .with(user("some.attendee").roles("ATTENDEE"))
                        .param("include", "contacts")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contacts").isEmpty());
    }

    /**
     * CUMS answers `include=contacts` with a real contact IF the caller were entitled to it.
     * Without this an empty contact list would make every "contacts must be empty" assertion
     * pass vacuously — it would still pass with the unauthenticated leak fully intact.
     */
    private void stubOneContactFor(String companyName) {
        CumsInternalUsers.partnerContacts(cums, companyName,
                CumsInternalUsers.user("ada.lovelace", companyName, "Ada", "Lovelace"));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Partner list: filter, search and paging in SQL; search resolved by CUMS
    // ═══════════════════════════════════════════════════════════════════════════

    private void createPartners(int count) {
        for (int i = 10; i < 10 + count; i++) {
            createTestPartner("partner" + i, i % 2 == 0 ? PartnershipLevel.GOLD : PartnershipLevel.SILVER);
        }
    }

    @Test
    void should_returnSecondPage_withExactTotals_when_morePartnersThanOnePage() throws Exception {
        createPartners(25);

        mockMvc.perform(get("/api/v1/partners").param("page", "1").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(5)))
                .andExpect(jsonPath("$.data[0].companyName").value("partner30"))
                .andExpect(jsonPath("$.metadata.totalItems").value(25))
                .andExpect(jsonPath("$.metadata.totalPages").value(2))
                .andExpect(jsonPath("$.metadata.hasNext").value(false))
                .andExpect(jsonPath("$.metadata.hasPrev").value(true));
    }

    @Test
    void should_findPartnerBeyondFirstPage_when_searchMatchesItsDisplayName() throws Exception {
        createPartners(25);
        // CUMS resolves the display name typed by the organizer to the slug (#23 of 25)
        cums.expect(HttpMethod.GET, "/internal/companies").withQueryParam("search", "Die Mobiliar")
                .respondWith(HttpStatus.OK, "[\"partner32\", \"notapartner\"]");

        mockMvc.perform(get("/api/v1/partners").param("search", " Die Mobiliar ").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].companyName").value("partner32"))
                .andExpect(jsonPath("$.metadata.totalItems").value(1));
    }

    @Test
    void should_combineSearchWithTierAndStatusFilters() throws Exception {
        createPartners(4);
        Partner ended = createTestPartner("partner99", PartnershipLevel.GOLD);
        ended.setPartnershipStartDate(LocalDate.now().minusYears(3));
        ended.setPartnershipEndDate(LocalDate.now().minusYears(1));
        partnerRepository.save(ended);
        cums.expect(HttpMethod.GET, "/internal/companies").withQueryParam("search", "ag")
                .respondWith(HttpStatus.OK, "[\"partner10\", \"partner11\", \"partner12\", \"partner99\"]");

        mockMvc.perform(get("/api/v1/partners").param("search", "ag")
                        .param("filter", "partnershipLevel:gold,isActive:true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].companyName", contains("partner10", "partner12")))
                .andExpect(jsonPath("$.metadata.totalItems").value(2));
    }

    @Test
    void should_returnNothing_when_cumsMatchesNoCompany() throws Exception {
        createPartners(3);
        cums.expect(HttpMethod.GET, "/internal/companies").withQueryParam("search", "(test")
                .respondWith(HttpStatus.OK, "[]");

        mockMvc.perform(get("/api/v1/partners").param("search", "(test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)))
                .andExpect(jsonPath("$.metadata.totalItems").value(0));
    }

    @Test
    void should_notCallCums_and_notFilter_when_searchIsBlank() throws Exception {
        createPartners(3);

        mockMvc.perform(get("/api/v1/partners").param("search", "   "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.metadata.totalItems").value(3));
        // No /internal/companies expectation is registered: a call to CUMS would fail the request
        // on the contract server. The search tests above show the same server answering it.
    }
}
