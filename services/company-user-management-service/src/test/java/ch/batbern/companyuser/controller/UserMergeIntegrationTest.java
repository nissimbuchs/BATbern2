package ch.batbern.companyuser.controller;

import ch.batbern.companyuser.client.EventManagementUserMergeClient;
import ch.batbern.companyuser.config.TestAwsConfig;
import ch.batbern.shared.types.Role;
import ch.batbern.companyuser.domain.User;
import ch.batbern.companyuser.domain.UserAdditionalEmail;
import ch.batbern.companyuser.repository.UserAdditionalEmailRepository;
import ch.batbern.companyuser.repository.UserRepository;
import ch.batbern.companyuser.service.ProfileClaimTokenService;
import ch.batbern.shared.service.EmailService;
import ch.batbern.shared.test.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * #1183 C (organizer merges two profiles) and D (a person claims an earlier profile under their
 * name) against real PostgreSQL. event-management's part ({@code /internal/users/merge}) is mocked
 * here and tested in its own service ({@code InternalUserMergeIntegrationTest}).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(TestAwsConfig.class)
@DisplayName("Profile merge (#1183 C/D)")
class UserMergeIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private UserAdditionalEmailRepository additionalEmailRepository;
    @Autowired
    private EmailService emailService; // Mockito mock from TestAwsConfig
    @Autowired
    private ProfileClaimTokenService claimTokenService;
    @MockitoBean
    private EventManagementUserMergeClient eventManagement;
    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;

    private User login;
    private User guest;

    @BeforeEach
    void setUp() {
        Mockito.reset(emailService);
        when(emailService.replaceVariables(anyString(), any())).thenCallRealMethod();
        when(eventManagement.preview(anyString(), anyString())).thenReturn(new EventManagementUserMergeClient.Preview(
                Map.of("registrations.attendee_username", 2), Map.of("registrations", 1), List.of()));
        when(eventManagement.merge(any())).thenReturn(new EventManagementUserMergeClient.Result(
                Map.of("registrations.attendee_username", 2), Map.of("registrations", 1)));

        userRepository.save(User.builder().username("orga.nizer").email("orga@example.com")
                .firstName("Orga").lastName("Nizer").cognitoUserId("cog-orga")
                .roles(new HashSet<>(Set.of(Role.ORGANIZER))).build());
        login = userRepository.save(User.builder().username("anna.muster.2").email("anna.private@example.com")
                .firstName("Anna").lastName("Muster").cognitoUserId("cog-anna")
                .roles(new HashSet<>(Set.of(Role.ATTENDEE))).build());
        guest = userRepository.save(User.builder().username("anna.muster").email("anna.work@example.com")
                .firstName("ANNA").lastName("muster").companyId("acme")
                .roles(new HashSet<>(Set.of(Role.ATTENDEE, Role.SPEAKER))).build());
        guest.addAdditionalEmail(UserAdditionalEmail.builder().email("anna.old@example.com")
                .verifiedAt(Instant.now()).createdAt(Instant.now()).build());
        userRepository.saveAndFlush(guest);
    }

    // ---------------------------------------------------------------- C: organizer merge

    @Test
    @WithMockUser(username = "orga.nizer", roles = "ORGANIZER")
    @DisplayName("C: preview shows both profiles and event-management's counts; mergeable")
    void preview_showsBothSides() throws Exception {
        mockMvc.perform(get("/api/v1/users/anna.muster.2/merge-preview").param("source", "anna.muster"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.target.username").value("anna.muster.2"))
                .andExpect(jsonPath("$.target.hasLogin").value(true))
                .andExpect(jsonPath("$.source.hasLogin").value(false))
                .andExpect(jsonPath("$.source.additionalEmails[0]").value("anna.old@example.com"))
                .andExpect(jsonPath("$.moves['registrations.attendee_username']").value(2))
                .andExpect(jsonPath("$.conflicts.registrations").value(1))
                .andExpect(jsonPath("$.canMerge").value(true));
    }

    @Test
    @WithMockUser(username = "orga.nizer", roles = "ORGANIZER")
    @DisplayName("C: merge moves roles and addresses, deletes the source, hands event-management a snapshot")
    void merge_absorbsTheGuestProfile() throws Exception {
        mockMvc.perform(post("/api/v1/users/anna.muster.2/merge").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceUsername\": \"anna.muster\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.moved['registrations.attendee_username']").value(2))
                .andExpect(jsonPath("$.removed.registrations").value(1));

        entityManager.flush();
        entityManager.clear();
        assertThat(userRepository.findByUsername("anna.muster")).isEmpty();
        User target = userRepository.findByUsername("anna.muster.2").orElseThrow();
        assertThat(target.getRoles()).containsExactlyInAnyOrder(Role.ATTENDEE, Role.SPEAKER);
        assertThat(target.getCompanyId()).isEqualTo("acme");
        assertThat(target.getCognitoUserId()).isEqualTo("cog-anna");
        assertThat(additionalEmailRepository.findAll())
                .filteredOn(e -> e.getUser().getUsername().equals("anna.muster.2"))
                .extracting(UserAdditionalEmail::getEmail)
                .containsExactlyInAnyOrder("anna.work@example.com", "anna.old@example.com");
        assertThat(additionalEmailRepository.findAll())
                .filteredOn(e -> e.getEmail().equals("anna.work@example.com"))
                .allSatisfy(e -> assertThat(e.getVerifiedAt()).isNotNull());

        ArgumentCaptor<EventManagementUserMergeClient.MergeCall> call =
                ArgumentCaptor.forClass(EventManagementUserMergeClient.MergeCall.class);
        verify(eventManagement).merge(call.capture());
        assertThat(call.getValue().sourceUsername()).isEqualTo("anna.muster");
        assertThat(call.getValue().targetUsername()).isEqualTo("anna.muster.2");
        assertThat(call.getValue().performedBy()).isEqualTo("orga.nizer");
        assertThat(call.getValue().trigger()).isEqualTo("ORGANIZER");
        assertThat(call.getValue().sourceProfile().toString()).contains("anna.work@example.com")
                .contains("SPEAKER").contains("anna.old@example.com");
    }

    @Test
    @WithMockUser(username = "orga.nizer", roles = "ORGANIZER")
    @DisplayName("C: an unknown profile is 404 with a plain message (not wrapped twice)")
    void preview_unknownSource_is404() throws Exception {
        mockMvc.perform(get("/api/v1/users/anna.muster.2/merge-preview").param("source", "nobody.here"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("User not found: nobody.here"));
    }

    @Test
    @WithMockUser(username = "orga.nizer", roles = "ORGANIZER")
    @DisplayName("C: the source has a login: not mergeable in this direction; merge refused, nothing touched")
    void merge_sourceWithLogin_isRefused() throws Exception {
        mockMvc.perform(get("/api/v1/users/anna.muster/merge-preview").param("source", "anna.muster.2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canMerge").value(false))
                .andExpect(jsonPath("$.reasons[0]").value("SOURCE_HAS_LOGIN"));

        mockMvc.perform(post("/api/v1/users/anna.muster/merge").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceUsername\": \"anna.muster.2\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("MERGE_NOT_ALLOWED"));
        verify(eventManagement, never()).merge(any());
        assertThat(userRepository.findByUsername("anna.muster.2")).isPresent();
    }

    @Test
    @WithMockUser(username = "orga.nizer", roles = "ORGANIZER")
    @DisplayName("C: event-management fails: the source profile stays, 502")
    void merge_eventManagementDown_keepsSource() throws Exception {
        when(eventManagement.merge(any())).thenThrow(new EventManagementUserMergeClient.MergeCallFailedException(
                "event-management answered 500"));

        mockMvc.perform(post("/api/v1/users/anna.muster.2/merge").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceUsername\": \"anna.muster\"}"))
                .andExpect(status().isBadGateway());
        assertThat(userRepository.findByUsername("anna.muster")).isPresent();
    }

    @Test
    @WithMockUser(username = "anna.muster.2", roles = "ATTENDEE")
    @DisplayName("C: only organizers merge")
    void merge_requiresOrganizer() throws Exception {
        mockMvc.perform(post("/api/v1/users/anna.muster.2/merge").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceUsername\": \"anna.muster\"}"))
                .andExpect(status().isForbidden());
        verify(eventManagement, never()).merge(any());
    }

    // ---------------------------------------------------------------- D: claim an earlier profile

    @Test
    @WithMockUser(username = "anna.muster.2", roles = "ATTENDEE")
    @DisplayName("D: candidates are profiles without login under the same name, address masked")
    void claims_listsNamesakesWithoutLogin() throws Exception {
        userRepository.save(User.builder().username("anna.muster.3").email("anna.third@example.com")
                .firstName("Anna").lastName("Muster").cognitoUserId("cog-anna-3").build());

        mockMvc.perform(get("/api/v1/users/me/profile-claims"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].candidateId").value(guest.getId().toString()))
                .andExpect(jsonPath("$[0].maskedEmail").value("a***@example.com"))
                .andExpect(jsonPath("$[0].companyName").value("acme"));
    }

    @Test
    @WithMockUser(username = "anna.muster.2", roles = "ATTENDEE")
    @DisplayName("D: a claim sends a link to the earlier profile's address, never to the caller")
    void claim_sendsLinkToTheOldAddress() throws Exception {
        mockMvc.perform(post("/api/v1/users/me/profile-claims").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"candidateId\": \"" + guest.getId() + "\"}"))
                .andExpect(status().isAccepted());

        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendHtmlEmail(eq("anna.work@example.com"), anyString(), html.capture());
        assertThat(html.getValue()).contains("/claim-profile?token=");
    }

    @Test
    @WithMockUser(username = "anna.muster.2", roles = "ATTENDEE")
    @DisplayName("D: a profile that is not a candidate cannot be claimed (404, no mail)")
    void claim_nonCandidate_is404() throws Exception {
        User stranger = userRepository.save(User.builder().username("bea.other").email("bea@example.com")
                .firstName("Bea").lastName("Other").build());

        mockMvc.perform(post("/api/v1/users/me/profile-claims").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"candidateId\": \"" + stranger.getId() + "\"}"))
                .andExpect(status().isNotFound());
        verify(emailService, never()).sendHtmlEmail(anyString(), anyString(), anyString());
    }

    @Test
    @WithMockUser(username = "anna.muster.2", roles = "ATTENDEE")
    @DisplayName("D: confirming the link while logged in as the claimer merges, as SELF_CLAIM")
    void confirm_merges() throws Exception {
        String token = claimTokenService.generateToken(login.getId(), guest.getId(), guest.getEmail());

        mockMvc.perform(post("/api/v1/users/me/profile-claims/confirm").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\": \"" + token + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.merged").value(true))
                .andExpect(jsonPath("$.alreadyMerged").value(false));

        entityManager.flush();
        assertThat(userRepository.findByUsername("anna.muster")).isEmpty();
        ArgumentCaptor<EventManagementUserMergeClient.MergeCall> call =
                ArgumentCaptor.forClass(EventManagementUserMergeClient.MergeCall.class);
        verify(eventManagement).merge(call.capture());
        assertThat(call.getValue().trigger()).isEqualTo("SELF_CLAIM");
        assertThat(call.getValue().performedBy()).isEqualTo("anna.muster.2");

        mockMvc.perform(post("/api/v1/users/me/profile-claims/confirm").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\": \"" + token + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.merged").value(false))
                .andExpect(jsonPath("$.alreadyMerged").value(true));
    }

    @Test
    @WithMockUser(username = "orga.nizer", roles = "ATTENDEE")
    @DisplayName("D: a link opened under another login is refused (403) and merges nothing")
    void confirm_otherAccount_isRefused() throws Exception {
        String token = claimTokenService.generateToken(login.getId(), guest.getId(), guest.getEmail());

        mockMvc.perform(post("/api/v1/users/me/profile-claims/confirm").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\": \"" + token + "\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("CLAIM_OTHER_ACCOUNT"));
        verify(eventManagement, never()).merge(any());
    }

    @Test
    @WithMockUser(username = "anna.muster.2", roles = "ATTENDEE")
    @DisplayName("D: a forged token is 400 TOKEN_INVALID")
    void confirm_forgedToken_is400() throws Exception {
        mockMvc.perform(post("/api/v1/users/me/profile-claims/confirm").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\": \"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isBadRequest());
    }
}
