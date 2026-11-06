package ch.batbern.companyuser.controller;

import java.time.Instant;
import ch.batbern.companyuser.domain.UserAdditionalEmail;
import ch.batbern.companyuser.repository.UserAdditionalEmailRepository;
import ch.batbern.companyuser.config.TestAwsConfig;
import ch.batbern.shared.types.Role;
import ch.batbern.companyuser.domain.User;
import ch.batbern.companyuser.repository.UserRepository;
import ch.batbern.shared.test.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Internal user API for the other services ({@code /internal/users}). It is not routed by the API
 * gateway, so callers are services inside the VPC and carry no user JWT. The data must not depend
 * on who triggered the lookup: EMS and PCS cache it per username.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(TestAwsConfig.class)
@DisplayName("InternalUserController Integration Tests")
class InternalUserControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        userRepository.save(user("anna.organizer", "anna@example.com", "AcmeAG", Role.ORGANIZER));
        userRepository.save(user("paul.partner", "paul@example.com", "AcmeAG", Role.PARTNER));
        userRepository.save(user("petra.partner", "petra@example.com", "OtherAG", Role.PARTNER));
        userRepository.save(user("sam.speaker", "sam@example.com", "AcmeAG", Role.SPEAKER));
    }

    private static User user(String username, String email, String company, Role role) {
        return User.builder()
                .username(username)
                .email(email)
                .firstName(username.split("\\.")[0])
                .lastName(username.split("\\.")[1])
                .cognitoUserId("cognito-" + username)
                .companyId(company)
                .roles(new HashSet<>(Set.of(role)))
                .build();
    }

    @Test
    @DisplayName("should_returnFullUser_when_serviceLooksUpUsernameWithoutJwt")
    void should_returnFullUser_when_serviceLooksUpUsernameWithoutJwt() throws Exception {
        mockMvc.perform(get("/internal/users/{username}", "paul.partner"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("paul.partner"))
                .andExpect(jsonPath("$.email").value("paul@example.com"))
                .andExpect(jsonPath("$.companyId").value("AcmeAG"))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    @DisplayName("should_expandPreferences_when_includeRequested")
    void should_expandPreferences_when_includeRequested() throws Exception {
        mockMvc.perform(get("/internal/users/{username}", "paul.partner").param("include", "preferences"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preferences").exists());
    }

    @Test
    @DisplayName("should_return404_when_internalUserUnknown")
    void should_return404_when_internalUserUnknown() throws Exception {
        mockMvc.perform(get("/internal/users/{username}", "nobody.here"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("should_listUsersByRole_when_serviceRequestsRole")
    void should_listUsersByRole_when_serviceRequestsRole() throws Exception {
        mockMvc.perform(get("/internal/users").param("role", "PARTNER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[*].id", containsInAnyOrder("paul.partner", "petra.partner")))
                .andExpect(jsonPath("$.data[*].email", containsInAnyOrder("paul@example.com", "petra@example.com")))
                .andExpect(jsonPath("$.pagination.page").value(1));
    }

    @Test
    @DisplayName("should_listUsersByCompanyAndRole_when_serviceRequestsBoth")
    void should_listUsersByCompanyAndRole_when_serviceRequestsBoth() throws Exception {
        mockMvc.perform(get("/internal/users").param("company", "AcmeAG").param("role", "PARTNER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].id").value("paul.partner"));
    }

    @Test
    @DisplayName("should_returnEmptyList_when_noUsersMatchCompanyAndRole")
    void should_returnEmptyList_when_noUsersMatchCompanyAndRole() throws Exception {
        mockMvc.perform(get("/internal/users").param("company", "NoSuchAG").param("role", "PARTNER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    @DisplayName("should_pageThroughUsers_when_limitAndPageGiven")
    void should_pageThroughUsers_when_limitAndPageGiven() throws Exception {
        mockMvc.perform(get("/internal/users").param("page", "2").param("limit", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.pagination.page").value(2))
                .andExpect(jsonPath("$.pagination.limit").value(3))
                .andExpect(jsonPath("$.pagination.totalItems").value(4));
    }

    // #1183: who owns an address. The by-email deregistration finds a person's registrations on all
    // their profiles. Primary address or VERIFIED additional address only; an unverified one is a claim.
    @Autowired
    private UserAdditionalEmailRepository additionalEmailRepository;

    private void additional(String username, String email, boolean verified) {
        User owner = userRepository.findByUsername(username).orElseThrow();
        additionalEmailRepository.save(UserAdditionalEmail.builder()
                .user(owner).email(email).createdAt(Instant.now())
                .verifiedAt(verified ? Instant.now() : null).build());
    }

    @Test
    @DisplayName("#1183: the owner of a primary address, and of a verified additional address")
    void should_returnOwner_when_addressIsPrimaryOrVerifiedAdditional() throws Exception {
        // A DB trigger keeps an address from being both a primary and an additional one, so an
        // address has at most one owner.
        additional("anna.organizer", "Anna.Work@example.com", true);

        mockMvc.perform(get("/internal/email-owners").param("email", "paul@EXAMPLE.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0]").value("paul.partner"));
        mockMvc.perform(get("/internal/email-owners").param("email", "anna.work@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0]").value("anna.organizer"));
    }

    @Test
    @DisplayName("#1183: an unverified additional address owns nothing; an unknown address gives []")
    void should_ignoreUnverified_and_returnEmpty_when_unknown() throws Exception {
        additional("anna.organizer", "claim@example.com", false);

        mockMvc.perform(get("/internal/email-owners").param("email", "claim@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/internal/email-owners").param("email", "nobody@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
