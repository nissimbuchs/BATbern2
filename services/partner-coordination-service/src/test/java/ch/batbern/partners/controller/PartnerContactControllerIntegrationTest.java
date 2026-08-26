package ch.batbern.partners.controller;

import ch.batbern.partners.client.UserServiceClient;
import ch.batbern.partners.client.user.dto.UserResponse;
import ch.batbern.partners.config.TestAwsConfig;
import ch.batbern.partners.config.TestSecurityConfig;
import ch.batbern.partners.domain.Partner;
import ch.batbern.partners.domain.PartnershipLevel;
import ch.batbern.partners.repository.PartnerRepository;
import ch.batbern.partners.security.SecurityContextHelper;
import ch.batbern.shared.test.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for Partner Contact Management API.
 *
 * Contacts are derived from the User Service: any user with PARTNER role
 * and matching companyId is automatically a contact of that company.
 * No explicit add/remove management is needed.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import({TestSecurityConfig.class, TestAwsConfig.class})
@DisplayName("Partner Contact Controller Integration Tests")
class PartnerContactControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PartnerRepository partnerRepository;

    @MockitoBean
    private UserServiceClient userServiceClient;

    @MockitoBean
    private SecurityContextHelper securityContextHelper;

    private Partner testPartner;

    @BeforeEach
    void setUp() {
        testPartner = new Partner();
        testPartner.setCompanyName("GoogleZH");
        testPartner.setPartnershipLevel(PartnershipLevel.GOLD);
        testPartner.setPartnershipStartDate(LocalDate.now().minusMonths(6));
        testPartner.setPartnershipEndDate(LocalDate.now().plusMonths(6));
        testPartner = partnerRepository.save(testPartner);

        when(securityContextHelper.getCurrentUsername()).thenReturn("test.user");
    }

    @Test
    @DisplayName("should return contacts from User Service when company has PARTNER users")
    @WithMockUser(username = "organizer.user", roles = "ORGANIZER")
    void should_returnContactsFromUserService_when_companyHasPartnerUsers() throws Exception {
        UserResponse john = new UserResponse();
        john.setId("john.doe");
        john.setEmail("john.doe@example.com");
        john.setFirstName("John");
        john.setLastName("Doe");
        john.setProfilePictureUrl(java.net.URI.create("https://example.com/john.jpg"));

        when(userServiceClient.getUsersByCompanyAndRole("GoogleZH", "PARTNER"))
                .thenReturn(List.of(john));

        mockMvc.perform(get("/api/v1/partners/{companyName}/contacts", "GoogleZH"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].username").value("john.doe"))
                .andExpect(jsonPath("$[0].email").value("john.doe@example.com"))
                .andExpect(jsonPath("$[0].firstName").value("John"))
                .andExpect(jsonPath("$[0].lastName").value("Doe"))
                .andExpect(jsonPath("$[0].profilePictureUrl").value("https://example.com/john.jpg"));
    }

    @Test
    @DisplayName("should return empty list when no PARTNER users for company")
    @WithMockUser(username = "organizer.user", roles = "ORGANIZER")
    void should_returnEmptyList_when_noPartnerUsersForCompany() throws Exception {
        when(userServiceClient.getUsersByCompanyAndRole("GoogleZH", "PARTNER"))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/partners/{companyName}/contacts", "GoogleZH"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("should return 404 when partner not found")
    @WithMockUser(username = "organizer.user", roles = "ORGANIZER")
    void should_return404_when_partnerNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/partners/{companyName}/contacts", "NotFoundCo"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Partner not found")));
    }
    // ═══════════════════════════════════════════════════════════════════════════
    // Issue #961 — contact PII is not for every authenticated user
    // ═══════════════════════════════════════════════════════════════════════════
    //
    // This endpoint returns email, firstName, lastName, username and profilePictureUrl for a
    // sponsor's staff. It had no role rule at all and fell through to
    // `.anyRequest().authenticated()`, so any attendee or speaker with a valid token could
    // read any company's contacts.

    @Test
    @DisplayName("#961: should return 403 when an attendee requests partner contacts")
    @WithMockUser(username = "some.attendee", roles = "ATTENDEE")
    void should_return403_when_attendeeRequestsContacts() throws Exception {
        mockMvc.perform(get("/api/v1/partners/{companyName}/contacts", "GoogleZH"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("#961: should return 403 when a partner requests another company's contacts")
    @WithMockUser(username = "partner.other", roles = "PARTNER")
    void should_return403_when_partnerRequestsAnotherCompanysContacts() throws Exception {
        UserResponse other = new UserResponse();
        other.setId("partner.other");
        other.setCompanyId("MicrosoftZH");
        when(userServiceClient.getUserByUsername("partner.other")).thenReturn(other);
        when(securityContextHelper.getCurrentUsername()).thenReturn("partner.other");

        mockMvc.perform(get("/api/v1/partners/{companyName}/contacts", "GoogleZH"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("#961: a partner may still read their OWN company's contacts")
    @WithMockUser(username = "partner.google", roles = "PARTNER")
    void should_allowOwnCompany_when_partnerRequestsOwnContacts() throws Exception {
        UserResponse self = new UserResponse();
        self.setId("partner.google");
        self.setCompanyId("GoogleZH");
        when(userServiceClient.getUserByUsername("partner.google")).thenReturn(self);
        when(securityContextHelper.getCurrentUsername()).thenReturn("partner.google");
        when(userServiceClient.getUsersByCompanyAndRole("GoogleZH", "PARTNER"))
                .thenReturn(List.of(self));

        mockMvc.perform(get("/api/v1/partners/{companyName}/contacts", "GoogleZH"))
                .andExpect(status().isOk());
    }

}
