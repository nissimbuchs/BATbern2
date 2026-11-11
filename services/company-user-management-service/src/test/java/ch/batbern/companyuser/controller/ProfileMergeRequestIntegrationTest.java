package ch.batbern.companyuser.controller;

import ch.batbern.companyuser.client.HttpMailCenterRequestContract;
import ch.batbern.companyuser.client.HttpUserReferenceContract;
import ch.batbern.companyuser.client.MailCenterContract;
import ch.batbern.companyuser.client.UserReferenceHolders;
import ch.batbern.companyuser.config.TestAwsConfig;
import ch.batbern.companyuser.domain.ProfileMergeRequest;
import ch.batbern.companyuser.domain.User;
import ch.batbern.companyuser.domain.UserAdditionalEmail;
import ch.batbern.companyuser.repository.ProfileMergeRequestRepository;
import ch.batbern.companyuser.repository.UserRepository;
import ch.batbern.companyuser.service.ProfileMergeRequestService;
import ch.batbern.shared.test.mail.SesRecorder;
import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.shared.test.contract.ContractServer;
import ch.batbern.shared.types.Role;
import com.fasterxml.jackson.databind.JsonNode;
import com.jayway.jsonpath.JsonPath;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.servlet.MockMvc;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static ch.batbern.companyuser.client.UserReferenceHolders.HOLDER_A;
import static ch.batbern.companyuser.client.UserReferenceHolders.HOLDER_B;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * #1237: claiming an earlier profile without access to its old address. The person asks, any
 * organizer approves or declines in the Mail Center. The merge and the Mail Center conversation go
 * through the real contract callers, answered at the wire by {@link ContractServer}s on the
 * user-reference contract (two holders, Epic 17 entry 7b) and the Mail Center request contract
 * (Epic 17 entry 7). Without an expectation a holder call fails the test.
 *
 * <p>Not {@code @Transactional}: the decision reaches the contract after the commit. Every test
 * commits; {@code @BeforeEach} and {@code @AfterEach} remove the rows.
 */
