package ch.batbern.companyuser.controller;

import ch.batbern.companyuser.config.TestAwsConfig;
import ch.batbern.companyuser.domain.Company;
import ch.batbern.companyuser.domain.Logo;
import ch.batbern.companyuser.domain.LogoStatus;
import ch.batbern.companyuser.domain.User;
import ch.batbern.companyuser.repository.CompanyRepository;
import ch.batbern.companyuser.repository.LogoRepository;
import ch.batbern.companyuser.repository.UserRepository;
import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.shared.types.Role;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.assertj.core.data.TemporalUnitOffset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Update responses carry the {@code updatedAt} the row got from this change.
 *
 * <p>{@code User} and {@code Company} set {@code updatedAt} in {@code @PreUpdate}, which Hibernate
 * runs at flush. {@code UserService} and {@code CompanyService} build the response inside their
 * transaction, so without a flush the response showed the previous {@code updatedAt} (users) or
 * one the flush then overwrote (companies, and a new company whose logo is set by a second save).
 * users-api and companies-api require {@code createdAt} and {@code updatedAt}.
 *
 * <p>No class-level {@code @Transactional}: each request commits as in production, and the
 * stored value is read back from the database.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestAwsConfig.class)
@DisplayName("Update responses carry the stored updatedAt")
class ResponseTimestampsIntegrationTest extends AbstractIntegrationTest {

    private static final String USERNAME = "hong.an";
    private static final String COMPANY = "brunnimann";
    private static final String UPLOAD_ID = "ts-up-1";
    /** The JVM clock has nanoseconds, PostgreSQL rounds to microseconds. */
    private static final TemporalUnitOffset DB_PRECISION = within(1, ChronoUnit.MICROS);

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CompanyRepository companyRepository;
    @Autowired
    private LogoRepository logoRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private CacheManager cacheManager;

    private List<String> companiesBefore;
    private Instant userInsertedAt;
    private Instant companyInsertedAt;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM watch_pairings");
        jdbcTemplate.update("DELETE FROM user_profiles");
        jdbcTemplate.update("DELETE FROM companies WHERE name = ?", COMPANY);
        companiesBefore = jdbcTemplate.queryForList("SELECT name FROM companies", String.class);

