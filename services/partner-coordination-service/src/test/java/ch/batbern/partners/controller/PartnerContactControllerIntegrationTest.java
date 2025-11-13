package ch.batbern.partners.controller;

import ch.batbern.partners.client.UserServiceClient;
import ch.batbern.partners.config.TestAwsConfig;
import ch.batbern.partners.config.TestSecurityConfig;
import ch.batbern.partners.domain.ContactRole;
import ch.batbern.partners.domain.Partner;
import ch.batbern.partners.domain.PartnerContact;
import ch.batbern.partners.domain.PartnershipLevel;
import ch.batbern.partners.client.user.dto.UserResponse;
import ch.batbern.partners.exception.UserNotFoundException;
import ch.batbern.partners.repository.PartnerContactRepository;
import ch.batbern.partners.repository.PartnerRepository;
import ch.batbern.partners.security.SecurityContextHelper;
import ch.batbern.shared.test.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for Partner Contact Management API.
 *
 * Tests ADR-003 compliance: stores username (String), enriches with User Service HTTP calls
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

    @Autowired
    private PartnerContactRepository partnerContactRepository;

    @MockBean
    private UserServiceClient userServiceClient;

    @MockBean
    private SecurityContextHelper securityContextHelper;

    @Autowired
    private ObjectMapper objectMapper;

    private Partner testPartner;

    @BeforeEach
    void setUp() {
        // Create test partner
        testPartner = new Partner();
        testPartner.setCompanyName("GoogleZH");
        testPartner.setPartnershipLevel(PartnershipLevel.GOLD);
        testPartner.setPartnershipStartDate(LocalDate.now().minusMonths(6));
        testPartner.setPartnershipEndDate(LocalDate.now().plusMonths(6));
        testPartner = partnerRepository.save(testPartner);

        // Mock SecurityContextHelper to return a test username
        when(securityContextHelper.getCurrentUsername()).thenReturn("test.user");
    }

    @Test
    @DisplayName("should return contacts with User data when HTTP call succeeds")
    void should_returnContactsWithUserData_when_httpCallSucceeds() throws Exception {
        // Given
        PartnerContact contact = new PartnerContact();
        contact.setPartnerId(testPartner.getId());
        contact.setUsername("john.doe");
        contact.setContactRole(ContactRole.PRIMARY);
        contact.setPrimary(true);
        partnerContactRepository.save(contact);

        // Mock User Service response
        UserResponse userProfile = new UserResponse();
        userProfile.setId("john.doe");  // Generated DTO uses setId() for username
        userProfile.setEmail("john.doe@example.com");
        userProfile.setFirstName("John");
        userProfile.setLastName("Doe");
        userProfile.setProfilePictureUrl(java.net.URI.create("https://example.com/john.jpg"));

        when(userServiceClient.getUserProfile("john.doe")).thenReturn(userProfile);

        // When & Then
        mockMvc.perform(get("/api/v1/partners/{companyName}/contacts", "GoogleZH"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].username").value("john.doe"))
                .andExpect(jsonPath("$[0].contactRole").value("PRIMARY"))
                .andExpect(jsonPath("$[0].isPrimary").value(true))
                .andExpect(jsonPath("$[0].email").value("john.doe@example.com"))
                .andExpect(jsonPath("$[0].firstName").value("John"))
                .andExpect(jsonPath("$[0].lastName").value("Doe"))
                .andExpect(jsonPath("$[0].profilePictureUrl").value("https://example.com/john.jpg"));
    }

    @Test
    @DisplayName("should enrich contact with User fields when user service returns data")
    void should_enrichContactWithUserFields_when_userServiceReturnsData() throws Exception {
        // Given
        PartnerContact contact = new PartnerContact();
        contact.setPartnerId(testPartner.getId());
        contact.setUsername("jane.smith");
        contact.setContactRole(ContactRole.BILLING);
        contact.setPrimary(false);
        partnerContactRepository.save(contact);

        // Mock User Service response with all fields
        UserResponse userProfile = new UserResponse();
        userProfile.setId("jane.smith");
        userProfile.setEmail("jane.smith@example.com");
        userProfile.setFirstName("Jane");
        userProfile.setLastName("Smith");
        userProfile.setProfilePictureUrl(java.net.URI.create("https://example.com/jane.jpg"));

        when(userServiceClient.getUserProfile("jane.smith")).thenReturn(userProfile);

        // When & Then - verify HTTP enrichment includes all User fields
        mockMvc.perform(get("/api/v1/partners/{companyName}/contacts", "GoogleZH"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].email").exists())
                .andExpect(jsonPath("$[0].firstName").exists())
                .andExpect(jsonPath("$[0].lastName").exists())
                .andExpect(jsonPath("$[0].profilePictureUrl").exists());
    }

    @Test
    @DisplayName("should add contact by username when valid username provided (ADR-003)")
    void should_addContactByUsername_when_validUsernameProvided() throws Exception {
        // Given
        Map<String, Object> request = new HashMap<>();
        request.put("username", "bob.johnson");
        request.put("contactRole", "TECHNICAL");
        request.put("isPrimary", true);

        // Mock User Service validation
        UserResponse userProfile = new UserResponse();
        userProfile.setId("bob.johnson");
        userProfile.setEmail("bob.johnson@example.com");
        userProfile.setFirstName("Bob");
        userProfile.setLastName("Johnson");

        when(userServiceClient.getUserProfile("bob.johnson")).thenReturn(userProfile);

        // When & Then
        mockMvc.perform(post("/api/v1/partners/{companyName}/contacts", "GoogleZH")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("bob.johnson"))
                .andExpect(jsonPath("$.contactRole").value("TECHNICAL"))
                .andExpect(jsonPath("$.isPrimary").value(true))
                .andExpect(jsonPath("$.email").value("bob.johnson@example.com"));
    }

    @Test
    @DisplayName("should return 404 when user service returns 404")
    void should_return404_when_userServiceReturns404() throws Exception {
        // Given
        Map<String, Object> request = new HashMap<>();
        request.put("username", "nonexistent.user");
        request.put("contactRole", "PRIMARY");
        request.put("isPrimary", true);

        // Mock User Service 404 response
        when(userServiceClient.getUserProfile("nonexistent.user"))
                .thenThrow(new UserNotFoundException("User not found: nonexistent.user"));

        // When & Then
        mockMvc.perform(post("/api/v1/partners/{companyName}/contacts", "GoogleZH")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(containsString("User not found")));
    }

    @Test
    @DisplayName("should remove contact when delete endpoint called")
    void should_removeContact_when_deleteEndpointCalled() throws Exception {
        // Given - create 2 contacts, one primary and one non-primary
        PartnerContact primaryContact = new PartnerContact();
        primaryContact.setPartnerId(testPartner.getId());
        primaryContact.setUsername("primary.user");
        primaryContact.setContactRole(ContactRole.PRIMARY);
        primaryContact.setPrimary(true);
        partnerContactRepository.save(primaryContact);

        PartnerContact secondaryContact = new PartnerContact();
        secondaryContact.setPartnerId(testPartner.getId());
        secondaryContact.setUsername("secondary.user");
        secondaryContact.setContactRole(ContactRole.BILLING);
        secondaryContact.setPrimary(false);
        partnerContactRepository.save(secondaryContact);

        // When & Then - remove non-primary contact should succeed
        mockMvc.perform(delete("/api/v1/partners/{companyName}/contacts/{username}",
                        "GoogleZH", "secondary.user"))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("should return 400 when removing last primary contact")
    void should_return400_when_removingLastPrimaryContact() throws Exception {
        // Given - create only one primary contact
        PartnerContact primaryContact = new PartnerContact();
        primaryContact.setPartnerId(testPartner.getId());
        primaryContact.setUsername("only.primary");
        primaryContact.setContactRole(ContactRole.PRIMARY);
        primaryContact.setPrimary(true);
        partnerContactRepository.save(primaryContact);

        // When & Then - removing last primary contact should fail
        mockMvc.perform(delete("/api/v1/partners/{companyName}/contacts/{username}",
                        "GoogleZH", "only.primary"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("at least one primary contact")));
    }

    @Test
    @DisplayName("should return 409 when adding duplicate contact")
    void should_return409_when_addingDuplicateContact() throws Exception {
        // Given - create existing contact
        PartnerContact existingContact = new PartnerContact();
        existingContact.setPartnerId(testPartner.getId());
        existingContact.setUsername("duplicate.user");
        existingContact.setContactRole(ContactRole.PRIMARY);
        existingContact.setPrimary(true);
        partnerContactRepository.save(existingContact);

        // Mock User Service response
        UserResponse userProfile = new UserResponse();
        userProfile.setId("duplicate.user");
        userProfile.setEmail("duplicate.user@example.com");
        when(userServiceClient.getUserProfile("duplicate.user")).thenReturn(userProfile);

        // When - try to add same contact again
        Map<String, Object> request = new HashMap<>();
        request.put("username", "duplicate.user");
        request.put("contactRole", "BILLING");
        request.put("isPrimary", false);

        // Then - should return 409 Conflict
        mockMvc.perform(post("/api/v1/partners/{companyName}/contacts", "GoogleZH")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("already exists")));
    }

    @Test
    @DisplayName("should return 404 when partner not found")
    void should_return404_when_partnerNotFound() throws Exception {
        // When & Then - list contacts for non-existent partner
        mockMvc.perform(get("/api/v1/partners/{companyName}/contacts", "NotFoundCo"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(containsString("Partner not found")));
    }

    @Test
    @DisplayName("should return 404 when contact not found for deletion")
    void should_return404_when_contactNotFoundForDeletion() throws Exception {
        // When & Then - delete non-existent contact
        mockMvc.perform(delete("/api/v1/partners/{companyName}/contacts/{username}",
                        "GoogleZH", "nonexistent.user"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(containsString("Contact not found")));
    }
}
