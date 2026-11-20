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
import ch.batbern.shared.types.Role;
import ch.batbern.shared.test.contract.ContractServer;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Story 3 of the company-merge epic (#1251, side finding 1): renaming a company's key moves every
 * reference to it. EMS and PCS are reached through the company-references contract, answered at the
 * wire by {@link CompanyReferenceHolders}; CUMS moves its own rows, keeps the old key and display name
 * as an alias, and a failure leaves the company under its old key, renamable again to finish. Every
 * test commits and removes its rows before and after.
 */
@Import(TestAwsConfig.class)
@DisplayName("#1251: renaming a company moves its references")
class CompanyRenameIntegrationTest extends AbstractIntegrationTest {

    private static final String ORGANIZER = "rename.orga";
    private static final String OLD = "renold";
    private static final String NEW = "rennew";
    private static final String OLD_DISPLAY = "Rename Old AG";
    private static final String MEMBER = "rename.member";
    private static final String UPLOAD = "ren-up-1";

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
        companyRepository.saveAndFlush(Company.builder().name(OLD).displayName(OLD_DISPLAY)
                .isVerified(true).createdBy(ORGANIZER).build());
        memberId = userRepository.saveAndFlush(User.builder().username(MEMBER).email("rename.member@example.com")
                .firstName("Rena").lastName("Member").companyId(OLD)
                .roles(new HashSet<>(Set.of(Role.ATTENDEE))).build()).getId();
        jdbc.update("INSERT INTO company_logo_requests (requester_username, company_name, new_logo_upload_id, "
                + "new_logo_url) VALUES (?, ?, 'ren-up-2', 'https://cdn.example.com/r.png')", MEMBER, OLD);
        jdbc.update("INSERT INTO company_user_uploads (upload_id, purpose, s3_key, file_url, file_extension,"
                + " file_size, content_type, status, target_type, target_id) VALUES (?, 'company-logo',"
                + " 'logos/companies/renold/logo-ren.png', 'https://cdn.example.com/logos/companies/renold/logo-ren.png',"
                + " 'png', 1000, 'image/png', 'associated', 'COMPANY', ?)", UPLOAD, OLD);
        jdbc.update("INSERT INTO activity_history (user_id, activity_type, entity_type, entity_id, description) "
                + "VALUES (?, 'COMPANY_UPDATE', 'COMPANY', ?, 'x')", memberId, OLD);
    }

    @AfterEach
    void tearDown() {
        holders.close();
        removeRows();
    }

    private void removeRows() {
        jdbc.update("DELETE FROM activity_history WHERE entity_type = 'COMPANY' AND entity_id LIKE 'ren%'");
        jdbc.update("DELETE FROM company_logo_requests WHERE requester_username = ?", MEMBER);
        jdbc.update("DELETE FROM company_user_uploads WHERE upload_id = ?", UPLOAD);
        jdbc.update("DELETE FROM user_profiles WHERE username = ?", MEMBER);
        jdbc.update("DELETE FROM companies WHERE created_by = ?", ORGANIZER);
    }

    /** Both holders preview clean and merge; returns the merge bodies sent to holder A. */
    private List<String> expectMove() {
        List<String> bodies = new ArrayList<>();
        holders.expect(GET, PREVIEW).atHost(HOLDER_A).withQueryParam("source", OLD).withQueryParam("target", NEW)
                .respondWithExample("clean");
        holders.expect(GET, PREVIEW).atHost(HOLDER_B).respondWithExample("nothingHeld");
        holders.expect(POST, MERGE).atHost(HOLDER_A)
                .andExpect(r -> bodies.add(((MockClientHttpRequest) r).getBodyAsString()))
                .respondWithExample("merged");
        holders.expect(POST, MERGE).atHost(HOLDER_B).respondWithExample("nothingHeld");
        return bodies;
    }

    private ResultActions rename(String body) throws Exception {
        return mockMvc.perform(patch("/api/v1/companies/" + OLD).contentType(MediaType.APPLICATION_JSON)
                .content(body));
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

    @Test
    @WithMockUser(username = ORGANIZER, roles = {"ORGANIZER"})
    @DisplayName("a rename moves users, logo requests, the logo upload and activity, and calls the contract")
    void should_moveEveryReference_when_companyRenamed() throws Exception {
        List<String> sent = expectMove();

        rename("{\"name\": \"" + NEW + "\", \"displayName\": \"Rename New AG\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(NEW))
                .andExpect(jsonPath("$.displayName").value("Rename New AG"));

        holders.verify();
        assertThat(sent).hasSize(1);
        assertThat((String) JsonPath.read(sent.get(0), "$.source")).isEqualTo(OLD);
        assertThat((String) JsonPath.read(sent.get(0), "$.target")).isEqualTo(NEW);
        assertThat((String) JsonPath.read(sent.get(0), "$.trigger")).isEqualTo("RENAME");
        assertThat((String) JsonPath.read(sent.get(0), "$.performedBy")).isEqualTo(ORGANIZER);
        assertReferencesOn(NEW);
        assertThat(companyRepository.findByName(OLD)).isEmpty();
        assertThat(one("SELECT alias_display_name FROM company_aliases WHERE alias_key = ?", OLD))
                .isEqualTo(OLD_DISPLAY);
    }

    @Test
    @WithMockUser(username = ORGANIZER, roles = {"ORGANIZER"})
    @DisplayName("after a rename the old key and the old display name still find the company")
    void should_resolveOldKey_when_companyRenamed() throws Exception {
        expectMove();
        // a search before the rename fills the cache the rename must clear
        mockMvc.perform(get("/api/v1/companies/search").param("query", OLD))
                .andExpect(jsonPath("$[0].name").value(OLD));

        rename("{\"name\": \"" + NEW + "\", \"displayName\": \"Rename New AG\"}").andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/companies/search").param("query", OLD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value(NEW));
        mockMvc.perform(post("/api/v1/companies/get-or-create").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\": \"" + OLD_DISPLAY + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(NEW));
    }

    @Test
    @WithMockUser(username = ORGANIZER, roles = {"ORGANIZER"})
    @DisplayName("a holder that does not answer: 502, nothing renamed here; the same rename again finishes it")
    void should_leaveOldKey_then_finish_when_holderFailsThenRenameRepeated() throws Exception {
        holders.expect(GET, PREVIEW).atHost(HOLDER_A).respondWithExample("clean");
        holders.expect(GET, PREVIEW).atHost(HOLDER_B).respondWithExample("nothingHeld");
        holders.expect(POST, MERGE).atHost(HOLDER_A).respondWithExample("merged");
        holders.expect(POST, MERGE).atHost(HOLDER_B).respondWith(request -> {
            throw new ConnectException("connection refused");
        });

        rename("{\"name\": \"" + NEW + "\"}")
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.errorCode").value("RENAME_INCOMPLETE"));
        holders.verify();
        assertThat(companyRepository.findByName(OLD)).isPresent();
        assertReferencesOn(OLD);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM company_aliases WHERE alias_key = ?", Long.class, OLD))
                .isZero();

        holders.reset();
        expectMove();
        rename("{\"name\": \"" + NEW + "\"}").andExpect(status().isOk());
        holders.verify();
        assertReferencesOn(NEW);
    }

    @Test
    @WithMockUser(username = ORGANIZER, roles = {"ORGANIZER"})
    @DisplayName("a holder that refuses: 409, no holder merges, nothing renamed here")
    void should_refuse_when_holderBlocks() throws Exception {
        holders.expect(GET, PREVIEW).atHost(HOLDER_A).respondWithExample("blocked");
        holders.expect(GET, PREVIEW).atHost(HOLDER_B).respondWithExample("nothingHeld");
        holders.expect(POST, MERGE).times(ExpectedCount.never()).respondWithExample("merged");

        rename("{\"name\": \"" + NEW + "\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("RENAME_BLOCKED"));

        holders.verify();
        assertThat(companyRepository.findByName(OLD)).isPresent();
        assertReferencesOn(OLD);
    }

    @Test
    @WithMockUser(username = ORGANIZER, roles = {"ORGANIZER"})
    @DisplayName("a holder answering 5xx to the merge: 502, nothing renamed here")
    void should_leaveOldKey_when_holderAnswersServerError() throws Exception {
        holders.expect(GET, PREVIEW).atHost(HOLDER_A).respondWithExample("clean");
        holders.expect(GET, PREVIEW).atHost(HOLDER_B).respondWithExample("nothingHeld");
        holders.expect(POST, MERGE).atHost(HOLDER_A).respondWith(withServerError());

        rename("{\"name\": \"" + NEW + "\"}")
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.errorCode").value("RENAME_INCOMPLETE"));

        holders.verify();
        assertThat(companyRepository.findByName(OLD)).isPresent();
        assertReferencesOn(OLD);
    }

    @Test
    @WithMockUser(username = ORGANIZER, roles = {"ORGANIZER"})
    @DisplayName("a holder refusing the merge itself (409): 409, the next holder not called, nothing renamed here")
    void should_refuse_when_holderRefusesMerge() throws Exception {
        holders.expect(GET, PREVIEW).atHost(HOLDER_A).respondWithExample("clean");
        holders.expect(GET, PREVIEW).atHost(HOLDER_B).respondWithExample("nothingHeld");
        holders.expect(POST, MERGE).atHost(HOLDER_A).respondWithExample(HttpStatus.CONFLICT);
        holders.expect(POST, MERGE).atHost(HOLDER_B).times(ExpectedCount.never()).respondWithExample("merged");

        rename("{\"name\": \"" + NEW + "\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("RENAME_BLOCKED"));

        holders.verify();
        assertThat(companyRepository.findByName(OLD)).isPresent();
        assertReferencesOn(OLD);
    }

    @Test
    @WithMockUser(username = ORGANIZER, roles = {"ORGANIZER"})
    @DisplayName("a logo upload that cannot be attached fails the edit before any holder moves")
    void should_failBeforeTheMove_when_logoUploadUnknown() throws Exception {
        rename("{\"name\": \"" + NEW + "\", \"logoUploadId\": \"ren-no-such-upload\"}")
                .andExpect(status().isNotFound());

        holders.verify();
        assertThat(companyRepository.findByName(OLD)).isPresent();
        assertReferencesOn(OLD);
    }

    @Test
    @WithMockUser(username = ORGANIZER, roles = {"ORGANIZER"})
    @DisplayName("repeating its own display name, which another company keeps as an alias, is no change: 200")
    void should_acceptUnchangedDisplayName_when_itIsAnotherCompanysAlias() throws Exception {
        companyRepository.saveAndFlush(Company.builder().name("renother").displayName("Rename Other AG")
                .isVerified(false).createdBy(ORGANIZER).build());
        aliasService.record("renother", "renstolen", OLD_DISPLAY, CompanyAlias.Reason.MERGE, ORGANIZER);

        rename("{\"displayName\": \"" + OLD_DISPLAY + "\", \"industry\": \"Architecture\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.industry").value("Architecture"));
    }

    @Test
    @WithMockUser(username = ORGANIZER, roles = {"ORGANIZER"})
    @DisplayName("the alias key of another company: 409, no holder called")
    void should_refuse_when_newKeyIsAnotherCompanysAlias() throws Exception {
        companyRepository.saveAndFlush(Company.builder().name("renother").displayName("Rename Other AG")
                .isVerified(false).createdBy(ORGANIZER).build());
        aliasService.record("renother", "renstolen", "Stolen AG", CompanyAlias.Reason.MERGE, ORGANIZER);

        rename("{\"name\": \"renstolen\"}").andExpect(status().isConflict());

        holders.verify();
        assertThat(companyRepository.findByName(OLD)).isPresent();
        assertThat(one("SELECT c.name FROM company_aliases a JOIN companies c ON c.id = a.company_id"
                + " WHERE a.alias_key = 'renstolen'")).isEqualTo("renother");
    }

    @Test
    @WithMockUser(username = ORGANIZER, roles = {"ORGANIZER"})
    @DisplayName("a key another company holds: 409; not a key shape: 400; no holder called either way")
    void should_refuse_when_newKeyTakenOrInvalid() throws Exception {
        companyRepository.saveAndFlush(Company.builder().name("rentaken").displayName("Rename Taken AG")
                .isVerified(false).createdBy(ORGANIZER).build());

        rename("{\"name\": \"rentaken\"}").andExpect(status().isConflict());
        rename("{\"name\": \"ren new!\"}").andExpect(status().isBadRequest());

        holders.verify();
        assertReferencesOn(OLD);
    }

    @Test
    @WithMockUser(username = ORGANIZER, roles = {"ORGANIZER"})
    @DisplayName("the old display name of another company as the new display name: 409")
    void should_refuse_when_displayNameIsAnotherCompanysAlias() throws Exception {
        companyRepository.saveAndFlush(Company.builder().name("renother").displayName("Rename Other AG")
                .isVerified(false).createdBy(ORGANIZER).build());
        aliasService.record("renother", "renstolen", "Stolen AG", CompanyAlias.Reason.MERGE, ORGANIZER);

        rename("{\"displayName\": \"stolen ag\"}").andExpect(status().isConflict());

        assertThat(companyRepository.findByName(OLD)).map(Company::getDisplayName).contains(OLD_DISPLAY);
    }

    @Test
    @WithMockUser(username = ORGANIZER, roles = {"ORGANIZER"})
    @DisplayName("renaming back to its own alias: that alias goes, the key it leaves becomes one")
    void should_swapAlias_when_renamedBackToOwnAlias() throws Exception {
        aliasService.record(OLD, "renprev", "Rename Previous AG", CompanyAlias.Reason.RENAME, ORGANIZER);
        holders.expect(GET, PREVIEW).atHost(HOLDER_A).withQueryParam("target", "renprev").respondWithExample("clean");
        holders.expect(GET, PREVIEW).atHost(HOLDER_B).respondWithExample("nothingHeld");
        holders.expect(POST, MERGE).atHost(HOLDER_A).respondWithExample("merged");
        holders.expect(POST, MERGE).atHost(HOLDER_B).respondWithExample("nothingHeld");

        rename("{\"name\": \"renprev\"}").andExpect(status().isOk()).andExpect(jsonPath("$.name").value("renprev"));

        holders.verify();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM company_aliases WHERE alias_key = 'renprev'",
                Long.class)).isZero();
        assertThat(one("SELECT c.name FROM company_aliases a JOIN companies c ON c.id = a.company_id"
                + " WHERE a.alias_key = ?", OLD)).isEqualTo("renprev");
        assertReferencesOn("renprev");
    }

    @Test
    @WithMockUser(username = ORGANIZER, roles = {"ORGANIZER"})
    @DisplayName("a display name change only, or the same key: no holder called, no alias")
    void should_notMove_when_keyUnchanged() throws Exception {
        rename("{\"name\": \"" + OLD + "\", \"displayName\": \"Rename Old Holding\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Rename Old Holding"));

        holders.verify();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM company_aliases WHERE alias_key LIKE 'ren%'",
                Long.class)).isZero();
        assertReferencesOn(OLD);
    }
}
