package ch.batbern.companyuser.controller;

import ch.batbern.companyuser.config.TestAwsConfig;
import ch.batbern.companyuser.domain.Role;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Public organizer list for the About page ({@code GET /api/v1/public/organizers}, anonymous).
 * GHSA-qhqv: the page shows names, bios and pictures; the organizers' own email addresses were in
 * the response too and harvestable. The page contacts info@batbern.ch instead.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(TestAwsConfig.class)
@DisplayName("PublicOrganizerController Integration Tests")
class PublicOrganizerControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        userRepository.save(User.builder()
                .username("anna.organizer")
                .email("anna@example.com")
                .firstName("Anna")
                .lastName("Organizer")
                .cognitoUserId("cognito-anna.organizer")
                .roles(new HashSet<>(Set.of(Role.ORGANIZER)))
                .build());
    }

    @Test
    @DisplayName("should_listOrganizersWithoutEmail_when_anonymousVisitsAboutPage")
    void should_listOrganizersWithoutEmail_when_anonymousVisitsAboutPage() throws Exception {
        mockMvc.perform(get("/api/v1/public/organizers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("anna.organizer"))
                .andExpect(jsonPath("$[0].firstName").value("Anna"))
                .andExpect(jsonPath("$[0].email").doesNotExist());
    }
}
