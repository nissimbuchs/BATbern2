package ch.batbern.companyuser.controller;

import ch.batbern.companyuser.client.HttpUserReferenceContract;
import ch.batbern.companyuser.client.UserReferenceHolders;
import ch.batbern.companyuser.config.TestAwsConfig;
import ch.batbern.shared.types.Role;
import ch.batbern.companyuser.domain.User;
import ch.batbern.companyuser.domain.UserAdditionalEmail;
import ch.batbern.companyuser.repository.UserAdditionalEmailRepository;
import ch.batbern.companyuser.repository.UserRepository;
import ch.batbern.companyuser.service.ProfileClaimTokenService;
import ch.batbern.shared.test.mail.SesRecorder;
import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.shared.test.contract.ContractServer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.net.ConnectException;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static ch.batbern.companyuser.client.UserReferenceHolders.HOLDER_A;
import static ch.batbern.companyuser.client.UserReferenceHolders.HOLDER_B;
import static ch.batbern.companyuser.client.UserReferenceHolders.MERGE;
import static ch.batbern.companyuser.client.UserReferenceHolders.PREVIEW;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * #1183 C (organizer merges two profiles) and D (a person claims an earlier profile under their
 * name) against real PostgreSQL. The holders of the user-reference contract (Epic 17 entry 7b) answer
 * at the wire through the {@link ContractServer}: two holders, like event-management and
 * partner-coordination in production; each holder's own part is tested in its service.
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
    private SesRecorder sesRecorder;

    /** The guest profile's address receives the claim link: SES mailbox simulator, never delivered. */
    private static final String GUEST = "success+anna.work@simulator.amazonses.com";
    @Autowired
    private ProfileClaimTokenService claimTokenService;
    /** The real contract caller; the holders answer through the contract harness at the wire. */
    @Autowired
    private HttpUserReferenceContract userReferenceContract;
    @Autowired
    private ObjectMapper objectMapper;
    private ContractServer holders;
    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;

    private User login;
    private User guest;

    @BeforeEach
    void setUp() {
        sesRecorder.reset();
        holders = UserReferenceHolders.bindTo(userReferenceContract);

        userRepository.save(User.builder().username("orga.nizer").email("orga@example.com")
                .firstName("Orga").lastName("Nizer").cognitoUserId("cog-orga")
                .roles(new HashSet<>(Set.of(Role.ORGANIZER))).build());
        login = userRepository.save(User.builder().username("anna.muster.2").email("anna.private@example.com")
                .firstName("Anna").lastName("Muster").cognitoUserId("cog-anna")
                .roles(new HashSet<>(Set.of(Role.ATTENDEE))).build());
        guest = userRepository.save(User.builder().username("anna.muster").email(GUEST)
                .firstName("ANNA").lastName("muster").companyId("acme")
                .roles(new HashSet<>(Set.of(Role.ATTENDEE, Role.SPEAKER))).build());
        guest.addAdditionalEmail(UserAdditionalEmail.builder().email("anna.old@example.com")
                .verifiedAt(Instant.now()).createdAt(Instant.now()).build());
        userRepository.saveAndFlush(guest);
    }

    @AfterEach
    void tearDown() {
        holders.close();
    }

    /** Holder A (event-management-like) and holder B (partner-coordination-like) preview, no blocker. */
    private void expectPreviews() {
        holders.expect(GET, PREVIEW).atHost(HOLDER_A).respondWith(HttpStatus.OK, """
                {"moves": {"registrations.attendee_username": 2}, "conflicts": {"registrations": 1},
                 "blockers": []}""");
        holders.expect(GET, PREVIEW).atHost(HOLDER_B).respondWith(HttpStatus.OK, """
                {"moves": {"partner_notes.author_username": 1}, "conflicts": {}, "blockers": []}""");
    }

    /** Both holders merge; the request body holder A received is appended to {@code body}. */
    private void expectMerges(StringBuilder body) {
        holders.expect(POST, MERGE).atHost(HOLDER_A)
                .andExpect(r -> body.append(((MockClientHttpRequest) r).getBodyAsString()))
                .respondWith(HttpStatus.OK, """
                        {"moved": {"registrations.attendee_username": 2}, "removed": {"registrations": 1}}""");
        holders.expect(POST, MERGE).atHost(HOLDER_B).respondWith(HttpStatus.OK, """
                {"moved": {"partner_notes.author_username": 1}, "removed": {}}""");
    }

    private void expectNoMerge() {
        holders.expect(POST, MERGE).times(ExpectedCount.never()).respondWithExample();
    }

    // ---------------------------------------------------------------- C: organizer merge

    @Test
    @WithMockUser(username = "orga.nizer", roles = "ORGANIZER")
    @DisplayName("C: preview shows both profiles and every holder's counts; mergeable")
    void preview_showsBothSides() throws Exception {
        expectPreviews();

        mockMvc.perform(get("/api/v1/users/anna.muster.2/merge-preview").param("source", "anna.muster"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.target.username").value("anna.muster.2"))
                .andExpect(jsonPath("$.target.hasLogin").value(true))
                .andExpect(jsonPath("$.source.hasLogin").value(false))
                .andExpect(jsonPath("$.source.additionalEmails[0]").value("anna.old@example.com"))
                .andExpect(jsonPath("$.moves['registrations.attendee_username']").value(2))
                .andExpect(jsonPath("$.moves['partner_notes.author_username']").value(1))
                .andExpect(jsonPath("$.conflicts.registrations").value(1))
                .andExpect(jsonPath("$.canMerge").value(true));
        holders.verify();
    }

    @Test
    @WithMockUser(username = "orga.nizer", roles = "ORGANIZER")
    @DisplayName("C: one holder refuses (blocker): not mergeable; merge refused before any holder moves a row")
    void merge_holderBlocks_isRefusedBeforeAnyHolderMerges() throws Exception {
        holders.expect(GET, PREVIEW).atHost(HOLDER_A).times(ExpectedCount.twice()).respondWithExample("blocked");
        holders.expect(GET, PREVIEW).atHost(HOLDER_B).times(ExpectedCount.twice()).respondWithExample("nothingHeld");
        expectNoMerge();

        mockMvc.perform(get("/api/v1/users/anna.muster.2/merge-preview").param("source", "anna.muster"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blockers[0]").value("BATbern58"))
                .andExpect(jsonPath("$.canMerge").value(false))
                .andExpect(jsonPath("$.reasons[0]").value("BLOCKED"));
        mockMvc.perform(post("/api/v1/users/anna.muster.2/merge").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceUsername\": \"anna.muster\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("MERGE_NOT_ALLOWED"))
                .andExpect(jsonPath("$.details.reasons[0]").value("BLOCKED"));

        holders.verify();
        assertThat(userRepository.findByUsername("anna.muster")).isPresent();
    }

    @Test
    @WithMockUser(username = "orga.nizer", roles = "ORGANIZER")
    @DisplayName("C: merge moves roles and addresses, deletes the source, every holder moves its rows, snapshot sent")
    void merge_absorbsTheGuestProfile() throws Exception {
        expectPreviews();
        StringBuilder merged = new StringBuilder();
        expectMerges(merged);

        mockMvc.perform(post("/api/v1/users/anna.muster.2/merge").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceUsername\": \"anna.muster\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.moved['registrations.attendee_username']").value(2))
                .andExpect(jsonPath("$.moved['partner_notes.author_username']").value(1))
                .andExpect(jsonPath("$.removed.registrations").value(1));
        holders.verify();

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
                .containsExactlyInAnyOrder(GUEST, "anna.old@example.com");
        assertThat(additionalEmailRepository.findAll())
                .filteredOn(e -> e.getEmail().equals(GUEST))
                .allSatisfy(e -> assertThat(e.getVerifiedAt()).isNotNull());

        JsonNode call = objectMapper.readTree(merged.toString());
        assertThat(call.path("sourceUsername").asText()).isEqualTo("anna.muster");
        assertThat(call.path("targetUsername").asText()).isEqualTo("anna.muster.2");
        assertThat(call.path("performedBy").asText()).isEqualTo("orga.nizer");
        assertThat(call.path("trigger").asText()).isEqualTo("ORGANIZER");
        assertThat(call.path("sourceProfile").toString()).contains(GUEST)
                .contains("SPEAKER").contains("anna.old@example.com");
    }

    @Test
    @WithMockUser(username = "orga.nizer", roles = "ORGANIZER")
    @DisplayName("C: an import placeholder address (vorname.nachname@batbern.ch) is not carried, only logged")
    void merge_placeholderAddress_isNotCarried() throws Exception {
        User placeholder = userRepository.saveAndFlush(User.builder().username("anna.muster.3")
                .email("anna.muster@batbern.ch").firstName("Anna").lastName("Muster")
                .roles(new HashSet<>(Set.of(Role.ATTENDEE))).build());
        expectPreviews();
        StringBuilder merged = new StringBuilder();
        expectMerges(merged);

        mockMvc.perform(post("/api/v1/users/anna.muster.2/merge").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceUsername\": \"" + placeholder.getUsername() + "\"}"))
                .andExpect(status().isOk());

        entityManager.flush();
        entityManager.clear();
        assertThat(userRepository.findByUsername("anna.muster.3")).isEmpty();
        assertThat(additionalEmailRepository.findAll())
                .extracting(UserAdditionalEmail::getEmail)
                .doesNotContain("anna.muster@batbern.ch");
        holders.verify();
        assertThat(objectMapper.readTree(merged.toString()).path("sourceProfile").toString())
                .contains("anna.muster@batbern.ch");
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
        holders.expect(GET, PREVIEW).times(ExpectedCount.twice()).respondWithExample("clean");
        expectNoMerge();

        mockMvc.perform(get("/api/v1/users/anna.muster/merge-preview").param("source", "anna.muster.2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canMerge").value(false))
                .andExpect(jsonPath("$.reasons[0]").value("SOURCE_HAS_LOGIN"));

        mockMvc.perform(post("/api/v1/users/anna.muster/merge").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceUsername\": \"anna.muster.2\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("MERGE_NOT_ALLOWED"));
        holders.verify();
        assertThat(userRepository.findByUsername("anna.muster.2")).isPresent();
    }

    @Test
    @WithMockUser(username = "orga.nizer", roles = "ORGANIZER")
    @DisplayName("C: a holder does not answer: the source profile stays, 502; repeated with both holders up, it completes")
    void merge_holderDown_keepsSource() throws Exception {
        expectPreviews();
        holders.expect(POST, MERGE).atHost(HOLDER_A).respondWithExample();
        holders.expect(POST, MERGE).atHost(HOLDER_B).respondWith(r -> {
            throw new ConnectException("Connection refused");
        });

        mockMvc.perform(post("/api/v1/users/anna.muster.2/merge").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceUsername\": \"anna.muster\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.errorCode").value("MERGE_EVENT_SERVICE_FAILED"));
        holders.verify();
        assertThat(userRepository.findByUsername("anna.muster")).isPresent();

        // Both holders up again: the same merge completes (holder A's part is idempotent, moves nothing more).
        holders.reset();
        expectPreviews();
        holders.expect(POST, MERGE).atHost(HOLDER_A).respondWithExample("nothingHeld");
        holders.expect(POST, MERGE).atHost(HOLDER_B).respondWith(HttpStatus.OK, """
                {"moved": {"partner_notes.author_username": 1}, "removed": {}}""");

        mockMvc.perform(post("/api/v1/users/anna.muster.2/merge").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceUsername\": \"anna.muster\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.moved['partner_notes.author_username']").value(1));
        holders.verify();
        entityManager.flush();
        entityManager.clear();
        assertThat(userRepository.findByUsername("anna.muster")).isEmpty();
    }

    @Test
    @WithMockUser(username = "anna.muster.2", roles = "ATTENDEE")
    @DisplayName("C: only organizers merge")
    void merge_requiresOrganizer() throws Exception {
        mockMvc.perform(post("/api/v1/users/anna.muster.2/merge").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceUsername\": \"anna.muster\"}"))
                .andExpect(status().isForbidden());
        holders.verify();
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
                .andExpect(jsonPath("$[0].maskedEmail").value("s***@simulator.amazonses.com"))
                .andExpect(jsonPath("$[0].companyName").value("acme"));
    }

    @Test
    @WithMockUser(username = "anna.muster.2", roles = "ATTENDEE")
    @DisplayName("D: a claim sends a link to the earlier profile's address, never to the caller")
    void claim_sendsLinkToTheOldAddress() throws Exception {
        mockMvc.perform(post("/api/v1/users/me/profile-claims").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"candidateId\": \"" + guest.getId() + "\"}"))
                .andExpect(status().isAccepted());

        assertThat(sesRecorder.single().to()).containsExactly(GUEST);
        assertThat(sesRecorder.single().html()).contains("/claim-profile?token=");
    }

    @Test
    @WithMockUser(username = "anna.muster.2", roles = "ATTENDEE")
    @DisplayName("#1244: an imported profile with a placeholder vorname.nachname@batbern.ch is flagged, a real address is not")
    void claims_flagPlaceholderAddress() throws Exception {
        User imported = userRepository.save(User.builder().username("anna.muster.4")
                .email("anna.muster@batbern.ch").firstName("Anna").lastName("Muster").build());

        mockMvc.perform(get("/api/v1/users/me/profile-claims"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.candidateId == '" + imported.getId() + "')].placeholderAddress")
                        .value(true))
                .andExpect(jsonPath("$[?(@.candidateId == '" + guest.getId() + "')].placeholderAddress")
                        .value(false));
    }

    @Test
    @WithMockUser(username = "anna.muster.2", roles = "ATTENDEE")
    @DisplayName("#1244: no claim link to a placeholder address (409 PLACEHOLDER_ADDRESS, no mail)")
    void claim_placeholderAddress_is409() throws Exception {
        User imported = userRepository.save(User.builder().username("anna.muster.4")
                .email("Anna.Muster@BATbern.ch").firstName("Anna").lastName("Muster").build());

        mockMvc.perform(post("/api/v1/users/me/profile-claims").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"candidateId\": \"" + imported.getId() + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("PLACEHOLDER_ADDRESS"));
        assertThat(sesRecorder.mails()).isEmpty();
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
        assertThat(sesRecorder.mails()).isEmpty();
    }

    @Test
    @WithMockUser(username = "anna.muster.2", roles = "ATTENDEE")
    @DisplayName("D: confirming the link while logged in as the claimer merges, as SELF_CLAIM")
    void confirm_merges() throws Exception {
        String token = claimTokenService.generateToken(login.getId(), guest.getId(), guest.getEmail());
        expectPreviews();
        StringBuilder merged = new StringBuilder();
        expectMerges(merged);

        mockMvc.perform(post("/api/v1/users/me/profile-claims/confirm").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\": \"" + token + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.merged").value(true))
                .andExpect(jsonPath("$.alreadyMerged").value(false));

        entityManager.flush();
        assertThat(userRepository.findByUsername("anna.muster")).isEmpty();
        holders.verify();
        JsonNode call = objectMapper.readTree(merged.toString());
        assertThat(call.path("trigger").asText()).isEqualTo("SELF_CLAIM");
        assertThat(call.path("performedBy").asText()).isEqualTo("anna.muster.2");

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
        holders.verify();
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
