package ch.batbern.companyuser.controller;

import ch.batbern.companyuser.client.EventManagementUserMergeClient;
import ch.batbern.companyuser.config.TestAwsConfig;
import ch.batbern.companyuser.domain.User;
import ch.batbern.companyuser.exception.CognitoOperationException;
import ch.batbern.companyuser.repository.UserRepository;
import ch.batbern.companyuser.service.CognitoIntegrationService;
import ch.batbern.companyuser.service.ProfileClaimTokenService;
import ch.batbern.shared.test.mail.SesRecorder;
import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.shared.types.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * #1244 step 3 (owner decision 2026-11-07) — two accounts of one person, both with a login (first
 * the company address, later the private one). Logged in as A, the person asks to merge the account
 * of another address B; a link goes to B. Confirming it under A's login proves both mailboxes: B's
 * history moves to A, B's address becomes a confirmed address of A, and B's login is removed
 * (disabled before the merge, deleted after it; if the delete fails it stays disabled). Organizers
 * do not get this.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(TestAwsConfig.class)
@DisplayName("#1244 — merge a second account that has its own login")
class AccountMergeIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    /** The other login's address receives the claim link: SES mailbox simulator, never delivered. */
    private static final String WORK = "success+anna.work@simulator.amazonses.com";

    @Autowired
    private SesRecorder sesRecorder;
    @Autowired
    private ProfileClaimTokenService tokenService;
    @MockitoBean
    private CognitoIntegrationService cognito;
    @MockitoBean
    private EventManagementUserMergeClient eventManagement;

    private User a;
    private User b;

    @BeforeEach
    void setUp() {
        sesRecorder.reset();
        when(eventManagement.merge(any())).thenReturn(new EventManagementUserMergeClient.Result(
                Map.of("registrations.attendee_username", 3), Map.of()));
        a = userRepository.save(User.builder().username("anna.muster.2").email("anna@private.example.com")
                .firstName("Anna").lastName("Muster").cognitoUserId("cog-a")
                .roles(new HashSet<>(Set.of(Role.ATTENDEE))).build());
        b = userRepository.save(User.builder().username("anna.muster").email(WORK)
                .firstName("Anna").lastName("Muster").cognitoUserId("cog-b")
                .roles(new HashSet<>(Set.of(Role.ATTENDEE, Role.SPEAKER))).build());
    }

    private String request(String email) {
        return "{\"email\": \"" + email + "\"}";
    }

    @Test
    @WithMockUser(username = "anna.muster.2", roles = "ATTENDEE")
    @DisplayName("asking for an address that is another login sends a link to that address, nothing else")
    void should_sendLinkToTheOtherAddress_when_mergeRequested() throws Exception {
        mockMvc.perform(post("/api/v1/users/me/account-merges").contentType(MediaType.APPLICATION_JSON)
                        .content(request("Success+Anna.Work@Simulator.AmazonSES.com")))
                .andExpect(status().isAccepted());

        assertThat(sesRecorder.single().to()).containsExactly(WORK);
        assertThat(sesRecorder.single().html()).contains("/claim-profile?token=");
        verify(cognito, never()).disableLogin(anyString());
        assertThat(userRepository.findByUsername("anna.muster")).isPresent();
    }

    @Test
    @WithMockUser(username = "anna.muster.2", roles = "ATTENDEE")
    @DisplayName("an address that is no other login (own, unknown) is 404 and sends nothing")
    void should_return404_when_addressIsNoOtherLogin() throws Exception {
        for (String email : List.of("anna@private.example.com", "nobody@example.com")) {
            mockMvc.perform(post("/api/v1/users/me/account-merges").contentType(MediaType.APPLICATION_JSON)
                            .content(request(email)))
                    .andExpect(status().isNotFound());
        }
        assertThat(sesRecorder.mails()).isEmpty();
    }

    @Test
    @WithMockUser(username = "anna.muster.2", roles = "ATTENDEE")
    @DisplayName("confirming merges B into A: login disabled first, B's address kept as confirmed, login deleted")
    void should_mergeAndRemoveTheOtherLogin_when_confirmed() throws Exception {
        String token = tokenService.generateToken(a.getId(), b.getId(), b.getEmail());

        mockMvc.perform(post("/api/v1/users/me/profile-claims/confirm").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\": \"" + token + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.merged").value(true));

        InOrder order = inOrder(cognito, eventManagement);
        order.verify(cognito).disableLogin(WORK);
        order.verify(eventManagement).merge(any());
        order.verify(cognito).deleteLogin(WORK);
        assertThat(userRepository.findByUsername("anna.muster")).isEmpty();
        User merged = userRepository.findByUsername("anna.muster.2").orElseThrow();
        assertThat(merged.getRoles()).contains(Role.SPEAKER);
        assertThat(merged.getAdditionalEmails())
                .anySatisfy(e -> {
                    assertThat(e.getEmail()).isEqualTo(WORK);
                    assertThat(e.getVerifiedAt()).isNotNull();
                });
    }

    @Test
    @WithMockUser(username = "anna.muster.2", roles = "ATTENDEE")
    @DisplayName("the merge fails: the other login is enabled again and nothing is deleted")
    void should_enableTheLoginAgain_when_mergeFails() throws Exception {
        when(eventManagement.merge(any())).thenThrow(
                new EventManagementUserMergeClient.MergeCallFailedException("down"));
        String token = tokenService.generateToken(a.getId(), b.getId(), b.getEmail());

        mockMvc.perform(post("/api/v1/users/me/profile-claims/confirm").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\": \"" + token + "\"}"))
                .andExpect(status().isBadGateway());

        verify(cognito).disableLogin(WORK);
        verify(cognito).enableLogin(WORK);
        verify(cognito, never()).deleteLogin(anyString());
    }

    @Test
    @WithMockUser(username = "anna.muster.2", roles = "ATTENDEE")
    @DisplayName("the login cannot be disabled: nothing is merged (502)")
    void should_notMerge_when_loginCannotBeDisabled() throws Exception {
        doThrow(new CognitoOperationException("adminDisableUser", "s***@simulator.amazonses.com",
                new RuntimeException("x"))).when(cognito).disableLogin(anyString());
        String token = tokenService.generateToken(a.getId(), b.getId(), b.getEmail());

        mockMvc.perform(post("/api/v1/users/me/profile-claims/confirm").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\": \"" + token + "\"}"))
                .andExpect(status().is5xxServerError());

        verify(eventManagement, never()).merge(any());
        assertThat(userRepository.findByUsername("anna.muster")).isPresent();
    }
}
