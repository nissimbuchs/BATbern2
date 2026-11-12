package ch.batbern.companyuser.controller;

import ch.batbern.companyuser.config.TestAwsConfig;
import ch.batbern.companyuser.domain.Company;
import ch.batbern.companyuser.domain.CompanyUserUpload;
import ch.batbern.companyuser.repository.CompanyRepository;
import ch.batbern.companyuser.repository.CompanyUserUploadRepository;
import ch.batbern.companyuser.service.UploadExpiryJob;
import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.shared.upload.UploadService;
import ch.batbern.shared.upload.UploadStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

import java.time.Instant;
import java.time.Year;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Story 17.11: the upload contract for {@code company-logo} through the real controller, upload
 * service, handler, company service and table. S3 is the boundary (the Mockito {@link S3Client} of
 * {@link TestAwsConfig}); what it received is read from its invocations.
 *
 * <p>Not {@code @Transactional}: every request commits as in production; the rows are removed in
 * {@code @AfterEach}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestAwsConfig.class)
@DisplayName("Uploads: company-logo (Story 17.11)")
class UploadsControllerIntegrationTest extends AbstractIntegrationTest {

    private static final String COMPANY = "uplacme";
    private static final String OLD_LOGO = "https://cdn.batbern.ch/logos/2024/companies/uplacme/logo-old.png";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private CompanyRepository companyRepository;
    @Autowired
    private CompanyUserUploadRepository uploadRepository;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private S3Client s3Client;
    @Autowired
    private UploadExpiryJob expiryJob;

    @BeforeEach
    void setUp() {
        removeRows();
        Mockito.clearInvocations(s3Client);
        companyRepository.saveAndFlush(Company.builder().name(COMPANY).displayName("Upload ACME AG")
                .logoUrl(OLD_LOGO).logoS3Key("logos/2024/companies/uplacme/logo-old.png").logoFileId("old")
                .createdBy("orga.nizer").createdAt(Instant.now()).updatedAt(Instant.now()).build());
    }

    @AfterEach
    void tearDown() {
        removeRows();
    }

    private void removeRows() {
        jdbc.update("DELETE FROM company_user_uploads WHERE created_by IN ('orga.nizer', 'anna.muster')"
                + " OR upload_id LIKE 'exp-%'");
        jdbc.update("DELETE FROM companies WHERE name = ? OR display_name = 'Ølund Logo AG'", COMPANY);
    }

    private static RequestPostProcessor organizer() {
        return user("orga.nizer").roles("ORGANIZER");
    }

    private static RequestPostProcessor attendee() {
        return user("anna.muster").roles("ATTENDEE");
    }

    private JsonNode json(MockHttpServletRequestBuilder request, RequestPostProcessor who, String body,
            int expectedStatus) throws Exception {
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        String response = mockMvc.perform(request.with(who))
                .andExpect(status().is(expectedStatus))
                .andReturn().getResponse().getContentAsString();
        return response.isEmpty() ? null : objectMapper.readTree(response);
    }

    private String reserve(RequestPostProcessor who, String extra) throws Exception {
        JsonNode slot = json(post("/api/v1/uploads/company-logo"), who,
                "{\"fileName\": \"Logo Ölund.png\", \"contentType\": \"image/png\", \"size\": 2048" + extra + "}",
                201);
        return slot.path("uploadId").asText();
    }

    private List<String> s3Calls(Class<?> requestType) {
        return Mockito.mockingDetails(s3Client).getInvocations().stream()
                .filter(i -> i.getArguments().length == 1 && requestType.isInstance(i.getArguments()[0]))
                .map(i -> i.getArguments()[0] instanceof CopyObjectRequest c
                        ? c.sourceKey() + "->" + c.destinationKey()
                        : ((DeleteObjectRequest) i.getArguments()[0]).key())
                .toList();
    }

    // ------------------------------------------------------------- create form: confirm without target

