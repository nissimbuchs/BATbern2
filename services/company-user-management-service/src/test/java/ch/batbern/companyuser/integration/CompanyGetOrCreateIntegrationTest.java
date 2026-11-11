package ch.batbern.companyuser.integration;

import ch.batbern.companyuser.config.TestAwsConfig;
import ch.batbern.companyuser.domain.Company;
import ch.batbern.companyuser.repository.CompanyRepository;
import ch.batbern.shared.test.AbstractIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Story 4.10d (ADR-013 §2): {@code POST /companies/get-or-create} answers 201 when it creates the
 * company and 200 when it returns an existing one; the colon form {@code /companies:get-or-create}
 * is gone, no alias. Every test commits; the companies it creates or seeds carry the creator
 * {@value #CALLER} and are removed before and after each test.
 */
@Import(TestAwsConfig.class)
@DisplayName("Story 4.10d: POST /companies/get-or-create")
class CompanyGetOrCreateIntegrationTest extends AbstractIntegrationTest {

    private static final String CALLER = "getorcreate.it";
    private static final String PATH = "/api/v1/companies/get-or-create";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private DataSource dataSource;

    @BeforeEach
    @AfterEach
    void removeCreated() {
        jdbc.update("DELETE FROM companies WHERE created_by = ?", CALLER);
    }

    private static String body(String displayName) {
        return "{\"displayName\": \"" + displayName + "\"}";
    }

    private long countByName(String name) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM companies WHERE name = ?", Long.class, name);
    }

    @Test
    @WithMockUser(username = CALLER, roles = {"ATTENDEE"})
    @DisplayName("a new display name creates the company: 201, then the same name again: 200, no duplicate")
    void should_return201ThenSame200_when_newDisplayNameRepeated() throws Exception {
        // umlaut and ampersand: the 17.0d shape, slug "bruennimanns" (ADR-003, max 12, a-z0-9)
        String displayName = "Brünnimann & Söhne AG";
        String name = "bruennimanns";
        assertThat(countByName(name)).isZero();

        mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(body(displayName)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value(name))
                .andExpect(jsonPath("$.name").value(matchesPattern("^[a-z0-9]{1,12}$")))
                .andExpect(jsonPath("$.displayName").value(displayName))
                .andExpect(jsonPath("$.createdBy").value(CALLER));
        assertThat(countByName(name)).isEqualTo(1);

        mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(body(displayName)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(name));
        assertThat(countByName(name)).isEqualTo(1);
    }

    @Test
    @DisplayName("race: the same name committed by another transaction after the read: 200, that company, one row")
    void should_return200WithTheOtherCompany_when_sameNameCommittedConcurrently() throws Exception {
        // Deterministic interleaving: a second connection inserts the row and holds it uncommitted,
        // so the request's read does not see it and its insert waits on the unique index; then the
        // second connection commits and the request's insert fails on companies.name.
        String name = "racecompany";
        try (Connection other = dataSource.getConnection()) {
            other.setAutoCommit(false);
            try (PreparedStatement insert = other.prepareStatement(
                    "INSERT INTO companies (id, name, display_name, is_verified, created_by) "
                            + "VALUES (?, ?, 'Race Company', TRUE, ?)")) {
                insert.setObject(1, UUID.randomUUID());
                insert.setString(2, name);
                insert.setString(3, CALLER);
                insert.executeUpdate();
            }

            ExecutorService executor = Executors.newSingleThreadExecutor();
            try {
                Future<MvcResult> request = executor.submit(() -> mockMvc.perform(post(PATH)
                                .with(user(CALLER).roles("ATTENDEE"))
                                .contentType(MediaType.APPLICATION_JSON).content(body("Race Company")))
                        .andReturn());

                // wait until the request's insert waits on the other connection's row lock
                long deadline = System.currentTimeMillis() + 20_000;
                while (jdbc.queryForObject("SELECT count(*) FROM pg_locks WHERE NOT granted", Long.class) == 0) {
                    assertThat(request.isDone()).as("the request must wait for the uncommitted row").isFalse();
                    assertThat(System.currentTimeMillis()).isLessThan(deadline);
                    Thread.sleep(20);
                }
                other.commit();

                MvcResult result = request.get(20, TimeUnit.SECONDS);
                assertThat(result.getResponse().getStatus()).isEqualTo(200);
                String json = result.getResponse().getContentAsString();
                assertThat((String) JsonPath.read(json, "$.name")).isEqualTo(name);
                assertThat((String) JsonPath.read(json, "$.displayName")).isEqualTo("Race Company");
                // the other transaction's company (it stored isVerified true; a create would store false)
                assertThat((Boolean) JsonPath.read(json, "$.isVerified")).isTrue();
            } finally {
                executor.shutdownNow();
            }
        }
        assertThat(countByName(name)).isEqualTo(1);
    }

    @Test
    @WithMockUser(username = CALLER, roles = {"PARTNER"})
    @DisplayName("an existing company found by its generated name: 200, the stored one, nothing created")
    void should_return200_when_companyExists() throws Exception {
        companyRepository.save(Company.builder()
                .name("sluggedco")
                .displayName("Slugged Co")
                .isVerified(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .createdBy(CALLER)
                .build());
        long before = companyRepository.count();

        mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(body("Slugged Co")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("sluggedco"))
                .andExpect(jsonPath("$.isVerified").value(true));

        assertThat(companyRepository.count()).isEqualTo(before);
    }

    @Test
    @WithMockUser(username = CALLER, roles = {"ATTENDEE"})
    @DisplayName("a blank display name: 400, nothing created")
    void should_return400_when_displayNameBlank() throws Exception {
        long before = companyRepository.count();

        mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(body("")))
                .andExpect(status().isBadRequest());

        assertThat(companyRepository.count()).isEqualTo(before);
    }

    @Test
    @DisplayName("anonymous: 401, nothing created")
    void should_return401_when_anonymous() throws Exception {
        mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(body("Anon Probe AG")))
                .andExpect(status().isUnauthorized());

        assertThat(countByName("anonprobeag")).isZero();
    }

    @Test
    @WithMockUser(username = CALLER, roles = {"ATTENDEE"})
    @DisplayName("the colon form is gone, no alias: 404, nothing created")
    void should_return404_when_colonFormCalled() throws Exception {
        mockMvc.perform(post("/api/v1/companies:get-or-create")
                        .contentType(MediaType.APPLICATION_JSON).content(body("Colon Probe AG")))
                .andExpect(status().isNotFound());

        assertThat(countByName("colonprobeag")).isZero();
    }
}
