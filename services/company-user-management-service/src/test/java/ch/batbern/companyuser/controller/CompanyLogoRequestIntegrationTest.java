package ch.batbern.companyuser.controller;

import ch.batbern.companyuser.client.HttpMailCenterRequestContract;
import ch.batbern.companyuser.client.MailCenterContract;
import ch.batbern.companyuser.config.TestAwsConfig;
import ch.batbern.companyuser.domain.Company;
import ch.batbern.companyuser.domain.CompanyLogoRequest;
import ch.batbern.companyuser.domain.Logo;
import ch.batbern.companyuser.domain.LogoStatus;
import ch.batbern.companyuser.domain.User;
import ch.batbern.companyuser.repository.CompanyLogoRequestRepository;
import ch.batbern.companyuser.repository.CompanyRepository;
import ch.batbern.companyuser.repository.LogoRepository;
import ch.batbern.companyuser.repository.UserRepository;
import ch.batbern.shared.test.mail.SesRecorder;
import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.shared.test.contract.ContractServer;
import ch.batbern.shared.types.Role;
import com.fasterxml.jackson.databind.JsonNode;
import com.jayway.jsonpath.JsonPath;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.net.ConnectException;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * #1246: a participant asks for a new company logo, an organizer accepts or declines in the Mail
 * Center. The conversation goes through the real contract caller, answered at the wire by the
 * {@link ContractServer} on the Mail Center request contract (Epic 17 entry 7); S3 is the Mockito
 * mock from {@link TestAwsConfig}.
 *
 * <p>Not {@code @Transactional}: the decision reaches the contract after the commit. Every test
 * commits; {@code @BeforeEach} and {@code @AfterEach} remove the rows.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestAwsConfig.class)
@DisplayName("Company logo request (#1246)")
class CompanyLogoRequestIntegrationTest extends AbstractIntegrationTest {

    private static final String OLD_LOGO = "https://cdn.batbern.ch/logos/2024/companies/acme/logo-old.png";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CompanyRepository companyRepository;
    @Autowired
    private LogoRepository logoRepository;
    @Autowired
    private CompanyLogoRequestRepository requestRepository;
    @Autowired
    private SesRecorder sesRecorder;

    /** The requester receives the decision mail: SES mailbox simulator, never delivered. */
    private static final String REQUESTER = "success+anna.acme@simulator.amazonses.com";
    @Autowired
    private ObjectMapper objectMapper;
    /** The real contract caller; the implementer answers through the contract harness at the wire. */
    @Autowired
    private HttpMailCenterRequestContract mailCenterContract;
    @Autowired
    private JdbcTemplate jdbc;

    private ContractServer mailCenter;
    /** The id of the request filed first by {@link #createOk()}; the decision is expected for it. */
    private UUID filed;

    @BeforeEach
    void setUp() {
        removeRows();
        sesRecorder.reset();
        mailCenter = MailCenterContract.bindTo(mailCenterContract);
        userRepository.save(User.builder().username("orga.nizer").email("orga@example.com")
                .firstName("Orga").lastName("Nizer").cognitoUserId("cog-orga")
                .roles(new HashSet<>(Set.of(Role.ORGANIZER))).build());
        userRepository.save(User.builder().username("anna.muster").email(REQUESTER)
                .firstName("Anna").lastName("Muster").companyId("acme").cognitoUserId("cog-anna")
                .roles(new HashSet<>(Set.of(Role.ATTENDEE))).build());
        userRepository.save(User.builder().username("bert.ohne").email("bert@example.com")
                .firstName("Bert").lastName("Ohne").cognitoUserId("cog-bert")
                .roles(new HashSet<>(Set.of(Role.ATTENDEE))).build());
        companyRepository.saveAndFlush(Company.builder().name("acme").displayName("ACME AG")
                .logoUrl(OLD_LOGO).logoS3Key("logos/2024/companies/acme/logo-old.png").logoFileId("old")
                .createdBy("orga.nizer").createdAt(Instant.now()).updatedAt(Instant.now()).build());
        confirmedUpload("up-1");
        confirmedUpload("up-2");
    }