    @Test
    @DisplayName("create form: reserve, confirm, create the company with logoUploadId; the logo is attached")
    void should_attachLogo_when_companyCreatedWithConfirmedUpload() throws Exception {
        JsonNode slot = json(post("/api/v1/uploads/company-logo"), organizer(),
                "{\"fileName\": \"logo.svg\", \"contentType\": \"image/svg+xml\", \"size\": 2048}", 201);
        String uploadId = slot.path("uploadId").asText();
        assertThat(slot.path("uploadUrl").asText()).startsWith("https://");
        assertThat(slot.path("requiredHeaders").path("Content-Type").asText()).isEqualTo("image/svg+xml");
        assertThat(uploadRepository.findByUploadId(uploadId).orElseThrow().getStatus())
                .isEqualTo(UploadStatus.PENDING);

        json(post("/api/v1/uploads/company-logo/{id}/confirm", uploadId), organizer(), "{}", 200);
        assertThat(uploadRepository.findByUploadId(uploadId).orElseThrow().getStatus())
                .isEqualTo(UploadStatus.CONFIRMED);

        JsonNode company = json(post("/api/v1/companies"), organizer(),
                "{\"name\": \"Ølund Logo\", \"displayName\": \"Ølund Logo AG\", \"logoUploadId\": \"" + uploadId
                        + "\"}", 201);

        Company stored = companyRepository.findByName(company.path("name").asText()).orElseThrow();
        String key = "logos/" + Year.now(ZoneOffset.UTC) + "/companies/" + stored.getName()
                + "/logo-" + uploadId + ".svg";
        assertThat(stored.getLogoS3Key()).isEqualTo(key);
        assertThat(stored.getLogoUrl()).endsWith("/" + key);
        assertThat(stored.getLogoFileId()).isEqualTo(uploadId);
        CompanyUserUpload upload = uploadRepository.findByUploadId(uploadId).orElseThrow();
        assertThat(upload.getStatus()).isEqualTo(UploadStatus.ASSOCIATED);
        assertThat(upload.getTargetId()).isEqualTo(stored.getName());
        assertThat(s3Calls(CopyObjectRequest.class)).containsExactly(
                "logos/temp/" + uploadId + "/logo-" + uploadId + ".svg->" + key);
    }

    @Test
    @DisplayName("any signed-in user may upload without a target (the logo request of an attendee)")
    void should_reserveAndConfirm_when_attendeeUploadsWithoutTarget() throws Exception {
        String uploadId = reserve(attendee(), "");

        JsonNode confirmed = json(post("/api/v1/uploads/company-logo/{id}/confirm", uploadId), attendee(), null,
                200);

        assertThat(confirmed.path("status").asText()).isEqualTo("CONFIRMED");
        assertThat(confirmed.path("fileUrl").isNull()).isTrue();
    }