@SpringBootTest
@AutoConfigureMockMvc
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
    private SesRecorder sesRecorder;

    /** Mail goes only to the requester; the old address must never get one. Both deliverable (simulator). */
    private static final String REQUESTER = "success+anna.private@simulator.amazonses.com";
    private static final String OLD_ADDRESS = "success+anna.oldcompany@simulator.amazonses.com";
    @Autowired
    private ObjectMapper objectMapper;
    /** The real contract caller; the holders answer through the contract harness at the wire. */
    @Autowired
    private HttpUserReferenceContract userReferenceContract;
    /** The real contract caller; the implementer answers through the contract harness at the wire. */
    @Autowired
    private HttpMailCenterRequestContract mailCenterContract;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private MeterRegistry meterRegistry;
    @Autowired
    private ProfileMergeRequestService requestService;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private ContractServer mailCenter;
    private ContractServer holders;
    /** The id of the request filed last; the decision is expected for it. */
    private String filed;

    private User login;
    private User guest;

    @BeforeEach
    void setUp() {
        removeRows();
        sesRecorder.reset();
        mailCenter = MailCenterContract.bindTo(mailCenterContract);
        holders = UserReferenceHolders.bindTo(userReferenceContract);
        userRepository.save(User.builder().username("orga.nizer").email("orga@example.com")
                .firstName("Orga").lastName("Nizer").cognitoUserId("cog-orga")
                .roles(new HashSet<>(Set.of(Role.ORGANIZER))).build());
        login = userRepository.save(User.builder().username("anna.muster.2").email(REQUESTER)
                .firstName("Anna").lastName("Muster").cognitoUserId("cog-anna")
                .roles(new HashSet<>(Set.of(Role.ATTENDEE))).build());
        guest = userRepository.save(User.builder().username("anna.muster").email(OLD_ADDRESS)
                .firstName("Anna").lastName("Muster").companyId("oldco")
                .roles(new HashSet<>(Set.of(Role.ATTENDEE))).build());
        guest.addAdditionalEmail(UserAdditionalEmail.builder().email("anna.old@example.com")
                .verifiedAt(Instant.now()).createdAt(Instant.now()).build());
        guest = userRepository.saveAndFlush(guest);
    }

    @AfterEach
    void tearDown() {
        mailCenter.close();
        holders.close();
        removeRows();
    }

    private void removeRows() {
        jdbc.update("DELETE FROM profile_merge_requests WHERE requester_username = 'anna.muster.2'");
        jdbc.update("DELETE FROM user_profiles WHERE username IN ('orga.nizer', 'anna.muster.2', 'anna.muster')");
    }

    /** The implementer opens the conversation of a merge request (and records the body). */
    private void expectOpened(StringBuilder body) {
        mailCenter.expect(POST, MailCenterContract.OPENED)
                .andExpect(r -> body.append(((MockClientHttpRequest) r).getBodyAsString()))
                .respondWithStatus(HttpStatus.NO_CONTENT);
    }

    private void expectOpened() {
        expectOpened(new StringBuilder());
    }

    /** The implementer is told the decision of the request filed last ({@link #filed}), by the organizer. */
    private void expectDecided(String status) {
        mailCenter.expect(POST, MailCenterContract.DECIDED)
                .andExpect(r -> {
                    assertThat(r.getURI().getPath()).endsWith("/" + filed + "/decision");
                    String body = ((MockClientHttpRequest) r).getBodyAsString();
                    assertThat((String) JsonPath.read(body, "$.kind")).isEqualTo("MERGE");
                    assertThat((String) JsonPath.read(body, "$.status")).isEqualTo(status);
                    assertThat((String) JsonPath.read(body, "$.decidedBy")).isEqualTo("orga.nizer");
                    assertThat(Instant.parse(JsonPath.read(body, "$.decidedAt"))).isBefore(Instant.now());
                })
                .respondWithStatus(HttpStatus.NO_CONTENT);
    }

    private void expectNoDecision() {
        mailCenter.expect(POST, MailCenterContract.DECIDED).times(ExpectedCount.never())
                .respondWithStatus(HttpStatus.NO_CONTENT);
    }

    /** Both holders preview: the first holds 3 registrations, the second nothing. */
    private void expectPreviews() {
        holders.expect(GET, UserReferenceHolders.PREVIEW).atHost(HOLDER_A).respondWith(HttpStatus.OK, """
                {"moves": {"registrations.attendee_username": 3}, "conflicts": {}, "blockers": []}""");
        holders.expect(GET, UserReferenceHolders.PREVIEW).atHost(HOLDER_B).respondWithExample("nothingHeld");
    }

    /** Both holders merge; the first holder's request body is recorded. */
    private void expectMerges(StringBuilder body) {
        holders.expect(POST, UserReferenceHolders.MERGE).atHost(HOLDER_A)
                .andExpect(r -> body.append(((MockClientHttpRequest) r).getBodyAsString()))
                .respondWith(HttpStatus.OK, """
                        {"moved": {"registrations.attendee_username": 3}, "removed": {}}""");
        holders.expect(POST, UserReferenceHolders.MERGE).atHost(HOLDER_B).respondWithExample("nothingHeld");
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
        filed = json.path("requestId").asText();
        return filed;
    }

    // ---------------------------------------------------------------- the person asks

    @Test
    @DisplayName("a request is stored once and opens a Mail Center conversation; no mail goes to the old address")
    void request_opensConversation() throws Exception {
        StringBuilder opened = new StringBuilder();
        expectOpened(opened);

        String id = request("Ich war bis 2024 bei Firma X.");

        ProfileMergeRequest stored = requestRepository.findById(UUID.fromString(id)).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(ProfileMergeRequest.Status.OPEN);
        assertThat(stored.getRequesterUsername()).isEqualTo("anna.muster.2");
        assertThat(stored.getSourceUsername()).isEqualTo("anna.muster");
        assertThat(stored.getNote()).isEqualTo("Ich war bis 2024 bei Firma X.");

        mailCenter.verify();
        JsonNode body = objectMapper.readTree(opened.toString());
        assertThat(body.path("kind").asText()).isEqualTo("MERGE");
        assertThat(body.path("requestId").asText()).isEqualTo(id);
        assertThat(body.path("requesterName").asText()).isEqualTo("Anna Muster");
        assertThat(body.path("requesterEmail").asText()).isEqualTo(REQUESTER);
        assertThat(body.path("subject").asText()).isEqualTo("Profil zusammenführen: Anna Muster");
        assertThat(body.path("note").asText()).isEqualTo("Ich war bis 2024 bei Firma X.");
        assertThat(sesRecorder.mailsTo(OLD_ADDRESS)).isEmpty();
    }

    @Test
    @DisplayName("when the Mail Center does not answer: 502, nothing stored, the person can ask again")
    void request_mailCenterDown_nothingStored() throws Exception {
        mailCenter.expect(POST, MailCenterContract.OPENED).respondWith(r -> {
            throw new ConnectException("Connection refused");
        });

        mockMvc.perform(post("/api/v1/users/me/profile-claims/merge-requests").with(requester())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"candidateId\":\"" + guest.getId() + "\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.errorCode").value("MERGE_REQUEST_NOT_DELIVERED"));

        mailCenter.verify();
        assertThat(requestRepository.findAll()).isEmpty();
        mockMvc.perform(get("/api/v1/users/me/profile-claims").with(requester()))
                .andExpect(jsonPath("$[0].requestPending").value(false));
    }

    @Test
    @DisplayName("a second request for the same pair while one is open → 409, and the candidate shows it as pending")
    void request_twice_isConflict() throws Exception {
        expectOpened();
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
        guest = userRepository.saveAndFlush(guest);
        mailCenter.expect(POST, MailCenterContract.OPENED).times(ExpectedCount.never())
                .respondWithStatus(HttpStatus.NO_CONTENT);

        mockMvc.perform(post("/api/v1/users/me/profile-claims/merge-requests").with(requester())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"candidateId\":\"" + guest.getId() + "\"}"))
                .andExpect(status().isNotFound());
        assertThat(requestRepository.findAll()).isEmpty();
        mailCenter.verify();
    }

    @Test
    @DisplayName("sweep: the filing's commit fails after the open: nothing stored, the conversation is WITHDRAWN")
    void request_commitFailsAfterOpen_withdrawsConversation() {
        expectOpened();
        StringBuilder withdrawn = new StringBuilder();
        mailCenter.expect(POST, MailCenterContract.DECIDED)
                .andExpect(r -> withdrawn.append(r.getURI().getPath()).append(' ')
                        .append(((MockClientHttpRequest) r).getBodyAsString()))
                .respondWithStatus(HttpStatus.NO_CONTENT);
        UUID[] id = new UUID[1];

        assertThatThrownBy(() -> asRequester(() -> new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
            id[0] = requestService.request(guest.getId(), null).getId();
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void beforeCommit(boolean readOnly) {
                    throw new IllegalStateException("simulated commit failure");
                }
            });
        }))).hasMessageContaining("simulated commit failure");

        mailCenter.verify();
        assertThat(requestRepository.findAll()).isEmpty();
        assertThat(withdrawn.toString()).startsWith("/internal/mail-center-requests/" + id[0] + "/decision ");
        String body = withdrawn.substring(withdrawn.indexOf(" ") + 1);
        assertThat((String) JsonPath.read(body, "$.kind")).isEqualTo("MERGE");
        assertThat((String) JsonPath.read(body, "$.status")).isEqualTo("WITHDRAWN");
        assertThat((String) JsonPath.read(body, "$.decidedBy")).isEqualTo("system:mail-center-request");
    }

    @Test
    @DisplayName("sweep: the open times out (the Mail Center may still commit it): 502, nothing stored, WITHDRAWN sent")
    void request_openTimesOut_withdrawsConversation() throws Exception {
        mailCenter.expect(POST, MailCenterContract.OPENED).respondWith(r -> {
            throw new SocketTimeoutException("Read timed out");
        });
        mailCenter.expect(POST, MailCenterContract.DECIDED)
                .andExpect(r -> assertThat(((MockClientHttpRequest) r).getBodyAsString())
                        .contains("\"status\":\"WITHDRAWN\"", "\"decidedBy\":\"system:mail-center-request\""))
                .respondWithStatus(HttpStatus.NO_CONTENT);

        mockMvc.perform(post("/api/v1/users/me/profile-claims/merge-requests").with(requester())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"candidateId\":\"" + guest.getId() + "\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.errorCode").value("MERGE_REQUEST_NOT_DELIVERED"));

        mailCenter.verify();
        assertThat(requestRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("sweep: a withdrawal the Mail Center does not take is logged and counted")
    void request_rolledBack_withdrawalFails_isCounted() {
        expectOpened();
        mailCenter.expect(POST, MailCenterContract.DECIDED).respondWith(r -> {
            throw new ConnectException("Connection refused");
        });
        double failuresBefore = failures();

        asRequester(() -> new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
            requestService.request(guest.getId(), null);
            tx.setRollbackOnly();
        }));

        mailCenter.verify();
        assertThat(requestRepository.findAll()).isEmpty();
        assertThat(failures()).isEqualTo(failuresBefore + 1);
    }

    // ---------------------------------------------------------------- the organizers decide

    @Test
    @DisplayName("the requester cannot read, approve or decline a request (403)")
    void decide_byNonOrganizer_isForbidden() throws Exception {
        expectOpened();
        expectNoDecision();
        String id = request(null);

        mockMvc.perform(get("/api/v1/users/merge-requests/{id}", id).with(requester())).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/users/merge-requests/{id}/approve", id).with(requester())).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/users/merge-requests/{id}/decline", id).with(requester())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"no\"}"))
                .andExpect(status().isForbidden());
        mailCenter.verify();
    }

    @Test
    @DisplayName("an organizer sees the note and both profiles with what would move")
    void get_showsPreview() throws Exception {
        expectOpened();
        expectPreviews();
        String id = request("Firma X bis 2024");

        mockMvc.perform(get("/api/v1/users/merge-requests/{id}", id).with(organizer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.note").value("Firma X bis 2024"))
                .andExpect(jsonPath("$.preview.target.username").value("anna.muster.2"))
                .andExpect(jsonPath("$.preview.source.username").value("anna.muster"))
                .andExpect(jsonPath("$.preview.source.email").value(OLD_ADDRESS))
                .andExpect(jsonPath("$.preview.moves['registrations.attendee_username']").value(3))
                .andExpect(jsonPath("$.preview.canMerge").value(true));
        holders.verify();
    }

    @Test
    @DisplayName("approve merges, adds none of the old addresses, mails the requester, closes the conversation")
    void approve_mergesWithoutOldAddresses() throws Exception {
        expectOpened();
        expectDecided("APPROVED");
        expectPreviews();
        StringBuilder merged = new StringBuilder();
        expectMerges(merged);
        String id = request(null);

        mockMvc.perform(post("/api/v1/users/merge-requests/{id}/approve", id).with(organizer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.decidedBy").value("orga.nizer"));

        holders.verify();
        JsonNode call = objectMapper.readTree(merged.toString());
        assertThat(call.path("sourceUsername").asText()).isEqualTo("anna.muster");
        assertThat(call.path("targetUsername").asText()).isEqualTo("anna.muster.2");
        assertThat(call.path("performedBy").asText()).isEqualTo("orga.nizer");
        assertThat(call.path("sourceProfile").path("email").asText()).isEqualTo(OLD_ADDRESS);

        assertThat(userRepository.findByUsername("anna.muster")).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_additional_emails e JOIN user_profiles u"
                + " ON u.id = e.user_id WHERE u.username = 'anna.muster.2'", Integer.class)).isZero();
        assertThat(sesRecorder.mailsTo(REQUESTER)).hasSize(1);
        assertThat(sesRecorder.mailsTo(OLD_ADDRESS)).isEmpty();
        mailCenter.verify();
    }

    @Test
    @DisplayName("decline needs a reason, mails it to the requester and closes the conversation; no merge")
    void decline_mailsReason() throws Exception {
        expectOpened();
        expectDecided("DECLINED");
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

        assertThat(sesRecorder.single().to()).containsExactly(REQUESTER);
        assertThat(sesRecorder.single().html()).contains("Die Anmeldungen geh");
        mailCenter.verify();
        assertThat(userRepository.findByUsername("anna.muster")).isPresent();
    }

    @Test
    @DisplayName("when the Mail Center does not take the decision, the decision stands; ERROR and counter")
    void decline_mailCenterDown_decisionStands() throws Exception {
        expectOpened();
        mailCenter.expect(POST, MailCenterContract.DECIDED).respondWith(r -> {
            throw new ConnectException("Connection refused");
        });
        String id = request(null);
        double failuresBefore = failures();

        mockMvc.perform(post("/api/v1/users/merge-requests/{id}/decline", id).with(organizer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Andere Person\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DECLINED"));

        mailCenter.verify();
        assertThat(requestRepository.findById(UUID.fromString(id)).orElseThrow().getStatus())
                .isEqualTo(ProfileMergeRequest.Status.DECLINED);
        assertThat(sesRecorder.mailsTo(REQUESTER)).hasSize(1);
        assertThat(failures()).isEqualTo(failuresBefore + 1);
    }

    @Test
    @DisplayName("a decided request cannot be decided again (409)")
    void decide_twice_isConflict() throws Exception {
        expectOpened();
        expectDecided("DECLINED");
        String id = request(null);

        mockMvc.perform(post("/api/v1/users/merge-requests/{id}/decline", id).with(organizer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"no\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/users/merge-requests/{id}/approve", id).with(organizer()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("MERGE_REQUEST_DECIDED"));
        mailCenter.verify();
    }

    // ---------------------------------------------------------------- helpers

    private double failures() {
        Counter counter = meterRegistry.find("mailcenter.request_contract.failures").counter();
        return counter == null ? 0 : counter.count();
    }

    /** Runs {@code action} as the requester, outside MockMvc (the test owns the filing's transaction). */
    private static void asRequester(Runnable action) {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                new org.springframework.security.core.userdetails.User("anna.muster.2", "",
                        List.of(new SimpleGrantedAuthority("ROLE_ATTENDEE"))),
                null, List.of(new SimpleGrantedAuthority("ROLE_ATTENDEE"))));
        try {
            action.run();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private static RequestPostProcessor requester() {
        return user("anna.muster.2").roles("ATTENDEE");
    }

    private static RequestPostProcessor organizer() {
        return user("orga.nizer").roles("ORGANIZER");
    }
}
