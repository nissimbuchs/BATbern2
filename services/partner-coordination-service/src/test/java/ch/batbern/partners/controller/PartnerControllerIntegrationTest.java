package ch.batbern.partners.controller;

import ch.batbern.partners.client.CompanyServiceClient;
import ch.batbern.partners.client.UserServiceClient;
import ch.batbern.partners.client.user.dto.UserResponse;
import ch.batbern.partners.config.TestAwsConfig;
import ch.batbern.partners.config.TestSecurityConfig;
import ch.batbern.partners.domain.Partner;
import ch.batbern.partners.domain.PartnershipLevel;
import ch.batbern.partners.client.company.dto.CompanyResponse;
import ch.batbern.partners.repository.PartnerRepository;
import ch.batbern.shared.test.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
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

    @MockitoBean
    private CompanyServiceClient companyServiceClient;

    @MockitoBean
    private UserServiceClient userServiceClient;

    private CompanyResponse mockCompanyResponse;

    @BeforeEach
    void setUp() {
        partnerRepository.deleteAll();

        // Mock Company Service HTTP response
        mockCompanyResponse = new CompanyResponse();
        mockCompanyResponse.setName("GoogleZH");  // Generated DTO uses setName()
        mockCompanyResponse.setDisplayName("Google Zurich");
        // Note: Logo is CompanyLogo object in generated DTO, skip for this test

        when(companyServiceClient.getCompany(anyString())).thenReturn(mockCompanyResponse);
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

    @Test
    void should_returnPartnerByCompanyName_when_validCompanyNameProvided() throws Exception {
        // Given
        createTestPartner("GoogleZH", PartnershipLevel.GOLD);

        // When/Then
        mockMvc.perform(get("/api/v1/partners/GoogleZH")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companyName").value("GoogleZH"))
                .andExpect(jsonPath("$.partnershipLevel").value("GOLD"))
                .andExpect(jsonPath("$.isActive").value(true));
    }

    @Test
    void should_return404_when_companyNameNotFound() throws Exception {
        // When/Then - Use valid company name length (≤12 chars per VARCHAR(12) schema)
        mockMvc.perform(get("/api/v1/partners/NotFoundCo")
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

        // When/Then
        mockMvc.perform(get("/api/v1/partners/GoogleZH")
                        .param("include", "company")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companyName").value("GoogleZH"))
                .andExpect(jsonPath("$.company").exists())
                .andExpect(jsonPath("$.company.companyName").value("GoogleZH"))
                .andExpect(jsonPath("$.company.displayName").value("Google Zurich"));
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
    @org.springframework.security.test.context.support.WithMockUser(roles = "PARTNER")
    void should_returnTypedCompanyName_when_getMyPartnerCompanyCalledByPartner() throws Exception {
        // Given: the user-service resolves the current user's companyId
        UserResponse user = new UserResponse();
        user.setId("partner.user");
        user.setCompanyId("GoogleZH");
        when(userServiceClient.getCurrentUserProfile()).thenReturn(user);

        // When/Then: typed MyPartnerCompanyResponse (no raw Map)
        mockMvc.perform(get("/api/v1/partners/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companyName").value("GoogleZH"));
    }

    @Test
    void should_return401or403_when_getMyPartnerCompanyCalledWithoutAuth() throws Exception {
        mockMvc.perform(get("/api/v1/partners/me"))
                .andExpect(status().is4xxClientError());
    }

    // Helper methods
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
    // The assertions live on @PreAuthorize rather than on the filter chain deliberately: the
    // `test` profile chain is permitAll, so a matcher-only rule cannot be tested here at all,
    // while @EnableMethodSecurity is active in every profile. The matchers are still added in
    // SecurityConfig for defence in depth (and to reject earlier, before the controller), but
    // method security is what makes this stick.

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
        mockMvc.perform(get("/api/v1/partners/statistics")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
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
    void should_omitContacts_when_anonymousCallerRequestsDetailWithContacts() throws Exception {
        createTestPartner("GoogleZH", PartnershipLevel.GOLD);
        stubOneContactFor("GoogleZH");

        mockMvc.perform(get("/api/v1/partners/GoogleZH")
                        .param("include", "contacts")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contacts").isEmpty());
    }

    /**
     * Stub the User Service so `include=contacts` would return a real contact IF the caller
     * were entitled to it. Without this the Mockito default (empty list) makes every
     * "contacts must be empty" assertion pass vacuously — it would still pass with the
     * unauthenticated leak fully intact.
     */
    private void stubOneContactFor(String companyName) {
        UserResponse contact = new UserResponse();
        contact.setId("partner." + companyName.toLowerCase());
        contact.setEmail("contact@example.com");
        contact.setFirstName("Ada");
        contact.setLastName("Lovelace");
        when(userServiceClient.getUsersByCompanyAndRole(companyName, "PARTNER"))
                .thenReturn(java.util.List.of(contact));
    }

}
