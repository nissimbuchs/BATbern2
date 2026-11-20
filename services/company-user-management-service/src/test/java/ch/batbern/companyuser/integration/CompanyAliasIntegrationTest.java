package ch.batbern.companyuser.integration;

import ch.batbern.companyuser.config.TestAwsConfig;
import ch.batbern.companyuser.domain.Company;
import ch.batbern.companyuser.domain.CompanyAlias;
import ch.batbern.companyuser.repository.CompanyRepository;
import ch.batbern.companyuser.service.CompanyAliasService;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Story 2 of the company-merge epic (#1251): an old company key and display name, kept as an alias
 * after a merge or a rename, still find the company in search and typeahead, and get-or-create
 * returns the company instead of creating the duplicate again. Every test commits; the companies
 * carry the creator {@value #CALLER} and are removed (with their aliases) before and after each test.
 */
@Import(TestAwsConfig.class)
@DisplayName("#1251: an old company name stays findable as an alias")
class CompanyAliasIntegrationTest extends AbstractIntegrationTest {

    private static final String CALLER = "companyalias.it";
    private static final String TARGET = "alitarget";
    private static final String TARGET_DISPLAY = "Alias Target Holding";
    private static final String ALIAS_KEY = "alioldkey";
    private static final String ALIAS_DISPLAY = "Zebrafink Ost AG";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private CompanyAliasService aliasService;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void seedTarget() {
        removeCreated();
        companyRepository.save(Company.builder()
                .name(TARGET)
                .displayName(TARGET_DISPLAY)
                .isVerified(true)
                .createdBy(CALLER)
                .build());
    }

    @AfterEach
    void removeCreated() {
        jdbc.update("DELETE FROM companies WHERE created_by = ?", CALLER);
    }

    private void recordAlias() {
        aliasService.record(TARGET, ALIAS_KEY, ALIAS_DISPLAY, CompanyAlias.Reason.MERGE, CALLER);
    }

    private long countByName(String name) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM companies WHERE name = ?", Long.class, name);
    }

    private long aliasCount(String aliasKey) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM company_aliases WHERE alias_key = ?", Long.class,
                aliasKey);
    }

    private List<String> typeaheadNames(String query) throws Exception {
        String json = mockMvc.perform(get("/api/v1/companies/search").param("query", query))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$[*].name");
    }

    @Test
    @WithMockUser(username = CALLER, roles = {"ATTENDEE"})
    @DisplayName("typeahead: the alias key finds the company once, shown as the company")
    void should_findTargetInTypeahead_when_queryIsAliasKey() throws Exception {
        recordAlias();

        mockMvc.perform(get("/api/v1/companies/search").param("query", ALIAS_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value(TARGET))
                .andExpect(jsonPath("$[0].displayName").value(TARGET_DISPLAY));
    }

    @Test
    @WithMockUser(username = CALLER, roles = {"ATTENDEE"})
    @DisplayName("typeahead: the alias display name in other case and a word of it find the company")
    void should_findTargetInTypeahead_when_queryIsAliasDisplayNameInOtherCase() throws Exception {
        // positive control for absence: before the alias exists the query finds nothing
        assertThat(typeaheadNames("zebrafink ost ag")).isEmpty();

        recordAlias();

        // recording the alias cleared the cached empty answer
        assertThat(typeaheadNames("zebrafink ost ag")).containsExactly(TARGET);
        assertThat(typeaheadNames("ZEBRAFINK")).containsExactly(TARGET);
    }

    @Test
    @WithMockUser(username = CALLER, roles = {"ORGANIZER"})
    @DisplayName("paged list and internal search: the alias display name finds the company")
    void should_findTargetInListAndInternalSearch_when_searchIsAlias() throws Exception {
        recordAlias();

        mockMvc.perform(get("/api/v1/companies").param("search", "zebrafink ost"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].name").value(TARGET))
                .andExpect(jsonPath("$.pagination.totalItems").value(1));

        mockMvc.perform(get("/internal/companies").param("search", ALIAS_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0]").value(TARGET));
    }

    @Test
    @WithMockUser(username = CALLER, roles = {"ATTENDEE"})
    @DisplayName("get-or-create: the alias display name in other case returns the company, nothing created")
    void should_returnTarget_when_getOrCreateWithAliasDisplayName() throws Exception {
        recordAlias();
        long before = companyRepository.count();

        mockMvc.perform(post("/api/v1/companies/get-or-create").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\": \"ZEBRAFINK OST ag\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(TARGET))
                .andExpect(jsonPath("$.displayName").value(TARGET_DISPLAY));

        assertThat(companyRepository.count()).isEqualTo(before);
        assertThat(countByName("zebrafinkost")).isZero();
    }

    @Test
    @WithMockUser(username = CALLER, roles = {"ATTENDEE"})
    @DisplayName("get-or-create: the alias display name in another umlaut spelling returns the company")
    void should_returnTarget_when_getOrCreateWithAliasDisplayNameInOtherSpelling() throws Exception {
        aliasService.record(TARGET, "alizuerich", "Zürcher Möwe AG", CompanyAlias.Reason.RENAME, CALLER);

        // "Zurcher Mowe AG" slugs to "zurchermowea", not the alias key: only the folded display name matches
        mockMvc.perform(post("/api/v1/companies/get-or-create").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\": \"Zurcher Mowe AG\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(TARGET));

        assertThat(countByName("zurchermowea")).isZero();
    }

    @Test
    @WithMockUser(username = CALLER, roles = {"ATTENDEE"})
    @DisplayName("typeahead: the start of an alias display name ranks the company as a prefix match")
    void should_rankAliasPrefixAsPrefix_when_typeaheadLimited() throws Exception {
        recordAlias();
        for (int i = 1; i <= 3; i++) {
            companyRepository.save(Company.builder().name("alizz" + i).displayName("Aaa Zebrafink Halle " + i)
                    .isVerified(false).createdBy(CALLER).build());
        }

        // limit 1: the alias prefix match (rank 1) beats three companies that only contain the word
        mockMvc.perform(get("/api/v1/companies/search").param("query", "zebrafink").param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value(TARGET));
    }

    @Test
    @WithMockUser(username = CALLER, roles = {"ATTENDEE"})
    @DisplayName("get-or-create: a name whose slug is the alias key returns the company, nothing created")
    void should_returnTarget_when_getOrCreateSlugIsAliasKey() throws Exception {
        recordAlias();

        mockMvc.perform(post("/api/v1/companies/get-or-create").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\": \"Ali Old Key\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(TARGET));

        assertThat(countByName(ALIAS_KEY)).isZero();
    }

    @Test
    @WithMockUser(username = CALLER, roles = {"ORGANIZER"})
    @DisplayName("POST /companies: an alias display name is a duplicate (409); an alias key is never reused")
    void should_refuseAliasDisplayName_and_skipAliasKey_when_companyCreated() throws Exception {
        recordAlias();

        mockMvc.perform(post("/api/v1/companies").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"zebrafink ost ag\"}"))
                .andExpect(status().isConflict());
        assertThat(countByName("zebrafinkost")).isZero();

        mockMvc.perform(post("/api/v1/companies").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Ali Old Key\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value(ALIAS_KEY + "2"));
        assertThat(countByName(ALIAS_KEY)).isZero();
    }

    @Test
    @WithMockUser(username = CALLER, roles = {"ORGANIZER"})
    @DisplayName("deleting the company removes its aliases")
    void should_removeAliases_when_companyDeleted() throws Exception {
        recordAlias();
        assertThat(aliasCount(ALIAS_KEY)).isEqualTo(1);

        mockMvc.perform(delete("/api/v1/companies/" + TARGET))
                .andExpect(status().isNoContent());

        assertThat(aliasCount(ALIAS_KEY)).isZero();
    }

    @Test
    @DisplayName("an alias of a key a company holds is refused; recording a key again points it at the new company")
    void should_refuseLiveKey_and_repointAlias_when_recordedAgain() {
        assertThatThrownBy(() -> aliasService.record(TARGET, TARGET, "Self", CompanyAlias.Reason.RENAME, CALLER))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(aliasCount(TARGET)).isZero();

        recordAlias();
        companyRepository.save(Company.builder()
                .name("alisecond")
                .displayName("Alias Second Holding")
                .isVerified(false)
                .createdBy(CALLER)
                .build());

        aliasService.record("alisecond", ALIAS_KEY, ALIAS_DISPLAY, CompanyAlias.Reason.MERGE, CALLER);

        assertThat(aliasCount(ALIAS_KEY)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT c.name FROM company_aliases a JOIN companies c ON c.id = a.company_id"
                + " WHERE a.alias_key = ?", String.class, ALIAS_KEY)).isEqualTo("alisecond");
        assertThat(aliasService.resolve("zebrafink ost ag")).map(Company::getName).contains("alisecond");
    }
}