        userInsertedAt = userRepository.save(User.builder()
                .username(USERNAME)
                .email("Hong.An@example.com")
                .firstName("Hông")
                .lastName("Ân")
                .cognitoUserId(USERNAME)
                .roles(new HashSet<>(Set.of(Role.SPEAKER)))
                .build()).getUpdatedAt();
        companyInsertedAt = companyRepository.save(Company.builder()
                .name(COMPANY)
                .displayName("Brünnimann AG")
                .isVerified(false)
                .createdBy("test-user")
                .build()).getUpdatedAt();
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM logos WHERE upload_id = ?", UPLOAD_ID);
        jdbcTemplate.update("DELETE FROM watch_pairings");
        jdbcTemplate.update("DELETE FROM user_profiles");
        jdbcTemplate.queryForList("SELECT name FROM companies", String.class).stream()
                .filter(name -> !companiesBefore.contains(name))
                .forEach(name -> jdbcTemplate.update("DELETE FROM companies WHERE name = ?", name));
        cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
    }

    @Test
    @WithMockUser(username = USERNAME)
    void should_returnStoredUpdatedAt_when_currentUserPatched() throws Exception {
        JsonNode body = call(patch("/api/v1/users/me"), "{\"bio\": \"Neue Bio\"}");

        assertUserUpdatedAt(body);
    }

    @Test
    @WithMockUser(username = "organizer", roles = {"ORGANIZER"})
    void should_returnStoredUpdatedAt_when_organizerUpdatesUser() throws Exception {
        JsonNode body = call(put("/api/v1/users/{username}", USERNAME), "{\"firstName\": \"Hông Thị\"}");

        assertUserUpdatedAt(body);
    }

    @Test
    @WithMockUser(username = "organizer", roles = {"ORGANIZER"})
    void should_returnStoredUpdatedAt_when_userProfilePatched() throws Exception {
        JsonNode body = call(patch("/api/v1/users/{username}/profile", USERNAME), "{\"bio\": \"Neue Bio\"}");

        assertUserUpdatedAt(body);
    }

    @Test
    void should_returnStoredUpdatedAt_when_getOrCreateRefreshesCompany() throws Exception {
        JsonNode body = call(post("/internal/users/get-or-create"), """
                {"email": "hong.an@example.com", "firstName": "Hông", "lastName": "Ân",
                 "companyId": "Brünnimann AG", "cognitoSync": false}""");

        assertThat(body.get("companyUpdated").asBoolean()).as("positive control: the user changed").isTrue();
        assertUserUpdatedAt(body.get("user"));
    }

    @Test
    @WithMockUser(username = "organizer", roles = {"ORGANIZER"})
    void should_returnStoredUpdatedAt_when_companyPatched() throws Exception {
        JsonNode body = call(patch("/api/v1/companies/{name}", COMPANY), "{\"industry\": \"Architektur\"}");

        assertCompanyUpdatedAt(body);
    }

    @Test
    @WithMockUser(username = "organizer", roles = {"ORGANIZER"})
    void should_returnStoredTimestamps_when_companyCreatedWithLogo() throws Exception {
        // The logo is set on the new row in a second save, an update before the insert is flushed:
        // @PreUpdate then moves updatedAt after the response was built.
        logoRepository.saveAndFlush(Logo.builder().uploadId(UPLOAD_ID)
                .s3Key("logos/temp/" + UPLOAD_ID + "/logo-f.png").fileExtension("png").fileSize(1000L)
                .mimeType("image/png").status(LogoStatus.CONFIRMED).expiresAt(Instant.now().plusSeconds(3600))
                .build());

        String response = mockMvc.perform(post("/api/v1/companies").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Ølund\", \"displayName\": \"Ølund Partner\", \"logoUploadId\": \""
                                + UPLOAD_ID + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode body = objectMapper.readTree(response);

        Company stored = companyRepository.findByName(body.get("name").asText()).orElseThrow();
        assertThat(stored.getLogoFileId()).as("positive control: the logo was associated").isEqualTo(UPLOAD_ID);
        assertThat(instant(body, "createdAt")).isCloseTo(stored.getCreatedAt(), DB_PRECISION);
        assertThat(instant(body, "updatedAt")).isCloseTo(stored.getUpdatedAt(), DB_PRECISION);
    }

    @Test
    @WithMockUser(username = "organizer", roles = {"ORGANIZER"})
    void should_returnStoredUpdatedAt_when_companyVerified() throws Exception {
        JsonNode body = call(post("/api/v1/companies/{name}/verify", COMPANY), null);

        assertCompanyUpdatedAt(body);
    }

    private JsonNode call(MockHttpServletRequestBuilder request, String json) throws Exception {
        if (json != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        String response = mockMvc.perform(request).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response);
    }

    private void assertUserUpdatedAt(JsonNode user) {
        Instant stored = userRepository.findByUsername(USERNAME).orElseThrow().getUpdatedAt();
        assertTimestamp(user, userInsertedAt, stored);
    }

    private void assertCompanyUpdatedAt(JsonNode company) {
        Instant stored = companyRepository.findByName(COMPANY).orElseThrow().getUpdatedAt();
        assertTimestamp(company, companyInsertedAt, stored);
    }

    private static Instant instant(JsonNode body, String field) {
        assertThat(body.hasNonNull(field)).as("%s in %s", field, body).isTrue();
        return OffsetDateTime.parse(body.get(field).asText()).toInstant();
    }

    private static void assertTimestamp(JsonNode body, Instant insertedAt, Instant stored) {
        assertThat(body.hasNonNull("updatedAt")).as("updatedAt in %s", body).isTrue();
        Instant returned = instant(body, "updatedAt");
        assertThat(stored).as("positive control: the update moved the stored updatedAt").isAfter(insertedAt);
        assertThat(returned).as("response updatedAt is the stored one").isCloseTo(stored, DB_PRECISION);
    }
}
