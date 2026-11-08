package ch.batbern.companyuser.controller;

import ch.batbern.companyuser.client.EventManagementMailCenterClient;
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
import ch.batbern.shared.types.Role;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * #1246: a participant asks for a new company logo, an organizer accepts or declines in the Mail
 * Center. The Mail Center conversation (event-management) is mocked here and tested there; S3 is the
 * Mockito mock from {@link TestAwsConfig}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
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
    @MockitoBean
    private EventManagementMailCenterClient mailCenter;

    @BeforeEach
    void setUp() {
        sesRecorder.reset();
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
        return UUID.fromString(objectMapper.readTree(body).path("requestId").asText());
    }

    // ---------------------------------------------------------------- the participant asks

    @Test
    @DisplayName("a request snapshots the old logo, keeps the upload out of the cleanup and opens a conversation")
    void request_storesAndOpensConversation() throws Exception {
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

        ArgumentCaptor<EventManagementMailCenterClient.OpenRequest> open =
                ArgumentCaptor.forClass(EventManagementMailCenterClient.OpenRequest.class);
        verify(mailCenter).openLogoRequest(open.capture());
        assertThat(open.getValue().requestId()).isEqualTo(id);
        assertThat(open.getValue().requesterEmail()).isEqualTo(REQUESTER);
        assertThat(open.getValue().subject()).contains("ACME AG");
        assertThat(open.getValue().note()).contains("Neues Logo seit Juni");

        // The company is untouched until an organizer decides.
        assertThat(companyRepository.findByName("acme").orElseThrow().getLogoUrl()).isEqualTo(OLD_LOGO);
    }

    @Test
    @DisplayName("the open request is shown on the profile; without one the endpoint answers 204")
    void openRequest_forProfile() throws Exception {
        mockMvc.perform(get("/api/v1/users/me/company-logo-requests/open").with(anna()))
                .andExpect(status().isNoContent());
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
        create(user("bert.ohne").roles("ATTENDEE"), "up-1", null, 409);
        assertThat(requestRepository.count()).isZero();
        verify(mailCenter, never()).openLogoRequest(any());
    }

    @Test
    @DisplayName("one open request per person and company: 409 LOGO_REQUEST_OPEN")
    void request_twice_conflict() throws Exception {
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
        // The class-level test transaction would hide the rollback: commit the fixture, let the
        // request run in its own transaction, then remove the committed fixture.
        TestTransaction.flagForCommit();
        TestTransaction.end();
        try {
            doThrow(new EventManagementMailCenterClient.MailCenterCallFailedException("down"))
                    .when(mailCenter).openLogoRequest(any());
            String body = create(anna(), "up-1", null, 502);
            assertThat(objectMapper.readTree(body).path("errorCode").asText()).isEqualTo("LOGO_REQUEST_NOT_DELIVERED");
            assertThat(requestRepository.count()).isZero();
            assertThat(logoRepository.findByUploadId("up-1").orElseThrow().getStatus()).isEqualTo(LogoStatus.CONFIRMED);
        } finally {
            requestRepository.deleteAll();
            logoRepository.findByUploadId("up-1").ifPresent(logoRepository::delete);
            logoRepository.findByUploadId("up-2").ifPresent(logoRepository::delete);
            companyRepository.findByName("acme").ifPresent(companyRepository::delete);
            for (String u : new String[] {"orga.nizer", "anna.muster", "bert.ohne"}) {
                userRepository.findByUsername(u).ifPresent(userRepository::delete);
            }
        }
    }

    // ---------------------------------------------------------------- organizers decide

    @Test
    @DisplayName("only organizers read and decide a request")
    void decide_participant_forbidden() throws Exception {
        UUID id = createOk();
        mockMvc.perform(get("/api/v1/users/company-logo-requests/{id}", id).with(anna()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/users/company-logo-requests/{id}/approve", id).with(anna()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/users/company-logo-requests/{id}/decline", id).with(anna())
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("the organizer view shows company, old and new logo and the note")
    void get_organizerView() throws Exception {
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
        verify(mailCenter).closeLogoRequest(id, "orga.nizer");
    }

    @Test
    @DisplayName("decline with a reason: the reason is mailed, the logo stays")
    void decline_withReason() throws Exception {
        UUID id = createOk();

        mockMvc.perform(post("/api/v1/users/company-logo-requests/{id}/decline", id).with(organizer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Bitte als SVG <senden>\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DECLINED"))
                .andExpect(jsonPath("$.reason").value("Bitte als SVG <senden>"));

        assertThat(companyRepository.findByName("acme").orElseThrow().getLogoUrl()).isEqualTo(OLD_LOGO);
        assertThat(sesRecorder.single().to()).containsExactly(REQUESTER);
        assertThat(sesRecorder.single().html()).contains("Bitte als SVG &lt;senden&gt;").contains("<blockquote>");
        verify(mailCenter).closeLogoRequest(id, "orga.nizer");
    }

    @Test
    @DisplayName("decline without a reason: the mail has no reason block")
    void decline_withoutReason() throws Exception {
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
        UUID id = createOk();
        mockMvc.perform(post("/api/v1/users/company-logo-requests/{id}/decline", id).with(organizer())
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/users/company-logo-requests/{id}/approve", id).with(organizer()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("LOGO_REQUEST_DECIDED"));
        create(anna(), "up-2", null, 201);
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
