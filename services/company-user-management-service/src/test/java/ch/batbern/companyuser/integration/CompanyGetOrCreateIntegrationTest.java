package ch.batbern.companyuser.integration;

import ch.batbern.companyuser.config.TestAwsConfig;
import ch.batbern.companyuser.domain.Company;
import ch.batbern.companyuser.repository.CompanyRepository;
import ch.batbern.shared.test.AbstractIntegrationTest;
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

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
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
