package ch.batbern.companyuser.integration;

import ch.batbern.companyuser.client.CompanyReferenceHolders;
import ch.batbern.companyuser.client.HttpCompanyReferenceContract;
import ch.batbern.companyuser.config.TestAwsConfig;
import ch.batbern.companyuser.domain.Company;
import ch.batbern.companyuser.domain.CompanyAlias;
import ch.batbern.companyuser.domain.User;
import ch.batbern.companyuser.repository.CompanyRepository;
import ch.batbern.companyuser.repository.UserRepository;
import ch.batbern.companyuser.service.CompanyAliasService;
import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.shared.test.contract.ContractServer;
import ch.batbern.shared.types.Role;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.net.ConnectException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static ch.batbern.companyuser.client.CompanyReferenceHolders.HOLDER_A;
import static ch.batbern.companyuser.client.CompanyReferenceHolders.HOLDER_B;
import static ch.batbern.companyuser.client.CompanyReferenceHolders.MERGE;
import static ch.batbern.companyuser.client.CompanyReferenceHolders.PREVIEW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Story 4 of the company-merge epic (#1251): organizers fold a duplicate company into another.
 * EMS and PCS are reached through the company-references contract, answered at the wire by
 * {@link CompanyReferenceHolders}; CUMS moves its own rows (the path a rename uses), fills the
 * target's empty fields, keeps the source key and display name as aliases, deletes the source and
 * logs the merge. Every test commits and removes its rows before and after.
 */
@Import(TestAwsConfig.class)
@DisplayName("#1251: organizers merge two companies")
class CompanyMergeIntegrationTest extends AbstractIntegrationTest {

    private static final String ORGANIZER = "merge.orga";
    private static final String TARGET = "mrgtgt";
    private static final String SOURCE = "mrgsrc";
    private static final String SOURCE_DISPLAY = "Merge Source AG";
    private static final String MEMBER = "merge.member";
    private static final String UPLOAD = "mrg-up-1";
    private static final String LOGO_URL = "https://cdn.example.com/logos/companies/mrgsrc/logo-mrg.png";
    private static final String LOGO_KEY = "logos/companies/mrgsrc/logo-mrg.png";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CompanyAliasService aliasService;

    @Autowired
    private HttpCompanyReferenceContract contract;

    @Autowired
    private JdbcTemplate jdbc;

    private ContractServer holders;
    private UUID memberId;

    @BeforeEach
    void seed() {
        removeRows();
        holders = CompanyReferenceHolders.bindTo(contract);
        companyRepository.saveAndFlush(Company.builder().name(TARGET).displayName("Merge Target")
                .industry("Architecture").isVerified(true).createdBy(ORGANIZER).build());
        companyRepository.saveAndFlush(Company.builder().name(SOURCE).displayName(SOURCE_DISPLAY)
                .website("https://merge-source.example.com").industry("Software").description("The duplicate")
                .logoUrl(LOGO_URL).logoS3Key(LOGO_KEY).logoFileId(UPLOAD)
                .isVerified(false).createdBy(ORGANIZER).build());
        memberId = userRepository.saveAndFlush(User.builder().username(MEMBER).email("merge.member@example.com")
                .firstName("Mira").lastName("Member").companyId(SOURCE)
                .roles(new HashSet<>(Set.of(Role.ATTENDEE))).build()).getId();
        jdbc.update("INSERT INTO company_logo_requests (requester_username, company_name, new_logo_upload_id, "
                + "new_logo_url) VALUES (?, ?, 'mrg-up-2', 'https://cdn.example.com/m.png')", MEMBER, SOURCE);
        jdbc.update("INSERT INTO company_user_uploads (upload_id, purpose, s3_key, file_url, file_extension,"
                + " file_size, content_type, status, target_type, target_id) VALUES (?, 'company-logo',"
                + " ?, ?, 'png', 1000, 'image/png', 'associated', 'COMPANY', ?)", UPLOAD, LOGO_KEY, LOGO_URL, SOURCE);
        jdbc.update("INSERT INTO activity_history (user_id, activity_type, entity_type, entity_id, description) "
                + "VALUES (?, 'COMPANY_UPDATE', 'COMPANY', ?, 'x')", memberId, SOURCE);
    }

    @AfterEach
    void tearDown() {
        holders.close();
        removeRows();
    }

    private void removeRows() {
        jdbc.update("DELETE FROM company_merge_log WHERE source_key LIKE 'mrg%'");
        jdbc.update("DELETE FROM activity_history WHERE entity_type = 'COMPANY' AND entity_id LIKE 'mrg%'");
        jdbc.update("DELETE FROM company_logo_requests WHERE requester_username = ?", MEMBER);
        jdbc.update("DELETE FROM company_user_uploads WHERE upload_id = ?", UPLOAD);
        jdbc.update("DELETE FROM user_profiles WHERE username = ?", MEMBER);
        jdbc.update("DELETE FROM companies WHERE created_by = ?", ORGANIZER);
    }

    /** Both holders preview clean and merge; returns the merge bodies sent to holder A. */
    private List<String> expectMove() {
        List<String> bodies = new ArrayList<>();
        holders.expect(GET, PREVIEW).atHost(HOLDER_A).withQueryParam("source", SOURCE)
                .withQueryParam("target", TARGET).respondWithExample("clean");
        holders.expect(GET, PREVIEW).atHost(HOLDER_B).respondWithExample("nothingHeld");
        holders.expect(POST, MERGE).atHost(HOLDER_A)
                .andExpect(r -> bodies.add(((MockClientHttpRequest) r).getBodyAsString()))
                .respondWithExample("merged");
        holders.expect(POST, MERGE).atHost(HOLDER_B).respondWithExample("nothingHeld");
        return bodies;
    }

    private ResultActions preview(String target, String source) throws Exception {
        return mockMvc.perform(get("/api/v1/companies/" + target + "/merge-preview").param("source", source));
    }

    private ResultActions merge(String target, String source) throws Exception {
        return mockMvc.perform(post("/api/v1/companies/" + target + "/merge").contentType(MediaType.APPLICATION_JSON)
                .content("{\"sourceName\": \"" + source + "\"}"));
    }

    private String one(String sql, Object... args) {
        return jdbc.queryForObject(sql, String.class, args);
    }

    private void assertReferencesOn(String key) {
        assertThat(one("SELECT company_id FROM user_profiles WHERE username = ?", MEMBER)).isEqualTo(key);
        assertThat(one("SELECT company_name FROM company_logo_requests WHERE requester_username = ?", MEMBER))
                .isEqualTo(key);
        assertThat(one("SELECT target_id FROM company_user_uploads WHERE upload_id = ?", UPLOAD)).isEqualTo(key);
        assertThat(one("SELECT entity_id FROM activity_history WHERE entity_type = 'COMPANY' AND user_id = ?",
                memberId)).isEqualTo(key);
    }

    /** COMPLETED log rows of the source. */
    private long mergeLogRows() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM company_merge_log WHERE source_key = ?"
                + " AND outcome = 'COMPLETED'", Long.class, SOURCE);
    }

    private void assertNothingMerged() {
        assertThat(companyRepository.findByName(SOURCE)).isPresent();
        assertReferencesOn(SOURCE);
        assertThat(mergeLogRows()).isZero();
        assertThat(companyRepository.findByName(TARGET).orElseThrow().getWebsite()).isNull();
    }

    // ---------------------------------------------------------------- preview

    @Test
    @WithMockUser(username = ORGANIZER, roles = {"ORGANIZER"})
    @DisplayName("preview: both companies with user count and logo, CUMS and holder moves, fills; nothing changes")
    void should_showBothSidesAndMoves_when_previewed() throws Exception {
        holders.expect(GET, PREVIEW).atHost(HOLDER_A).withQueryParam("source", SOURCE)
                .withQueryParam("target", TARGET).respondWithExample("clean");
        holders.expect(GET, PREVIEW).atHost(HOLDER_B).respondWithExample("nothingHeld");

        preview(TARGET, SOURCE)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.target.name").value(TARGET))
                .andExpect(jsonPath("$.target.userCount").value(0))
                .andExpect(jsonPath("$.target.logo").doesNotExist())
                .andExpect(jsonPath("$.source.name").value(SOURCE))
                .andExpect(jsonPath("$.source.displayName").value(SOURCE_DISPLAY))
                .andExpect(jsonPath("$.source.userCount").value(1))
                .andExpect(jsonPath("$.source.logo.url").value(LOGO_URL))
                .andExpect(jsonPath("$.moves['user_profiles.company_id']").value(1))
                .andExpect(jsonPath("$.moves['company_logo_requests.company_name']").value(1))
                .andExpect(jsonPath("$.moves['company_user_uploads.target_id']").value(1))
                .andExpect(jsonPath("$.moves['activity_history.entity_id']").value(1))
                .andExpect(jsonPath("$.moves['topic_votes.company_name']").value(3))
                .andExpect(jsonPath("$.moves['partners.company_name']").value(1))
                .andExpect(jsonPath("$.moves['registrations.attendee_company_id']").value(0))
                .andExpect(jsonPath("$.conflicts.topic_votes").value(1))
                .andExpect(jsonPath("$.blockers.length()").value(0))
                .andExpect(jsonPath("$.fills.length()").value(3))
                .andExpect(jsonPath("$.fills[0]").value("website"))
                .andExpect(jsonPath("$.fills[1]").value("description"))
                .andExpect(jsonPath("$.fills[2]").value("logo"))
                .andExpect(jsonPath("$.canMerge").value(true))
                .andExpect(jsonPath("$.reasons.length()").value(0))
                .andExpect(jsonPath("$.canSwap").value(true));

        holders.verify();
        assertNothingMerged();
    }

    @Test
    @WithMockUser(username = ORGANIZER, roles = {"ORGANIZER"})
    @DisplayName("same company: preview canMerge false SAME_COMPANY without asking a holder; merge 400")
    void should_refuse_when_sameCompany() throws Exception {
        preview(SOURCE, SOURCE)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canMerge").value(false))
                .andExpect(jsonPath("$.reasons[0]").value("SAME_COMPANY"))
                .andExpect(jsonPath("$.canSwap").value(false));
        merge(SOURCE, SOURCE)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("MERGE_NOT_ALLOWED"));

        holders.verify();
        assertNothingMerged();
    }

    @Test
    @WithMockUser(username = ORGANIZER, roles = {"ORGANIZER"})
    @DisplayName("a target key a user profile cannot hold: TARGET_KEY_TOO_LONG, canSwap; merge 409, no holder called")
    void should_refuse_when_targetKeyTooLong() throws Exception {
        String longKey = "mrglongtargetkey";
        companyRepository.saveAndFlush(Company.builder().name(longKey).displayName("Merge Long Target")
                .isVerified(false).createdBy(ORGANIZER).build());

        preview(longKey, SOURCE)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canMerge").value(false))
                .andExpect(jsonPath("$.reasons[0]").value("TARGET_KEY_TOO_LONG"))
                .andExpect(jsonPath("$.canSwap").value(true))
                .andExpect(jsonPath("$.moves['user_profiles.company_id']").value(1));
        merge(longKey, SOURCE)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("MERGE_NOT_ALLOWED"))
                .andExpect(jsonPath("$.details.reasons[0]").value("TARGET_KEY_TOO_LONG"))
                .andExpect(jsonPath("$.details.canSwap").value(true));

        holders.verify();
        assertNothingMerged();

        // canSwap holds: the other direction merges; the non-key source is kept as no alias
        holders.reset();
        holders.expect(GET, PREVIEW).atHost(HOLDER_A).withQueryParam("source", longKey)
                .withQueryParam("target", SOURCE).respondWithExample("nothingHeld");
        holders.expect(GET, PREVIEW).atHost(HOLDER_B).respondWithExample("nothingHeld");
        holders.expect(POST, MERGE).atHost(HOLDER_A).respondWithExample("nothingHeld");
        holders.expect(POST, MERGE).atHost(HOLDER_B).respondWithExample("nothingHeld");
        merge(SOURCE, longKey)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targetName").value(SOURCE));
        holders.verify();
        assertThat(companyRepository.findByName(longKey)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM company_aliases a JOIN companies c"
                + " ON c.id = a.company_id WHERE c.name = ?", Long.class, SOURCE)).isZero();
    }

    @Test
    @WithMockUser(username = ORGANIZER, roles = {"ORGANIZER"})
    @DisplayName("both partners: preview BOTH_PARTNERS; merge 409 MERGE_BLOCKED_BOTH_PARTNERS, no holder merges")
    void should_refuse_when_bothPartners() throws Exception {
        holders.expect(GET, PREVIEW).atHost(HOLDER_A).times(ExpectedCount.twice()).respondWithExample("blocked");
        holders.expect(GET, PREVIEW).atHost(HOLDER_B).times(ExpectedCount.twice()).respondWithExample("nothingHeld");
        holders.expect(POST, MERGE).times(ExpectedCount.never()).respondWithExample("merged");

        preview(TARGET, SOURCE)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canMerge").value(false))
                .andExpect(jsonPath("$.reasons[0]").value("BOTH_PARTNERS"))
                .andExpect(jsonPath("$.blockers[0]").value("MERGE_BLOCKED_BOTH_PARTNERS"));
        merge(TARGET, SOURCE)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("MERGE_BLOCKED_BOTH_PARTNERS"));

        holders.verify();
        assertNothingMerged();
    }

    @Test
    @DisplayName("anonymous: 401 for preview and merge")
    void should_answer401_when_anonymous() throws Exception {
        preview(TARGET, SOURCE).andExpect(status().isUnauthorized());
        merge(TARGET, SOURCE).andExpect(status().isUnauthorized());
        assertNothingMerged();
    }

    @Test
    @WithMockUser(username = MEMBER, roles = {"ATTENDEE"})
    @DisplayName("not an organizer: 403 for preview and merge")
    void should_answer403_when_notOrganizer() throws Exception {
        preview(TARGET, SOURCE).andExpect(status().isForbidden());
        merge(TARGET, SOURCE).andExpect(status().isForbidden());
        assertNothingMerged();
    }

    @Test
    @WithMockUser(username = ORGANIZER, roles = {"ORGANIZER"})
    @DisplayName("an unknown company on either side: 404, no holder called")
    void should_answer404_when_companyUnknown() throws Exception {
        preview("mrgnone", SOURCE).andExpect(status().isNotFound());
        preview(TARGET, "mrgnone").andExpect(status().isNotFound());
        merge("mrgnone", SOURCE).andExpect(status().isNotFound());
        merge(TARGET, "mrgnone").andExpect(status().isNotFound());

        holders.verify();
        assertNothingMerged();
    }

    // ---------------------------------------------------------------- merge

    @Test
    @WithMockUser(username = ORGANIZER, roles = {"ORGANIZER"})
    @DisplayName("merge: every reference on the target, empty fields filled, source gone, aliases, one log row")
    void should_foldSourceIntoTarget_when_merged() throws Exception {
        List<String> sent = expectMove();

        merge(TARGET, SOURCE)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targetName").value(TARGET))
                .andExpect(jsonPath("$.sourceName").value(SOURCE))
                .andExpect(jsonPath("$.moved['user_profiles.company_id']").value(1))
                .andExpect(jsonPath("$.moved['company_user_uploads.target_id']").value(1))
                .andExpect(jsonPath("$.moved['topic_votes.company_name']").value(1))
                .andExpect(jsonPath("$.removed['topic_votes.company_name']").value(1))
                .andExpect(jsonPath("$.filled.length()").value(3));

        holders.verify();
        assertThat(sent).hasSize(1);
        assertThat((String) JsonPath.read(sent.get(0), "$.source")).isEqualTo(SOURCE);
        assertThat((String) JsonPath.read(sent.get(0), "$.target")).isEqualTo(TARGET);
        assertThat((String) JsonPath.read(sent.get(0), "$.trigger")).isEqualTo("MERGE");
        assertThat((String) JsonPath.read(sent.get(0), "$.performedBy")).isEqualTo(ORGANIZER);

        assertReferencesOn(TARGET);
        assertThat(companyRepository.findByName(SOURCE)).isEmpty();
        Company target = companyRepository.findByName(TARGET).orElseThrow();
        assertThat(target.getDisplayName()).isEqualTo("Merge Target");
        assertThat(target.getIndustry()).isEqualTo("Architecture");
        assertThat(target.getWebsite()).isEqualTo("https://merge-source.example.com");
        assertThat(target.getDescription()).isEqualTo("The duplicate");
        assertThat(target.getLogoUrl()).isEqualTo(LOGO_URL);
        assertThat(target.getLogoS3Key()).isEqualTo(LOGO_KEY);
        assertThat(target.getLogoFileId()).isEqualTo(UPLOAD);

        assertThat(one("SELECT c.name FROM company_aliases a JOIN companies c ON c.id = a.company_id"
                + " WHERE a.alias_key = ?", SOURCE)).isEqualTo(TARGET);
        assertThat(one("SELECT alias_display_name FROM company_aliases WHERE alias_key = ?", SOURCE))
                .isEqualTo(SOURCE_DISPLAY);
        assertThat(one("SELECT reason FROM company_aliases WHERE alias_key = ?", SOURCE))
                .isEqualTo(CompanyAlias.Reason.MERGE.name());

        assertThat(mergeLogRows()).isEqualTo(1);
        Map<String, Object> log = jdbc.queryForMap("SELECT target_key, performed_by, moved_keys::text AS moved,"
                + " removed_keys::text AS removed, filled_fields::text AS filled, source_snapshot::text AS snapshot"
                + " FROM company_merge_log WHERE source_key = ?", SOURCE);
        assertThat(log.get("target_key")).isEqualTo(TARGET);
        assertThat(log.get("performed_by")).isEqualTo(ORGANIZER);
        String moved = (String) log.get("moved");
        assertThat(JsonPath.<List<String>>read(moved, "$['user_profiles.company_id']"))
                .containsExactly(memberId.toString());
        assertThat(JsonPath.<List<String>>read(moved, "$['company_logo_requests.company_name']")).hasSize(1);
        assertThat(JsonPath.<List<String>>read(moved, "$['activity_history.entity_id']")).hasSize(1);
        assertThat(JsonPath.<List<String>>read(moved, "$['company_user_uploads.target_id']")).hasSize(1);
        assertThat(JsonPath.<List<String>>read(moved, "$['partners.company_name']"))
                .containsExactly("c4e1a2b3-5d6f-4a7b-9c8d-0e1f2a3b4c5d");
        assertThat(JsonPath.<List<String>>read((String) log.get("removed"), "$['topic_votes.company_name']"))
                .containsExactly("9a2b3c4d-5e6f-4a1b-8c2d-3e4f5a6b7c8d");
        assertThat(JsonPath.<List<String>>read((String) log.get("filled"), "$"))
                .containsExactly("website", "description", "logo");
        String snapshot = (String) log.get("snapshot");
        assertThat((String) JsonPath.read(snapshot, "$.name")).isEqualTo(SOURCE);
        assertThat((String) JsonPath.read(snapshot, "$.displayName")).isEqualTo(SOURCE_DISPLAY);
        assertThat((String) JsonPath.read(snapshot, "$.website")).isEqualTo("https://merge-source.example.com");
        assertThat((String) JsonPath.read(snapshot, "$.logoS3Key")).isEqualTo(LOGO_KEY);

        // a completed merge repeated: the source is gone
        merge(TARGET, SOURCE).andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = ORGANIZER, roles = {"ORGANIZER"})
    @DisplayName("after a merge the source key and display name find the target")
    void should_resolveSourceNames_when_merged() throws Exception {
        expectMove();
        // a search before the merge fills the cache the merge must clear
        mockMvc.perform(get("/api/v1/companies/search").param("query", SOURCE))
                .andExpect(jsonPath("$[0].name").value(SOURCE));

        merge(TARGET, SOURCE).andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/companies/search").param("query", SOURCE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value(TARGET));
        mockMvc.perform(post("/api/v1/companies/get-or-create").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\": \"" + SOURCE_DISPLAY + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(TARGET));
    }

    @Test
    @WithMockUser(username = ORGANIZER, roles = {"ORGANIZER"})
    @DisplayName("a target with its own logo and fields keeps them; the source's logo upload still moves")
    void should_keepTargetFields_when_alreadySet() throws Exception {
        Company target = companyRepository.findByName(TARGET).orElseThrow();
        target.setWebsite("https://target.example.com");
        target.setDescription("The original");
        target.setLogoUrl("https://cdn.example.com/logos/companies/mrgtgt/own.png");
        target.setLogoS3Key("logos/companies/mrgtgt/own.png");
        target.setLogoFileId("mrg-own");
        companyRepository.saveAndFlush(target);
        expectMove();

        merge(TARGET, SOURCE)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filled.length()").value(0));

        Company merged = companyRepository.findByName(TARGET).orElseThrow();
        assertThat(merged.getWebsite()).isEqualTo("https://target.example.com");
        assertThat(merged.getDescription()).isEqualTo("The original");
        assertThat(merged.getIndustry()).isEqualTo("Architecture");
        assertThat(merged.getLogoS3Key()).isEqualTo("logos/companies/mrgtgt/own.png");
        assertReferencesOn(TARGET);
    }

    @Test
    @WithMockUser(username = ORGANIZER, roles = {"ORGANIZER"})
    @DisplayName("an open logo request of a person with one at the target too: a conflict; the source's is closed and moves")
    void should_closeSourceLogoRequest_when_personHasOpenOneAtTarget() throws Exception {
        jdbc.update("INSERT INTO company_logo_requests (requester_username, company_name, new_logo_upload_id, "
                + "new_logo_url) VALUES (?, ?, 'mrg-up-3', 'https://cdn.example.com/t.png')", MEMBER, TARGET);
        holders.expect(GET, PREVIEW).atHost(HOLDER_A).respondWithExample("nothingHeld");
        holders.expect(GET, PREVIEW).atHost(HOLDER_B).respondWithExample("nothingHeld");
        preview(TARGET, SOURCE)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conflicts.company_logo_requests").value(1))
                .andExpect(jsonPath("$.canMerge").value(true));
        holders.verify();
        holders.reset();
        expectMove();

        merge(TARGET, SOURCE)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.removed['company_logo_requests.status']").doesNotExist());

        assertThat(jdbc.queryForList("SELECT status FROM company_logo_requests WHERE requester_username = ?"
                + " AND company_name = ? ORDER BY new_logo_upload_id", String.class, MEMBER, TARGET))
                .containsExactly("DECLINED", "OPEN");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM company_logo_requests WHERE company_name = ?",
                Long.class, SOURCE)).isZero();
        assertThat(JsonPath.<List<String>>read(one("SELECT removed_keys::text FROM company_merge_log"
                + " WHERE source_key = ?", SOURCE), "$['company_logo_requests.status']")).hasSize(1);
    }

    @Test
    @WithMockUser(username = ORGANIZER, roles = {"ORGANIZER"})
    @DisplayName("the source's own aliases lead to the target after the merge")
    void should_repointSourceAliases_when_merged() throws Exception {
        aliasService.record(SOURCE, "mrgold", "Merge Old Name", CompanyAlias.Reason.RENAME, ORGANIZER);
        expectMove();

        merge(TARGET, SOURCE).andExpect(status().isOk());

        assertThat(one("SELECT c.name FROM company_aliases a JOIN companies c ON c.id = a.company_id"
                + " WHERE a.alias_key = 'mrgold'")).isEqualTo(TARGET);
        String snapshot = one("SELECT source_snapshot::text FROM company_merge_log WHERE source_key = ?", SOURCE);
        assertThat((String) JsonPath.read(snapshot, "$.aliases[0].aliasKey")).isEqualTo("mrgold");
        assertThat((String) JsonPath.read(snapshot, "$.aliases[0].aliasDisplayName")).isEqualTo("Merge Old Name");
        assertThat((String) JsonPath.read(snapshot, "$.aliases[0].reason")).isEqualTo("RENAME");
        assertThat((String) JsonPath.read(snapshot, "$.aliases[0].createdBy")).isEqualTo(ORGANIZER);
        assertThat((String) JsonPath.read(snapshot, "$.aliases[0].createdAt")).isNotBlank();
        assertThat((String) JsonPath.read(snapshot, "$.updatedAt")).isNotBlank();
        mockMvc.perform(post("/api/v1/companies/get-or-create").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\": \"Merge Old Name\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(TARGET));
    }

    @Test
    @WithMockUser(username = ORGANIZER, roles = {"ORGANIZER"})
    @DisplayName("a holder refusing the merge itself (409): 409 MERGE_BLOCKED, nothing moved here")
    void should_refuse_when_holderRefusesMerge() throws Exception {
        holders.expect(GET, PREVIEW).atHost(HOLDER_A).respondWithExample("clean");
        holders.expect(GET, PREVIEW).atHost(HOLDER_B).respondWithExample("nothingHeld");
        holders.expect(POST, MERGE).atHost(HOLDER_A).respondWithExample(HttpStatus.CONFLICT);
        holders.expect(POST, MERGE).atHost(HOLDER_B).times(ExpectedCount.never()).respondWithExample("merged");

        merge(TARGET, SOURCE)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("MERGE_BLOCKED"));

        holders.verify();
        assertNothingMerged();
    }

    @Test
    @WithMockUser(username = ORGANIZER, roles = {"ORGANIZER"})
    @DisplayName("a holder that does not answer: 502 MERGE_INCOMPLETE, nothing merged here; the same merge finishes it")
    void should_leaveSource_then_finish_when_holderFailsThenMergeRepeated() throws Exception {
        holders.expect(GET, PREVIEW).atHost(HOLDER_A).respondWithExample("clean");
        holders.expect(GET, PREVIEW).atHost(HOLDER_B).respondWithExample("nothingHeld");
        holders.expect(POST, MERGE).atHost(HOLDER_A).respondWithExample("merged");
        holders.expect(POST, MERGE).atHost(HOLDER_B).respondWith(request -> {
            throw new ConnectException("connection refused");
        });

        merge(TARGET, SOURCE)
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.errorCode").value("MERGE_INCOMPLETE"));
        holders.verify();
        assertNothingMerged();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM company_aliases WHERE alias_key = ?", Long.class,
                SOURCE)).isZero();

        // the ids holder A moved are logged, though CUMS rolled back: the retry gets none from A
        String incomplete = one("SELECT moved_keys::text FROM company_merge_log WHERE source_key = ?"
                + " AND outcome = 'INCOMPLETE'", SOURCE);
        assertThat(JsonPath.<List<String>>read(incomplete, "$['partners.company_name']"))
                .containsExactly("c4e1a2b3-5d6f-4a7b-9c8d-0e1f2a3b4c5d");
        assertThat(JsonPath.<List<String>>read(one("SELECT removed_keys::text FROM company_merge_log"
                + " WHERE source_key = ? AND outcome = 'INCOMPLETE'", SOURCE), "$['topic_votes.company_name']"))
                .containsExactly("9a2b3c4d-5e6f-4a1b-8c2d-3e4f5a6b7c8d");

        holders.reset();
        holders.expect(GET, PREVIEW).atHost(HOLDER_A).respondWithExample("clean");
        holders.expect(GET, PREVIEW).atHost(HOLDER_B).respondWithExample("nothingHeld");
        holders.expect(POST, MERGE).atHost(HOLDER_A).respondWithExample("nothingHeld");
        holders.expect(POST, MERGE).atHost(HOLDER_B).respondWithExample("nothingHeld");
        merge(TARGET, SOURCE).andExpect(status().isOk());
        holders.verify();
        assertReferencesOn(TARGET);
        assertThat(companyRepository.findByName(SOURCE)).isEmpty();
        assertThat(mergeLogRows()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM company_merge_log WHERE source_key = ?",
                Long.class, SOURCE)).isEqualTo(2);
    }

    @Test
    @WithMockUser(username = ORGANIZER, roles = {"ORGANIZER"})
    @DisplayName("a holder answering 5xx to the merge: 502 MERGE_INCOMPLETE, nothing merged here")
    void should_leaveSource_when_holderAnswersServerError() throws Exception {
        holders.expect(GET, PREVIEW).atHost(HOLDER_A).respondWithExample("clean");
        holders.expect(GET, PREVIEW).atHost(HOLDER_B).respondWithExample("nothingHeld");
        holders.expect(POST, MERGE).atHost(HOLDER_A).respondWith(withServerError());

        merge(TARGET, SOURCE)
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.errorCode").value("MERGE_INCOMPLETE"));

        holders.verify();
        assertNothingMerged();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM company_merge_log WHERE source_key = ?",
                Long.class, SOURCE)).isZero();
    }
}
