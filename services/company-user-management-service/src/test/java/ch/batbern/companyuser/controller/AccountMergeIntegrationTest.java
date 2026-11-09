package ch.batbern.companyuser.controller;

import ch.batbern.companyuser.client.EventManagementContract;
import ch.batbern.companyuser.client.HttpEventManagementUserMergeClient;
import ch.batbern.companyuser.config.InMemoryCognito;
import ch.batbern.companyuser.config.TestAwsConfig;
import ch.batbern.companyuser.domain.User;
import ch.batbern.companyuser.repository.UserRepository;
import ch.batbern.companyuser.service.ProfileClaimTokenService;
import ch.batbern.shared.test.contract.ContractServer;
import ch.batbern.shared.test.mail.SesRecorder;
import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.shared.types.Role;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.ResourceAccessException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserStatusType;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.POST;
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
    private static final String PRIVATE = "anna@private.example.com";
    private static final String MERGE = "/internal/users/merge";

    @Autowired
    private SesRecorder sesRecorder;
    @Autowired
    private ProfileClaimTokenService tokenService;
    /** Cognito (the boundary): both people have a login there. */
    @Autowired
    private InMemoryCognito cognito;
    /** The real client; event-management answers through the contract harness at the wire. */
    @Autowired
    private HttpEventManagementUserMergeClient eventManagementClient;

    private ContractServer eventManagement;
    private User a;
    private User b;

    @BeforeEach
    void setUp() {
        sesRecorder.reset();
        cognito.reset();
        eventManagement = EventManagementContract.bindTo(eventManagementClient);
        String subA = cognito.givenLogin(PRIVATE, UserStatusType.CONFIRMED).sub();
        String subB = cognito.givenLogin(WORK, UserStatusType.CONFIRMED).sub();
        a = userRepository.save(User.builder().username("anna.muster.2").email(PRIVATE)
                .firstName("Anna").lastName("Muster").cognitoUserId(subA)
                .roles(new HashSet<>(Set.of(Role.ATTENDEE))).build());
        b = userRepository.save(User.builder().username("anna.muster").email(WORK)
                .firstName("Anna").lastName("Muster").cognitoUserId(subB)
                .roles(new HashSet<>(Set.of(Role.ATTENDEE, Role.SPEAKER))).build());
    }

    @AfterEach
    void tearDown() {
        eventManagement.close();
    }

    private String request(String email) {
        return "{\"email\": \"" + email + "\"}";
    }

    /** A merge request reaching event-management fails the test at {@code verify()}. */
    private void expectNoMerge() {
        eventManagement.expect(POST, MERGE).times(ExpectedCount.never()).respondWithExample();
    }

    private boolean workLoginEnabled() {
        return cognito.login(WORK).orElseThrow().enabled();
    }

    @Test
    @WithMockUser(username = "anna.muster.2", roles = "ATTENDEE")
    @DisplayName("asking for an address that is another login sends a link to that address, nothing else")
    void should_sendLinkToTheOtherAddress_when_mergeRequested() throws Exception {
        expectNoMerge();
        mockMvc.perform(post("/api/v1/users/me/account-merges").contentType(MediaType.APPLICATION_JSON)
                        .content(request("Success+Anna.Work@Simulator.AmazonSES.com")))
                .andExpect(status().isAccepted());

        assertThat(sesRecorder.single().to()).containsExactly(WORK);
        assertThat(sesRecorder.single().html()).contains("/claim-profile?token=");
        assertThat(workLoginEnabled()).isTrue();
        assertThat(userRepository.findByUsername("anna.muster")).isPresent();
        eventManagement.verify();
    }

    @Test
    @WithMockUser(username = "anna.muster.2", roles = "ATTENDEE")
    @DisplayName("an address that is no other login (own, unknown) is 404 and sends nothing")
    void should_return404_when_addressIsNoOtherLogin() throws Exception {
        for (String email : List.of(PRIVATE, "nobody@example.com")) {
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
        // The login is disabled before event-management merges anything.
        eventManagement.expect(POST, MERGE).andExpect(r -> assertThat(workLoginEnabled()).isFalse())
                .respondWithExample();

        mockMvc.perform(post("/api/v1/users/me/profile-claims/confirm").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\": \"" + token + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.merged").value(true));

        eventManagement.verify();
        assertThat(cognito.login(WORK)).isEmpty();
        assertThat(cognito.login(PRIVATE)).hasValueSatisfying(l -> assertThat(l.enabled()).isTrue());
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
        eventManagement.expect(POST, MERGE).andExpect(r -> assertThat(workLoginEnabled()).isFalse())
                .respondWith(r -> {
                    throw new ResourceAccessException("event-management down");
                });
        String token = tokenService.generateToken(a.getId(), b.getId(), b.getEmail());

        mockMvc.perform(post("/api/v1/users/me/profile-claims/confirm").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\": \"" + token + "\"}"))
                .andExpect(status().isBadGateway());

        eventManagement.verify();
        assertThat(cognito.login(WORK)).isPresent();
        assertThat(workLoginEnabled()).isTrue();
        assertThat(userRepository.findByUsername("anna.muster")).isPresent();
    }

    @Test
    @WithMockUser(username = "anna.muster.2", roles = "ATTENDEE")
    @DisplayName("the login cannot be disabled: nothing is merged (502)")
    void should_notMerge_when_loginCannotBeDisabled() throws Exception {
        cognito.failNext(InMemoryCognito.Operation.ADMIN_DISABLE_USER, InMemoryCognito.internalError("x"));
        String token = tokenService.generateToken(a.getId(), b.getId(), b.getEmail());
        expectNoMerge();

        mockMvc.perform(post("/api/v1/users/me/profile-claims/confirm").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\": \"" + token + "\"}"))
                .andExpect(status().is5xxServerError());

        eventManagement.verify();
        assertThat(userRepository.findByUsername("anna.muster")).isPresent();
        assertThat(workLoginEnabled()).isTrue();
    }
}
