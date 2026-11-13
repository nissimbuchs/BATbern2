package ch.batbern.companyuser.controller;

import ch.batbern.companyuser.config.TestAwsConfig;
import ch.batbern.companyuser.domain.CompanyUserUpload;
import ch.batbern.companyuser.domain.User;
import ch.batbern.companyuser.repository.CompanyUserUploadRepository;
import ch.batbern.companyuser.repository.UserRepository;
import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.shared.types.Role;
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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Story 17.11a: the upload contract for {@code profile-picture} through the real controller, upload
 * service, handler, ownership rule and tables. S3 is the boundary (the Mockito {@link S3Client} of
 * {@link TestAwsConfig}); what it received is read from its invocations. The three removed
 * {@code /users/{username}/picture*} upload operations are pinned to the status they now answer.
 *
 * <p>Not {@code @Transactional}: every request commits as in production; the rows are removed in
 * {@code @AfterEach}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestAwsConfig.class)
@DisplayName("Uploads: profile-picture (Story 17.11a)")
class ProfilePictureUploadIntegrationTest extends AbstractIntegrationTest {

    private static final String SPEAKER = "pic.speaker";
    private static final String SPEAKER_SUB = "6f1c2d3e-0000-4000-8000-0000000b1101";
    private static final String OTHER = "pic.other";
    private static final String ORGANIZER = "pic.organizer";
    private static final String OLD_PICTURE_KEY = "profile-pictures/2025/pic.other/profile-old.png";
    private static final List<String> USERS = List.of(SPEAKER, OTHER, ORGANIZER);

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CompanyUserUploadRepository uploadRepository;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private S3Client s3Client;

    @BeforeEach
    void setUp() {
        removeRows();
        Mockito.clearInvocations(s3Client);
        save(SPEAKER, "Pic", "Speaker", SPEAKER_SUB, Role.SPEAKER);
        save(OTHER, "Pic", "Other", "6f1c2d3e-0000-4000-8000-0000000b1102", Role.ATTENDEE);
        save(ORGANIZER, "Pic", "Organizer", "6f1c2d3e-0000-4000-8000-0000000b1103", Role.ORGANIZER);
        jdbc.update("UPDATE user_profiles SET profile_picture_url = ?, profile_picture_s3_key = ? WHERE username = ?",
                "https://cdn.batbern.ch/" + OLD_PICTURE_KEY, OLD_PICTURE_KEY, OTHER);
    }

    @AfterEach
    void tearDown() {
        removeRows();
    }

    private void removeRows() {
        jdbc.update("DELETE FROM company_user_uploads WHERE purpose = 'profile-picture'"
                + " AND (created_by IN ('pic.speaker', 'pic.other', 'pic.organizer', 'admin.user', '"
                + SPEAKER_SUB + "') OR upload_id LIKE 'pic-%')");
        for (String username : USERS) {
            jdbc.update("DELETE FROM user_profiles WHERE username = ?", username);
        }
    }

    private void save(String username, String first, String last, String sub, Role role) {
        userRepository.save(User.builder()
                .username(username)
                .email(username + "@example.com")
                .firstName(first)
                .lastName(last)
                .cognitoUserId(sub)
                .roles(new HashSet<>(Set.of(role)))
                .build());
    }

    private static RequestPostProcessor speaker() {
        return user(SPEAKER).roles("SPEAKER");
    }

