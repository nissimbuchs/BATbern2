package ch.batbern.companyuser.controller;

import ch.batbern.companyuser.client.EventManagementMailCenterClient;
import ch.batbern.companyuser.client.EventManagementUserMergeClient;
import ch.batbern.companyuser.config.TestAwsConfig;
import ch.batbern.companyuser.domain.ProfileMergeRequest;
import ch.batbern.companyuser.domain.User;
import ch.batbern.companyuser.domain.UserAdditionalEmail;
import ch.batbern.companyuser.repository.ProfileMergeRequestRepository;
import ch.batbern.companyuser.repository.UserRepository;
import ch.batbern.shared.service.EmailService;
import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.shared.types.Role;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * #1237: claiming an earlier profile without access to its old address. The person asks, any
 * organizer approves or declines in the Mail Center. event-management's parts (the merge and the
 * conversation) are mocked here and tested in their own service.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(TestAwsConfig.class)
@DisplayName("Profile merge request (#1237)")
class ProfileMergeRequestIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ProfileMergeRequestRepository requestRepository;
    @Autowired
    private EmailService emailService; // Mockito mock from TestAwsConfig
    @Autowired
    private ObjectMapper objectMapper;
    @MockitoBean
    private EventManagementUserMergeClient eventManagement;
    @MockitoBean
    private EventManagementMailCenterClient mailCenter;
    @PersistenceContext
    private EntityManager entityManager;

    private User login;
    private User guest;

    @BeforeEach
    void setUp() {
        Mockito.reset(emailService);
        when(emailService.replaceVariables(anyString(), any())).thenCallRealMethod();
        when(eventManagement.preview(anyString(), anyString())).thenReturn(new EventManagementUserMergeClient.Preview(
                Map.of("registrations.attendee_username", 3), Map.of(), List.of()));
        when(eventManagement.merge(any())).thenReturn(new EventManagementUserMergeClient.Result(
                Map.of("registrations.attendee_username", 3), Map.of()));
        userRepository.save(User.builder().username("orga.nizer").email("orga@example.com")
                .firstName("Orga").lastName("Nizer").cognitoUserId("cog-orga")
                .roles(new HashSet<>(Set.of(Role.ORGANIZER))).build());
        login = userRepository.save(User.builder().username("anna.muster.2").email("anna.private@example.com")
                .firstName("Anna").lastName("Muster").cognitoUserId("cog-anna")
                .roles(new HashSet<>(Set.of(Role.ATTENDEE))).build());
        guest = userRepository.save(User.builder().username("anna.muster").email("anna@oldcompany.example.com")
                .firstName("Anna").lastName("Muster").companyId("oldco")
                .roles(new HashSet<>(Set.of(Role.ATTENDEE))).build());
        guest.addAdditionalEmail(UserAdditionalEmail.builder().email("anna.old@example.com")
                .verifiedAt(Instant.now()).createdAt(Instant.now()).build());
        userRepository.saveAndFlush(guest);
    }

    private String request(String note) throws Exception {
        String body = mockMvc.perform(post("/api/v1/users/me/profile-claims/merge-requests").with(requester())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(note == null
                                ? Map.of("candidateId", guest.getId().toString())
                                : Map.of("candidateId", guest.getId().toString(), "note", note))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(body);
        return json.path("requestId").asText();
    }

    // ---------------------------------------------------------------- the person asks

    @Test
    @DisplayName("a request is stored once and opens a Mail Center conversation; no mail goes to the old address")
    void request_opensConversation() throws Exception {
        String id = request("Ich war bis 2024 bei Firma X.");

        ProfileMergeRequest stored = requestRepository.findById(UUID.fromString(id)).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(ProfileMergeRequest.Status.OPEN);
        assertThat(stored.getRequesterUsername()).isEqualTo("anna.muster.2");
        assertThat(stored.getSourceUsername()).isEqualTo("anna.muster");
        assertThat(stored.getNote()).isEqualTo("Ich war bis 2024 bei Firma X.");

        ArgumentCaptor<EventManagementMailCenterClient.OpenRequest> open =
                ArgumentCaptor.forClass(EventManagementMailCenterClient.OpenRequest.class);
        verify(mailCenter).openMergeRequest(open.capture());
        assertThat(open.getValue().requestId()).isEqualTo(UUID.fromString(id));
        assertThat(open.getValue().requesterEmail()).isEqualTo("anna.private@example.com");
        assertThat(open.getValue().note()).isEqualTo("Ich war bis 2024 bei Firma X.");
        verify(emailService, never()).sendHtmlEmail(eq("anna@oldcompany.example.com"), anyString(), anyString());
    }

    @Test
    @DisplayName("a second request for the same pair while one is open → 409, and the candidate shows it as pending")
    void request_twice_isConflict() throws Exception {
        request(null);

        mockMvc.perform(post("/api/v1/users/me/profile-claims/merge-requests").with(requester())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"candidateId\":\"" + guest.getId() + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("MERGE_REQUEST_OPEN"));

        mockMvc.perform(get("/api/v1/users/me/profile-claims").with(requester()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].candidateId").value(guest.getId().toString()))
                .andExpect(jsonPath("$[0].requestPending").value(true));
    }

    @Test
    @DisplayName("a pair declined less than 30 days ago cannot be asked again (409)")
    void request_recentlyDeclined_isConflict() throws Exception {
        requestRepository.save(ProfileMergeRequest.builder()
                .requesterUsername("anna.muster.2").sourceUsername("anna.muster")
                .status(ProfileMergeRequest.Status.DECLINED).reason("Not the same person")
                .createdAt(Instant.now().minus(3, ChronoUnit.DAYS))
                .decidedAt(Instant.now().minus(2, ChronoUnit.DAYS)).decidedBy("orga.nizer").build());

        mockMvc.perform(post("/api/v1/users/me/profile-claims/merge-requests").with(requester())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"candidateId\":\"" + guest.getId() + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("MERGE_REQUEST_DECLINED"));
    }

    @Test
    @DisplayName("a profile with a login is no candidate: 404, nothing stored")
    void request_forProfileWithLogin_is404() throws Exception {
        guest.setCognitoUserId("cog-other");
        userRepository.saveAndFlush(guest);

        mockMvc.perform(post("/api/v1/users/me/profile-claims/merge-requests").with(requester())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"candidateId\":\"" + guest.getId() + "\"}"))
                .andExpect(status().isNotFound());
        assertThat(requestRepository.findAll()).isEmpty();
        verify(mailCenter, never()).openMergeRequest(any());
    }

    // ---------------------------------------------------------------- the organizers decide

    @Test
    @DisplayName("the requester cannot read, approve or decline a request (403)")
    void decide_byNonOrganizer_isForbidden() throws Exception {
        String id = request(null);

        mockMvc.perform(get("/api/v1/users/merge-requests/{id}", id).with(requester())).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/users/merge-requests/{id}/approve", id).with(requester())).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/users/merge-requests/{id}/decline", id).with(requester())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"no\"}"))
                .andExpect(status().isForbidden());
        verify(eventManagement, never()).merge(any());
    }

    @Test
    @DisplayName("an organizer sees the note and both profiles with what would move")
    void get_showsPreview() throws Exception {
        String id = request("Firma X bis 2024");

        mockMvc.perform(get("/api/v1/users/merge-requests/{id}", id).with(organizer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.note").value("Firma X bis 2024"))
                .andExpect(jsonPath("$.preview.target.username").value("anna.muster.2"))
                .andExpect(jsonPath("$.preview.source.username").value("anna.muster"))
                .andExpect(jsonPath("$.preview.source.email").value("anna@oldcompany.example.com"))
                .andExpect(jsonPath("$.preview.moves['registrations.attendee_username']").value(3))
                .andExpect(jsonPath("$.preview.canMerge").value(true));
    }

    @Test
    @DisplayName("approve merges, adds none of the old addresses, mails the requester, closes the conversation")
    void approve_mergesWithoutOldAddresses() throws Exception {
        String id = request(null);

        mockMvc.perform(post("/api/v1/users/merge-requests/{id}/approve", id).with(organizer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.decidedBy").value("orga.nizer"));

        ArgumentCaptor<EventManagementUserMergeClient.MergeCall> call =
                ArgumentCaptor.forClass(EventManagementUserMergeClient.MergeCall.class);
        verify(eventManagement).merge(call.capture());
        assertThat(call.getValue().sourceUsername()).isEqualTo("anna.muster");
        assertThat(call.getValue().targetUsername()).isEqualTo("anna.muster.2");
        assertThat(call.getValue().performedBy()).isEqualTo("orga.nizer");
        assertThat(call.getValue().sourceProfile()).containsEntry("email", "anna@oldcompany.example.com");

        entityManager.flush();
        entityManager.clear();
        assertThat(userRepository.findByUsername("anna.muster")).isEmpty();
        User target = userRepository.findByUsername("anna.muster.2").orElseThrow();
        assertThat(target.getAdditionalEmails()).isEmpty();
        verify(emailService).sendHtmlEmail(eq("anna.private@example.com"), anyString(), anyString());
        verify(mailCenter).closeMergeRequest(UUID.fromString(id), "orga.nizer");
    }

    @Test
    @DisplayName("decline needs a reason, mails it to the requester and closes the conversation; no merge")
    void decline_mailsReason() throws Exception {
        String id = request(null);

        mockMvc.perform(post("/api/v1/users/merge-requests/{id}/decline", id).with(organizer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"   \"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/users/merge-requests/{id}/decline", id).with(organizer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Die Anmeldungen gehören zu einer anderen Person.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DECLINED"))
                .andExpect(jsonPath("$.reason").value("Die Anmeldungen gehören zu einer anderen Person."));

        verify(eventManagement, never()).merge(any());
        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendHtmlEmail(eq("anna.private@example.com"), anyString(), html.capture());
        assertThat(html.getValue()).contains("Die Anmeldungen geh");
        verify(mailCenter).closeMergeRequest(UUID.fromString(id), "orga.nizer");
        assertThat(userRepository.findByUsername("anna.muster")).isPresent();
    }

    @Test
    @DisplayName("a decided request cannot be decided again (409)")
    void decide_twice_isConflict() throws Exception {
        String id = request(null);

        mockMvc.perform(post("/api/v1/users/merge-requests/{id}/decline", id).with(organizer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"no\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/users/merge-requests/{id}/approve", id).with(organizer()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("MERGE_REQUEST_DECIDED"));
    }

    // ---------------------------------------------------------------- helpers

    private static RequestPostProcessor requester() {
        return user("anna.muster.2").roles("ATTENDEE");
    }

    private static RequestPostProcessor organizer() {
        return user("orga.nizer").roles("ORGANIZER");
    }
}