    @Test
    @DisplayName("type not png/jpeg/svg or larger than 5 MB: 400, nothing stored")
    void should_return400_when_typeOrSizeNotAllowed() throws Exception {
        json(post("/api/v1/uploads/company-logo"), organizer(),
                "{\"fileName\": \"logo.gif\", \"contentType\": \"image/gif\", \"size\": 2048}", 400);
        json(post("/api/v1/uploads/company-logo"), organizer(),
                "{\"fileName\": \"logo.png\", \"contentType\": \"image/png\", \"size\": 5242881}", 400);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM company_user_uploads WHERE created_by = 'orga.nizer'",
                Integer.class)).isZero();
    }

    @Test
    @DisplayName("anonymous: 401")
    void should_return401_when_anonymous() throws Exception {
        mockMvc.perform(post("/api/v1/uploads/company-logo").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fileName\": \"logo.png\", \"contentType\": \"image/png\", \"size\": 10}"))
                .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------- existing company: confirm with target

    @Test
    @DisplayName("existing company: confirm with target companyName replaces the logo at once")
    void should_replaceLogo_when_confirmedWithCompanyTarget() throws Exception {
        String uploadId = reserve(organizer(), ", \"target\": {\"companyName\": \"" + COMPANY + "\"}");

        JsonNode done = json(post("/api/v1/uploads/company-logo/{id}/confirm", uploadId), organizer(),
                "{\"target\": {\"companyName\": \"" + COMPANY + "\"}}", 200);

        assertThat(done.path("status").asText()).isEqualTo("ASSOCIATED");
        Company company = companyRepository.findByName(COMPANY).orElseThrow();
        assertThat(company.getLogoUrl()).isNotEqualTo(OLD_LOGO)
                .isEqualTo(done.path("fileUrl").asText())
                .contains("/companies/" + COMPANY + "/logo-" + uploadId + ".png");
        assertThat(company.getLogoFileId()).isEqualTo(uploadId);
    }

    @Test
    @DisplayName("edit form: PATCH logoUploadId with a confirmed upload replaces the logo; the same id again changes nothing")
    void should_replaceLogo_when_companyPatchedWithConfirmedUpload() throws Exception {
        String uploadId = reserve(organizer(), "");
        json(post("/api/v1/uploads/company-logo/{id}/confirm", uploadId), organizer(), null, 200);

        json(patch("/api/v1/companies/{name}", COMPANY), organizer(), "{\"logoUploadId\": \"" + uploadId + "\"}",
                200);
        Company company = companyRepository.findByName(COMPANY).orElseThrow();
        assertThat(company.getLogoFileId()).isEqualTo(uploadId);
        assertThat(company.getLogoUrl()).contains("/companies/" + COMPANY + "/logo-" + uploadId + ".png");

        json(patch("/api/v1/companies/{name}", COMPANY), organizer(), "{\"logoUploadId\": \"" + uploadId + "\"}",
                200);
        assertThat(companyRepository.findByName(COMPANY).orElseThrow().getLogoUrl()).isEqualTo(company.getLogoUrl());
    }

    @Test
    @DisplayName("create form: a logo upload that is not CONFIRMED fails the create, nothing is stored")
    void should_return409AndNotCreate_when_logoUploadOnlyPending() throws Exception {
        String uploadId = reserve(organizer(), "");

        json(post("/api/v1/companies"), organizer(),
                "{\"name\": \"Ølund Logo\", \"displayName\": \"Ølund Logo AG\", \"logoUploadId\": \"" + uploadId
                        + "\"}", 409);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM companies WHERE display_name = 'Ølund Logo AG'",
                Integer.class)).isZero();
    }

    @Test
    @DisplayName("existing company: unknown company 404, an attendee 403, a target of another purpose 400")
    void should_refuse_when_targetUnknownOrNotAllowed() throws Exception {
        json(post("/api/v1/uploads/company-logo"), organizer(),
                "{\"fileName\": \"a.png\", \"contentType\": \"image/png\", \"size\": 10,"
                        + " \"target\": {\"companyName\": \"nosuchco\"}}", 404);
        json(post("/api/v1/uploads/company-logo"), attendee(),
                "{\"fileName\": \"a.png\", \"contentType\": \"image/png\", \"size\": 10,"
                        + " \"target\": {\"companyName\": \"" + COMPANY + "\"}}", 403);
        json(post("/api/v1/uploads/company-logo"), organizer(),
                "{\"fileName\": \"a.png\", \"contentType\": \"image/png\", \"size\": 10,"
                        + " \"target\": {\"eventCode\": \"BATbern1\"}}", 400);

        String uploadId = reserve(attendee(), "");
        json(post("/api/v1/uploads/company-logo/{id}/confirm", uploadId), attendee(),
                "{\"target\": {\"companyName\": \"" + COMPANY + "\"}}", 403);
        assertThat(companyRepository.findByName(COMPANY).orElseThrow().getLogoUrl()).isEqualTo(OLD_LOGO);
    }

    // ------------------------------------------------------------- states

    @Test
    @DisplayName("confirm of an attached upload: 409; of a missing file: 409; of an expired or unknown one: 404")
    void should_refuse_when_uploadNotInAConfirmableState() throws Exception {
        String attached = reserve(organizer(), "");
        json(post("/api/v1/uploads/company-logo/{id}/confirm", attached), organizer(),
                "{\"target\": {\"companyName\": \"" + COMPANY + "\"}}", 200);
        json(post("/api/v1/uploads/company-logo/{id}/confirm", attached), organizer(), null, 409);

        String missing = reserve(organizer(), "");
        Mockito.doThrow(NoSuchKeyException.builder().statusCode(404).build()).when(s3Client)
                .headObject(argThat((HeadObjectRequest r) -> r != null && r.key().contains(missing)));
        json(post("/api/v1/uploads/company-logo/{id}/confirm", missing), organizer(), null, 409);

        String expired = reserve(organizer(), "");
        jdbc.update("UPDATE company_user_uploads SET expires_at = now() - interval '1 minute' WHERE upload_id = ?",
                expired);
        json(post("/api/v1/uploads/company-logo/{id}/confirm", expired), organizer(), null, 404);
        json(post("/api/v1/uploads/company-logo/{id}/confirm", "nope"), organizer(), null, 404);
    }

    @Test
    @DisplayName("a purpose of another service reaching this one: 404")
    void should_return404_when_purposeBelongsToAnotherService() throws Exception {
        json(post("/api/v1/uploads/event-theme-image"), organizer(),
                "{\"fileName\": \"a.png\", \"contentType\": \"image/png\", \"size\": 10}", 404);
        String uploadId = reserve(organizer(), "");
        json(post("/api/v1/uploads/event-theme-image/{id}/confirm", uploadId), organizer(), null, 404);
    }

    // ------------------------------------------------------------- import

    @Test
    @DisplayName("import: a non-HTTPS URL 400, an unreachable one 502, an attendee 403")
    void should_refuseImport_when_urlOrCallerNotAllowed() throws Exception {
        json(post("/api/v1/uploads/company-logo/import"), organizer(),
                "{\"url\": \"http://example.com/logo.png\"}", 400);
        json(post("/api/v1/uploads/company-logo/import"), organizer(),
                "{\"url\": \"https://localhost:1/logo.png\"}", 502);
        json(post("/api/v1/uploads/company-logo/import"), attendee(),
                "{\"url\": \"https://example.com/logo.png\"}", 403);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM company_user_uploads WHERE created_by = 'orga.nizer'",
                Integer.class)).isZero();
    }

    // ------------------------------------------------------------- expiry

    @Test
    @DisplayName("expiry: PENDING past 24 h and CONFIRMED past 7 d go with their S3 object, ASSOCIATED stays")
    void should_deleteExpiredUploads_when_expiryJobRuns() {
        insert("exp-pending", "pending", "now() - interval '1 minute'", null);
        insert("exp-confirmed", "confirmed", "now() - interval '1 minute'", null);
        insert("exp-fresh", "pending", "now() + interval '1 hour'", null);
        insert("exp-attached", "associated", "NULL", COMPANY);

        UploadService.ExpiryResult result = expiryJob.purgeExpired();

        assertThat(result.deleted()).isGreaterThanOrEqualTo(2);
        assertThat(jdbc.queryForList("SELECT upload_id FROM company_user_uploads WHERE upload_id LIKE 'exp-%'",
                String.class)).containsExactlyInAnyOrder("exp-fresh", "exp-attached");
        assertThat(s3Calls(DeleteObjectRequest.class))
                .contains("logos/temp/exp-pending/logo.png", "logos/temp/exp-confirmed/logo.png")
                .doesNotContain("logos/temp/exp-fresh/logo.png", "logos/temp/exp-attached/logo.png");
    }

    private void insert(String uploadId, String status, String expiresAt, String target) {
        jdbc.update("INSERT INTO company_user_uploads (upload_id, purpose, s3_key, file_url, file_extension,"
                + " file_size, content_type, status, target_type, target_id, expires_at) VALUES (?, 'company-logo',"
                + " ?, ?, 'png', 10, 'image/png', ?, ?, ?, " + expiresAt + ")",
                uploadId, "logos/temp/" + uploadId + "/logo.png", target == null ? null : "https://cdn/x.png",
                status, target == null ? null : "COMPANY", target);
    }

    // ------------------------------------------------------------- the removed /logos operations

    @Test
    @DisplayName("the six removed /logos operations answer 404")
    void should_return404_when_removedLogoPathIsCalled() throws Exception {
        mockMvc.perform(post("/api/v1/logos/presigned-url").with(organizer())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/logos/{id}/confirm", "x").with(organizer())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/logos/{id}", "x").with(organizer()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/logos/cleanup/statistics").with(organizer()))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/logos/cleanup/trigger").with(organizer()))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/logos/upload-from-url").with(organizer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"url\": \"https://example.com/a.png\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }
}