    private static RequestPostProcessor organizer() {
        return user(ORGANIZER).roles("ORGANIZER");
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

    private static String target(String username) {
        return "{\"target\": {\"username\": \"" + username + "\"}}";
    }

    private String reserve(RequestPostProcessor who, String username) throws Exception {
        JsonNode slot = json(post("/api/v1/uploads/profile-picture"), who,
                "{\"fileName\": \"Portrait Brünnimann.png\", \"contentType\": \"image/png\", \"size\": 2048,"
                        + " \"target\": {\"username\": \"" + username + "\"}}", 201);
        return slot.path("uploadId").asText();
    }

    private List<String> copies() {
        return Mockito.mockingDetails(s3Client).getInvocations().stream()
                .filter(i -> i.getArguments().length == 1 && i.getArguments()[0] instanceof CopyObjectRequest)
                .map(i -> (CopyObjectRequest) i.getArguments()[0])
                .map(c -> c.sourceKey() + "->" + c.destinationKey())
                .toList();
    }

    private User stored(String username) {
        return userRepository.findByUsername(username).orElseThrow();
    }

    // ------------------------------------------------------------- own picture

    @Test
    @DisplayName("own picture: reserve, confirm with target; ASSOCIATED, key without year, shown on the profile")
    void should_attachPicture_when_speakerUploadsOwn() throws Exception {
        JsonNode slot = json(post("/api/v1/uploads/profile-picture"), speaker(),
                "{\"fileName\": \"portrait.jpg\", \"contentType\": \"image/jpeg\", \"size\": 2048,"
                        + " \"target\": {\"username\": \"" + SPEAKER + "\"}}", 201);
        String uploadId = slot.path("uploadId").asText();
        assertThat(slot.path("uploadUrl").asText()).startsWith("https://");
        assertThat(slot.path("requiredHeaders").path("Content-Type").asText()).isEqualTo("image/jpeg");
        CompanyUserUpload pending = uploadRepository.findByUploadId(uploadId).orElseThrow();
        assertThat(pending.getStatus()).isEqualTo(UploadStatus.PENDING);
        String temp = "profile-pictures/temp/" + uploadId + "/profile-" + uploadId + ".jpg";
        assertThat(pending.getS3Key()).isEqualTo(temp);

        JsonNode done = json(post("/api/v1/uploads/profile-picture/{id}/confirm", uploadId), speaker(),
                target(SPEAKER), 200);

        String key = "profile-pictures/" + SPEAKER + "/profile-" + uploadId + ".jpg";
        assertThat(done.path("status").asText()).isEqualTo("ASSOCIATED");
        assertThat(done.path("purpose").asText()).isEqualTo("profile-picture");
        assertThat(done.path("fileUrl").asText()).endsWith("/" + key);
        assertThat(stored(SPEAKER).getProfilePictureS3Key()).isEqualTo(key);
        assertThat(stored(SPEAKER).getProfilePictureUrl()).isEqualTo(done.path("fileUrl").asText());
        CompanyUserUpload upload = uploadRepository.findByUploadId(uploadId).orElseThrow();
        assertThat(upload.getTargetType()).isEqualTo("USER");
        assertThat(upload.getTargetId()).isEqualTo(SPEAKER);
        assertThat(copies()).containsExactly(temp + "->" + key);

        JsonNode profile = json(get("/api/v1/users/{username}", SPEAKER), organizer(), null, 200);
        assertThat(profile.path("profilePictureUrl").asText()).isEqualTo(done.path("fileUrl").asText());
    }

    @Test
    @DisplayName("own picture with a token that has no username claim, only the own subject: allowed")
    void should_attachPicture_when_tokenNamesUserBySubjectOnly() throws Exception {
        RequestPostProcessor bySubject = jwt().jwt(j -> j.subject(SPEAKER_SUB))
                .authorities(new SimpleGrantedAuthority("ROLE_SPEAKER"));
        String uploadId = reserve(bySubject, SPEAKER);

        json(post("/api/v1/uploads/profile-picture/{id}/confirm", uploadId), bySubject, target(SPEAKER), 200);

        assertThat(stored(SPEAKER).getProfilePictureS3Key()).contains(uploadId);
    }

    @Test
    @DisplayName("type not png/jpeg/svg or larger than 5 MB: 400, nothing stored")
    void should_return400_when_typeOrSizeNotAllowed() throws Exception {
        json(post("/api/v1/uploads/profile-picture"), speaker(),
                "{\"fileName\": \"cv.pdf\", \"contentType\": \"application/pdf\", \"size\": 2048,"
                        + " \"target\": {\"username\": \"" + SPEAKER + "\"}}", 400);
        json(post("/api/v1/uploads/profile-picture"), speaker(),
                "{\"fileName\": \"portrait.png\", \"contentType\": \"image/png\", \"size\": 5242881,"
                        + " \"target\": {\"username\": \"" + SPEAKER + "\"}}", 400);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM company_user_uploads WHERE created_by = ?",
                Integer.class, SPEAKER)).isZero();
    }

    @Test
    @DisplayName("no target, or a target of another purpose: 400 on create and on confirm")
    void should_return400_when_targetMissing() throws Exception {
        json(post("/api/v1/uploads/profile-picture"), speaker(),
                "{\"fileName\": \"portrait.png\", \"contentType\": \"image/png\", \"size\": 2048}", 400);
        json(post("/api/v1/uploads/profile-picture"), organizer(),
                "{\"fileName\": \"portrait.png\", \"contentType\": \"image/png\", \"size\": 2048,"
                        + " \"target\": {\"companyName\": \"acme\"}}", 400);

        String uploadId = reserve(speaker(), SPEAKER);
        json(post("/api/v1/uploads/profile-picture/{id}/confirm", uploadId), speaker(), "{}", 400);
        json(post("/api/v1/uploads/profile-picture/{id}/confirm", uploadId), speaker(), null, 400);

        assertThat(uploadRepository.findByUploadId(uploadId).orElseThrow().getStatus())
                .isEqualTo(UploadStatus.PENDING);
        assertThat(stored(SPEAKER).getProfilePictureUrl()).isNull();
    }

    @Test
    @DisplayName("anonymous: 401")
    void should_return401_when_anonymous() throws Exception {
        mockMvc.perform(post("/api/v1/uploads/profile-picture").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fileName\": \"p.png\", \"contentType\": \"image/png\", \"size\": 10,"
                                + " \"target\": {\"username\": \"" + SPEAKER + "\"}}"))
                .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------- another user's picture

    @Test
    @DisplayName("another user's picture: a speaker and an admin without ORGANIZER get 403 on create and confirm")
    void should_return403_when_nonOrganizerTargetsAnotherUser() throws Exception {
        String body = "{\"fileName\": \"p.png\", \"contentType\": \"image/png\", \"size\": 10,"
                + " \"target\": {\"username\": \"" + OTHER + "\"}}";
        json(post("/api/v1/uploads/profile-picture"), speaker(), body, 403);
        json(post("/api/v1/uploads/profile-picture"), user("admin.user").roles("ADMIN"), body, 403);

        String own = reserve(speaker(), SPEAKER);
        json(post("/api/v1/uploads/profile-picture/{id}/confirm", own), speaker(), target(OTHER), 403);

        assertThat(stored(OTHER).getProfilePictureS3Key()).isEqualTo(OLD_PICTURE_KEY);
        assertThat(uploadRepository.findByUploadId(own).orElseThrow().getStatus()).isEqualTo(UploadStatus.PENDING);
    }

    @Test
    @DisplayName("organizer: any existing user's picture is replaced; an unknown username is 404")
    void should_replacePicture_when_organizerTargetsAnotherUser() throws Exception {
        String uploadId = reserve(organizer(), OTHER);

        JsonNode done = json(post("/api/v1/uploads/profile-picture/{id}/confirm", uploadId), organizer(),
                target(OTHER), 200);

        assertThat(done.path("status").asText()).isEqualTo("ASSOCIATED");
        assertThat(stored(OTHER).getProfilePictureS3Key())
                .isEqualTo("profile-pictures/" + OTHER + "/profile-" + uploadId + ".png");
        assertThat(stored(OTHER).getProfilePictureUrl()).isEqualTo(done.path("fileUrl").asText());

        json(post("/api/v1/uploads/profile-picture"), organizer(),
                "{\"fileName\": \"p.png\", \"contentType\": \"image/png\", \"size\": 10,"
                        + " \"target\": {\"username\": \"nosuch.user\"}}", 404);
        String second = reserve(organizer(), OTHER);
        json(post("/api/v1/uploads/profile-picture/{id}/confirm", second), organizer(), target("nosuch.user"), 404);
    }

    // ------------------------------------------------------------- states

    @Test
    @DisplayName("confirm before the PUT (file not in S3): 409, the picture is unchanged")
    void should_return409_when_fileNotInStorage() throws Exception {
        String uploadId = reserve(organizer(), OTHER);
        Mockito.doThrow(NoSuchKeyException.builder().statusCode(404).build()).when(s3Client)
                .headObject(argThat((HeadObjectRequest r) -> r != null && r.key().contains(uploadId)));

        json(post("/api/v1/uploads/profile-picture/{id}/confirm", uploadId), organizer(), target(OTHER), 409);

        assertThat(stored(OTHER).getProfilePictureS3Key()).isEqualTo(OLD_PICTURE_KEY);
        assertThat(uploadRepository.findByUploadId(uploadId).orElseThrow().getStatus())
                .isEqualTo(UploadStatus.PENDING);
    }

    @Test
    @DisplayName("confirm of an attached upload again: 409")
    void should_return409_when_confirmedTwice() throws Exception {
        String uploadId = reserve(speaker(), SPEAKER);
        json(post("/api/v1/uploads/profile-picture/{id}/confirm", uploadId), speaker(), target(SPEAKER), 200);

        json(post("/api/v1/uploads/profile-picture/{id}/confirm", uploadId), speaker(), target(SPEAKER), 409);
    }

    // ------------------------------------------------------------- speaker batch import

    @Test
    @DisplayName("batch import: an imported (CONFIRMED) upload confirmed with the target becomes the picture")
    void should_attachPicture_when_importedUploadConfirmedWithTarget() throws Exception {
        // The state importFileUpload leaves (the fetch itself needs a reachable HTTPS host).
        jdbc.update("INSERT INTO company_user_uploads (upload_id, purpose, s3_key, file_extension, file_size,"
                + " content_type, status, created_by, expires_at) VALUES ('pic-imported', 'profile-picture',"
                + " 'profile-pictures/temp/pic-imported/profile-pic-imported.jpeg', 'jpeg', 2048, 'image/jpeg',"
                + " 'confirmed', ?, now() + interval '7 days')", ORGANIZER);

        JsonNode done = json(post("/api/v1/uploads/profile-picture/{id}/confirm", "pic-imported"), organizer(),
                target(SPEAKER), 200);

        String key = "profile-pictures/" + SPEAKER + "/profile-pic-imported.jpeg";
        assertThat(done.path("status").asText()).isEqualTo("ASSOCIATED");
        assertThat(stored(SPEAKER).getProfilePictureS3Key()).isEqualTo(key);
        assertThat(copies()).containsExactly("profile-pictures/temp/pic-imported/profile-pic-imported.jpeg->" + key);
    }

    @Test
    @DisplayName("import: a non-organizer 403, a non-HTTPS URL 400, an unreachable one 502")
    void should_refuseImport_when_callerOrUrlNotAllowed() throws Exception {
        json(post("/api/v1/uploads/profile-picture/import"), speaker(),
                "{\"url\": \"https://example.com/portrait.png\"}", 403);
        json(post("/api/v1/uploads/profile-picture/import"), organizer(),
                "{\"url\": \"http://example.com/portrait.png\"}", 400);
        json(post("/api/v1/uploads/profile-picture/import"), organizer(),
                "{\"url\": \"https://localhost:1/portrait.png\"}", 502);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM company_user_uploads WHERE purpose = 'profile-picture'"
                + " AND created_by IN (?, ?)", Integer.class, SPEAKER, ORGANIZER)).isZero();
    }

    // ------------------------------------------------------------- the removed operations

    @Test
    @DisplayName("the three removed picture upload operations answer 404, nothing changes")
    void should_return404_when_removedPictureUploadPathIsCalled() throws Exception {
        mockMvc.perform(post("/api/v1/users/{username}/picture/presigned-url", OTHER).with(organizer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fileName\": \"p.png\", \"fileSize\": 2048, \"mimeType\": \"image/png\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/users/{username}/picture/confirm", OTHER).with(organizer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fileId\": \"old-path-file\", \"fileExtension\": \"png\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/users/{username}/profile-picture/upload-from-url", OTHER).with(organizer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\": \"https://example.com/portrait.png\"}"))
                .andExpect(status().isNotFound());

        assertThat(stored(OTHER).getProfilePictureS3Key()).isEqualTo(OLD_PICTURE_KEY);
    }
}