    @AfterEach
    void tearDown() {
        mailCenter.close();
        removeRows();
    }

    private void removeRows() {
        jdbc.update("DELETE FROM company_logo_requests WHERE requester_username = 'anna.muster'");
        jdbc.update("DELETE FROM logos WHERE upload_id IN ('up-1', 'up-2')");
        jdbc.update("DELETE FROM companies WHERE name = 'acme'");
        jdbc.update("DELETE FROM user_profiles WHERE username IN ('orga.nizer', 'anna.muster', 'bert.ohne')");
    }

    /** The implementer opens the conversation of a logo request (and records the body). */
    private void expectOpened(StringBuilder body, ExpectedCount count) {
        mailCenter.expect(POST, MailCenterContract.OPENED).times(count)
                .andExpect(r -> body.append(((MockClientHttpRequest) r).getBodyAsString()))
                .respondWithStatus(HttpStatus.NO_CONTENT);
    }

    private void expectOpened() {
        expectOpened(new StringBuilder(), ExpectedCount.once());
    }

    /** The implementer is told the decision of the request filed first ({@link #filed}), by the organizer. */
    private void expectDecided(String status) {
        mailCenter.expect(POST, MailCenterContract.DECIDED)
                .andExpect(r -> {
                    assertThat(r.getURI().getPath()).endsWith("/" + filed + "/decision");
                    String body = ((MockClientHttpRequest) r).getBodyAsString();
                    assertThat((String) JsonPath.read(body, "$.kind")).isEqualTo("LOGO");
                    assertThat((String) JsonPath.read(body, "$.status")).isEqualTo(status);
                    assertThat((String) JsonPath.read(body, "$.decidedBy")).isEqualTo("orga.nizer");
                    assertThat(Instant.parse(JsonPath.read(body, "$.decidedAt"))).isBefore(Instant.now());
                })
                .respondWithStatus(HttpStatus.NO_CONTENT);
    }

    private void confirmedUpload(String uploadId) {
        logoRepository.saveAndFlush(Logo.builder().uploadId(uploadId)
                .s3Key("logos/temp/" + uploadId + "/logo-f.png").fileExtension("png").fileSize(1000L)
                .mimeType("image/png").status(LogoStatus.CONFIRMED).expiresAt(Instant.now().plusSeconds(3600))
                .build());
    }

