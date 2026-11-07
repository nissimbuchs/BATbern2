package ch.batbern.companyuser.controller;

import ch.batbern.companyuser.client.EventManagementUserMergeClient;
import ch.batbern.companyuser.config.TestAwsConfig;
import ch.batbern.companyuser.domain.User;
import ch.batbern.companyuser.domain.UserAdditionalEmail;
import ch.batbern.companyuser.exception.EmailHasOtherLoginException;
import ch.batbern.companyuser.repository.UserRepository;
import ch.batbern.companyuser.service.CognitoIntegrationService;
import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.shared.types.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * #1244 step 2 — "Als Hauptadresse verwenden": a confirmed additional address becomes the primary
 * (login) address. Cognito moves the login first; then the profile swaps the two addresses, the
 * old primary stays as a confirmed additional address; event-management moves upcoming
 * registrations made under the old address.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(TestAwsConfig.class)
@DisplayName("#1244 — make an additional address the primary one")
class PrimaryEmailIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @MockitoBean
    private CognitoIntegrationService cognito;
    @MockitoBean
    private EventManagementUserMergeClient eventManagement;

    private User anna;

    @BeforeEach
    void setUp() {
        anna = userRepository.save(User.builder().username("anna.muster").email("anna@work.example.com")
                .firstName("Anna").lastName("Muster").cognitoUserId("cog-anna")
                .roles(new HashSet<>(Set.of(Role.ATTENDEE))).build());
        anna.addAdditionalEmail(UserAdditionalEmail.builder().email("anna@private.example.com")
                .verifiedAt(Instant.now()).createdAt(Instant.now()).build());
        anna.addAdditionalEmail(UserAdditionalEmail.builder().email("anna@pending.example.com")
                .createdAt(Instant.now()).build());
        userRepository.saveAndFlush(anna);
    }

    @Test
    @WithMockUser(username = "anna.muster")
    @DisplayName("a confirmed address becomes the primary; the old primary stays as a confirmed address")
    void should_swapAddresses_when_confirmedAddressMadePrimary() throws Exception {
        mockMvc.perform(post("/api/v1/users/me/additional-emails/Anna@Private.example.com/make-primary"))
                .andExpect(status().isNoContent());

        verify(cognito).changeLoginEmail("anna@work.example.com", "anna@private.example.com");
        verify(eventManagement).primaryEmailChanged("anna.muster", "anna@work.example.com",
                "anna@private.example.com");

        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("anna@private.example.com"))
                .andExpect(jsonPath("$.additionalEmails[?(@.email == 'anna@work.example.com')].verifiedAt")
                        .exists())
                .andExpect(jsonPath("$.additionalEmails[?(@.email == 'anna@private.example.com')]").isEmpty());
    }

    @Test
    @WithMockUser(username = "anna.muster")
    @DisplayName("an unconfirmed address cannot become the primary (409 NOT_VERIFIED), Cognito untouched")
    void should_return409_when_addressUnconfirmed() throws Exception {
        mockMvc.perform(post("/api/v1/users/me/additional-emails/anna@pending.example.com/make-primary"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("NOT_VERIFIED"));
        verifyNoInteractions(cognito, eventManagement);
    }

    @Test
    @WithMockUser(username = "anna.muster")
    @DisplayName("the address is another login's → 409 EMAIL_HAS_OTHER_LOGIN, profile unchanged")
    void should_return409_when_addressIsAnotherLogin() throws Exception {
        doThrow(new EmailHasOtherLoginException("a***@private.example.com"))
                .when(cognito).changeLoginEmail(anyString(), anyString());

        mockMvc.perform(post("/api/v1/users/me/additional-emails/anna@private.example.com/make-primary"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("EMAIL_HAS_OTHER_LOGIN"));

        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(jsonPath("$.email").value("anna@work.example.com"));
        verify(eventManagement, never()).primaryEmailChanged(anyString(), anyString(), anyString());
    }

    @Test
    @WithMockUser(username = "anna.muster")
    @DisplayName("someone else's or an unknown address is 404")
    void should_return404_when_notOwnAddress() throws Exception {
        mockMvc.perform(post("/api/v1/users/me/additional-emails/nobody@example.com/make-primary"))
                .andExpect(status().isNotFound());
        verifyNoInteractions(cognito);
    }

    @Test
    @DisplayName("needs a login (401)")
    void should_return401_when_anonymous() throws Exception {
        mockMvc.perform(post("/api/v1/users/me/additional-emails/anna@private.example.com/make-primary"))
                .andExpect(status().isUnauthorized());
    }
}