    private String create(RequestPostProcessor who, String uploadId, String note, int expectedStatus)
            throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("logoUploadId", uploadId);
        if (note != null) {
            body.put("note", note);
        }
        return mockMvc.perform(post("/api/v1/users/me/company-logo-requests").with(who)
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)))
                .andExpect(status().is(expectedStatus))
                .andReturn().getResponse().getContentAsString();
    }

    private UUID createOk() throws Exception {
        String body = create(anna(), "up-1", "Neues Logo seit Juni", 201);
        filed = UUID.fromString(objectMapper.readTree(body).path("requestId").asText());
        return filed;
    }

    // ---------------------------------------------------------------- the participant asks

    @Test
    @DisplayName("a request snapshots the old logo, keeps the upload out of the cleanup and opens a conversation")
    void request_storesAndOpensConversation() throws Exception {
        StringBuilder opened = new StringBuilder();
        expectOpened(opened, ExpectedCount.once());

        UUID id = createOk();

        CompanyLogoRequest stored = requestRepository.findById(id).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(CompanyLogoRequest.Status.OPEN);
        assertThat(stored.getRequesterUsername()).isEqualTo("anna.muster");
        assertThat(stored.getCompanyName()).isEqualTo("acme");
        assertThat(stored.getOldLogoUrl()).isEqualTo(OLD_LOGO);
        assertThat(stored.getNewLogoUrl()).contains("logos/requests/up-1/");
        assertThat(stored.getNote()).isEqualTo("Neues Logo seit Juni");

        Logo logo = logoRepository.findByUploadId("up-1").orElseThrow();
        assertThat(logo.getStatus()).isEqualTo(LogoStatus.ASSOCIATED);
        assertThat(logo.getAssociatedEntityType()).isEqualTo("LOGO_REQUEST");
        assertThat(logo.getExpiresAt()).isNull();

        mailCenter.verify();
        JsonNode body = objectMapper.readTree(opened.toString());
        assertThat(body.path("kind").asText()).isEqualTo("LOGO");
        assertThat(body.path("requestId").asText()).isEqualTo(id.toString());
        assertThat(body.path("requesterEmail").asText()).isEqualTo(REQUESTER);
        assertThat(body.path("subject").asText()).isEqualTo("Neues Logo für ACME AG: Anna Muster");
        assertThat(body.path("note").asText()).isEqualTo("Neues Logo seit Juni");

        // The company is untouched until an organizer decides.
        assertThat(companyRepository.findByName("acme").orElseThrow().getLogoUrl()).isEqualTo(OLD_LOGO);
    }

    @Test
    @DisplayName("the open request is shown on the profile; without one the endpoint answers 204")
    void openRequest_forProfile() throws Exception {
        mockMvc.perform(get("/api/v1/users/me/company-logo-requests/open").with(anna()))
                .andExpect(status().isNoContent());
        expectOpened();
        UUID id = createOk();
        mockMvc.perform(get("/api/v1/users/me/company-logo-requests/open").with(anna()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId").value(id.toString()))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.companyName").value("acme"));
    }

    @Test
    @DisplayName("without a company on the profile: 409 NO_COMPANY, nothing stored")
    void request_withoutCompany_conflict() throws Exception {
        expectOpened(new StringBuilder(), ExpectedCount.never());
        create(user("bert.ohne").roles("ATTENDEE"), "up-1", null, 409);
        assertThat(requestRepository.count()).isZero();
        mailCenter.verify();
    }

    @Test
    @DisplayName("one open request per person and company: 409 LOGO_REQUEST_OPEN")
    void request_twice_conflict() throws Exception {
        expectOpened();
        createOk();
        String body = create(anna(), "up-2", null, 409);
        assertThat(objectMapper.readTree(body).path("errorCode").asText()).isEqualTo("LOGO_REQUEST_OPEN");
    }

    @Test
    @DisplayName("an unknown or unconfirmed upload: 400 LOGO_NOT_READY")
    void request_unknownUpload_badRequest() throws Exception {
        String body = create(anna(), "nope", null, 400);
        assertThat(objectMapper.readTree(body).path("errorCode").asText()).isEqualTo("LOGO_NOT_READY");
    }

    @Test
    @DisplayName("when the Mail Center is unreachable: 502, nothing stored")
    void request_mailCenterDown_nothingStored() throws Exception {
        mailCenter.expect(POST, MailCenterContract.OPENED).respondWith(r -> {
            throw new ConnectException("Connection refused");
        });

        String body = create(anna(), "up-1", null, 502);

        assertThat(objectMapper.readTree(body).path("errorCode").asText()).isEqualTo("LOGO_REQUEST_NOT_DELIVERED");
        assertThat(requestRepository.count()).isZero();
        assertThat(logoRepository.findByUploadId("up-1").orElseThrow().getStatus()).isEqualTo(LogoStatus.CONFIRMED);
        mailCenter.verify();
    }

    // ---------------------------------------------------------------- organizers decide

    @Test
    @DisplayName("only organizers read and decide a request")
    void decide_participant_forbidden() throws Exception {
        expectOpened();
        mailCenter.expect(POST, MailCenterContract.DECIDED).times(ExpectedCount.never())
                .respondWithStatus(HttpStatus.NO_CONTENT);
        UUID id = createOk();
        mockMvc.perform(get("/api/v1/users/company-logo-requests/{id}", id).with(anna()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/users/company-logo-requests/{id}/approve", id).with(anna()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/users/company-logo-requests/{id}/decline", id).with(anna())
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
        mailCenter.verify();
    }

    @Test
    @DisplayName("the organizer view shows company, old and new logo and the note")
    void get_organizerView() throws Exception {
        expectOpened();
        UUID id = createOk();
        mockMvc.perform(get("/api/v1/users/company-logo-requests/{id}", id).with(organizer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companyName").value("acme"))
                .andExpect(jsonPath("$.companyDisplayName").value("ACME AG"))
                .andExpect(jsonPath("$.oldLogoUrl").value(OLD_LOGO))
                .andExpect(jsonPath("$.newLogoUrl").isNotEmpty())
                .andExpect(jsonPath("$.requesterName").value("Anna Muster"))
                .andExpect(jsonPath("$.note").value("Neues Logo seit Juni"));
    }

    @Test
    @DisplayName("accept: the company gets the new logo, the requester a mail, the conversation is closed")
    void approve_setsLogo() throws Exception {
        expectOpened();
        expectDecided("APPROVED");
        UUID id = createOk();

        mockMvc.perform(post("/api/v1/users/company-logo-requests/{id}/approve", id).with(organizer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.decidedBy").value("orga.nizer"));

        Company company = companyRepository.findByName("acme").orElseThrow();
        assertThat(company.getLogoUrl()).contains("/companies/acme/logo-up-1.png");
        assertThat(company.getLogoS3Key()).contains("/companies/acme/logo-up-1.png");
        assertThat(company.getLogoFileId()).isEqualTo("up-1");
        Logo logo = logoRepository.findByUploadId("up-1").orElseThrow();
        assertThat(logo.getAssociatedEntityType()).isEqualTo("COMPANY");
        assertThat(logo.getAssociatedEntityId()).isEqualTo("acme");

        assertThat(sesRecorder.single().to()).containsExactly(REQUESTER);
        assertThat(sesRecorder.single().html()).contains("ACME AG");
        mailCenter.verify();
    }

    @Test
    @DisplayName("decline with a reason: the reason is mailed, the logo stays")
    void decline_withReason() throws Exception {
        expectOpened();
        expectDecided("DECLINED");
        UUID id = createOk();

        mockMvc.perform(post("/api/v1/users/company-logo-requests/{id}/decline", id).with(organizer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Bitte als SVG <senden>\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DECLINED"))
                .andExpect(jsonPath("$.reason").value("Bitte als SVG <senden>"));

        assertThat(companyRepository.findByName("acme").orElseThrow().getLogoUrl()).isEqualTo(OLD_LOGO);
        assertThat(sesRecorder.single().to()).containsExactly(REQUESTER);
        assertThat(sesRecorder.single().html()).contains("Bitte als SVG &lt;senden&gt;").contains("<blockquote>");
        mailCenter.verify();
    }

    @Test
    @DisplayName("decline without a reason: the mail has no reason block")
    void decline_withoutReason() throws Exception {
        expectOpened();
        expectDecided("DECLINED");
        UUID id = createOk();

        mockMvc.perform(post("/api/v1/users/company-logo-requests/{id}/decline", id).with(organizer())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DECLINED"));

        assertThat(sesRecorder.single().to()).containsExactly(REQUESTER);
        assertThat(sesRecorder.single().html()).doesNotContain("<blockquote>").doesNotContain("{{");
    }

    @Test
    @DisplayName("a decided request cannot be decided again: 409 LOGO_REQUEST_DECIDED; a new request is possible")
    void decide_twice_conflict() throws Exception {
        expectOpened(new StringBuilder(), ExpectedCount.twice());
        expectDecided("DECLINED");
        UUID id = createOk();
        mockMvc.perform(post("/api/v1/users/company-logo-requests/{id}/decline", id).with(organizer())
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/users/company-logo-requests/{id}/approve", id).with(organizer()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("LOGO_REQUEST_DECIDED"));
        create(anna(), "up-2", null, 201);
        mailCenter.verify();
    }

    @Test
    @DisplayName("an unknown request: 404")
    void get_unknown_notFound() throws Exception {
        mockMvc.perform(get("/api/v1/users/company-logo-requests/{id}", UUID.randomUUID()).with(organizer()))
                .andExpect(status().isNotFound());
    }

    private static RequestPostProcessor anna() {
        return user("anna.muster").roles("ATTENDEE");
    }

    private static RequestPostProcessor organizer() {
        return user("orga.nizer").roles("ORGANIZER");
    }
}
