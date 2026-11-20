package ch.batbern.companyuser.integration;

import ch.batbern.companyuser.client.CompanyReferenceHolders;
import ch.batbern.companyuser.client.HttpCompanyReferenceContract;
import ch.batbern.companyuser.config.TestAwsConfig;
import ch.batbern.companyuser.domain.Company;
import ch.batbern.companyuser.domain.User;
import ch.batbern.companyuser.repository.CompanyRepository;
import ch.batbern.companyuser.repository.UserRepository;
import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.shared.test.contract.ContractServer;
import ch.batbern.shared.types.Role;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
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
import java.util.function.Function;
import java.util.stream.Collectors;

import static ch.batbern.companyuser.client.CompanyReferenceHolders.COUNTS;
import static ch.batbern.companyuser.client.CompanyReferenceHolders.HOLDER_A;
import static ch.batbern.companyuser.client.CompanyReferenceHolders.HOLDER_B;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Story 5 of the company-merge epic (#1251): organizers see which companies are probably one, with
 * both sides' counts and the suggested target, and dismiss a pair for good. Users are counted in CUMS;
 * registrations and the partner flag come from the holders of the company-references contract, one
 * counts call each for the whole list, answered at the wire by {@link CompanyReferenceHolders}. Every
 * test commits and removes its rows before and after; the list covers every company in the database,
 * so the assertions look at this test's pairs only.
 */
@Import(TestAwsConfig.class)
@DisplayName("#1251: organizers see candidate duplicate companies")
class CompanyDuplicateCandidatesIntegrationTest extends AbstractIntegrationTest {

    private static final String URL = "/api/v1/companies/duplicate-candidates";
    private static final String DISMISSALS = URL + "/dismissals";
    private static final String ORGANIZER = "dup.orga";
    private static final String UNIC = "zqunic";
    private static final String UNIC_AG = "zqunicag";
    private static final String WEB_ONE = "zqwebone";
    private static final String WEB_TWO = "zqwebtwo";
    private static final String PARTNER_A = "zqpartnera";
    private static final String PARTNER_B = "zqpartnerb";
    private static final String KANTON_BE = "zqkantonbe";
    private static final String KANTON_ZH = "zqkantonzh";
    private static final String LOGO_URL = "https://cdn.example.com/logos/companies/zqunicag/logo.png";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private CompanyRepository companyRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private HttpCompanyReferenceContract contract;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private ObjectMapper objectMapper;

    private ContractServer holders;

    @BeforeEach
    void seed() {
        removeRows();
        holders = CompanyReferenceHolders.bindTo(contract);
        company(UNIC, "Zqunic", null, null);
        company(UNIC_AG, "Zqunic AG", null, LOGO_URL);
        company(WEB_ONE, "Zq Web One", "https://www.zq-web.example.ch/de", null);
        company(WEB_TWO, "Zq Web Two", "http://zq-web.example.ch", null);
        company(PARTNER_A, "Zq Partner", null, null);
        company(PARTNER_B, "Zq Partner", null, null);
        company(KANTON_BE, "Zq Kanton Bern", null, null);
        company(KANTON_ZH, "Zq Kanton Zürich", null, null);
        user("dup.anna", UNIC);
        user("dup.beat", UNIC);
        user("dup.carla", UNIC_AG);
    }

    @AfterEach
    void tearDown() {
        holders.close();
        removeRows();
    }

    private void removeRows() {
        jdbc.update("DELETE FROM user_profiles WHERE username LIKE 'dup.%'");
        jdbc.update("DELETE FROM companies WHERE created_by = ?", ORGANIZER);
    }

    private void company(String key, String displayName, String website, String logoUrl) {
        companyRepository.saveAndFlush(Company.builder().name(key).displayName(displayName).website(website)
                .logoUrl(logoUrl).isVerified(false).createdBy(ORGANIZER).build());
    }

    private void user(String username, String company) {
        userRepository.saveAndFlush(User.builder().username(username).email(username + "@example.com")
                .firstName("Dup").lastName(username).companyId(company)
                .roles(new HashSet<>(Set.of(Role.ATTENDEE))).build());
    }

    /**
     * Holder A answers as event-management (registrations), holder B as partner-coordination
     * (the partner flag); keys of other tests' companies get nothing. Returns the keys each asked for.
     */
    private List<List<String>> expectCounts(ExpectedCount times) {
        List<List<String>> asked = new ArrayList<>();
        holders.expect(POST, COUNTS).atHost(HOLDER_A).times(times).respondPerRequest(HttpStatus.OK, call -> {
            asked.add(keys(call.body()));
            return answer(Map.of(UNIC_AG, Map.of("registrations.attendee_company_id", 4),
                    WEB_ONE, Map.of("registrations.attendee_company_id", 2)));
        });
        holders.expect(POST, COUNTS).atHost(HOLDER_B).times(times).respondPerRequest(HttpStatus.OK, call -> {
            asked.add(keys(call.body()));
            return answer(Map.of(UNIC_AG, Map.of("partners.company_name", 1, "topic_votes.company_name", 3),
                    PARTNER_A, Map.of("partners.company_name", 1),
                    PARTNER_B, Map.of("partners.company_name", 1)));
        });
        return asked;
    }

    private List<String> keys(String body) {
        try {
            List<String> keys = new ArrayList<>();
            objectMapper.readTree(body).path("keys").forEach(k -> keys.add(k.asText()));
            return keys;
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private String answer(Map<String, Map<String, Integer>> counts) {
        try {
            return objectMapper.writeValueAsString(Map.of("counts", counts));
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private Map<Set<String>, JsonNode> list() throws Exception {
        String body = mockMvc.perform(get(URL)).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString();
        List<JsonNode> pairs = new ArrayList<>();
        objectMapper.readTree(body).forEach(pairs::add);
        Map<Set<String>, JsonNode> byPair = pairs.stream().collect(Collectors.toMap(
                p -> Set.of(p.path("target").path("name").asText(), p.path("source").path("name").asText()),
                Function.identity()));
        assertThat(byPair).as("each pair once").hasSize(pairs.size());
        return byPair;
    }

    /** This test's pairs only: the list covers every company in the database. */
    private static Map<Set<String>, JsonNode> ours(Map<Set<String>, JsonNode> all) {
        return all.entrySet().stream().filter(e -> e.getKey().stream().allMatch(k -> k.startsWith("zq")))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private ResultActions dismiss(String a, String b) throws Exception {
        return mockMvc.perform(post(DISMISSALS).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nameA\": \"" + a + "\", \"nameB\": \"" + b + "\"}"));
    }

    // ---------------------------------------------------------------- the list

    @Test
    @WithMockUser(username = ORGANIZER, roles = {"ORGANIZER"})
    @DisplayName("exactly the expected pairs, each once, under the first rule; no pair for generic words")
    void should_listExactlyTheExpectedPairs() throws Exception {
        expectCounts(ExpectedCount.once());

        Map<Set<String>, JsonNode> pairs = ours(list());

        assertThat(pairs.keySet()).containsExactlyInAnyOrder(Set.of(UNIC, UNIC_AG), Set.of(WEB_ONE, WEB_TWO),
                Set.of(PARTNER_A, PARTNER_B));
        assertThat(pairs.get(Set.of(UNIC, UNIC_AG)).path("rule").asText()).isEqualTo("SAME_NAME_WITHOUT_LEGAL_FORM");
        assertThat(pairs.get(Set.of(UNIC, UNIC_AG)).path("confidence").asText()).isEqualTo("MEDIUM");
        assertThat(pairs.get(Set.of(WEB_ONE, WEB_TWO)).path("rule").asText()).isEqualTo("SAME_WEBSITE");
        assertThat(pairs.get(Set.of(WEB_ONE, WEB_TWO)).path("confidence").asText()).isEqualTo("HIGH");
        assertThat(pairs.get(Set.of(PARTNER_A, PARTNER_B)).path("rule").asText()).isEqualTo("SAME_DISPLAY_NAME");
        holders.verify();
    }

    @Test
    @WithMockUser(username = ORGANIZER, roles = {"ORGANIZER"})
    @DisplayName("sides: users from CUMS, registrations and partner from the holders; the partner is the target")
    void should_showBothSidesCounts_andSuggestThePartner() throws Exception {
        List<List<String>> asked = expectCounts(ExpectedCount.once());

        JsonNode unic = ours(list()).get(Set.of(UNIC, UNIC_AG));

        JsonNode target = unic.path("target");
        JsonNode source = unic.path("source");
        assertThat(target.path("name").asText()).as("partner before more users").isEqualTo(UNIC_AG);
        assertThat(target.path("displayName").asText()).isEqualTo("Zqunic AG");
        assertThat(target.path("logoUrl").asText()).isEqualTo(LOGO_URL);
        assertThat(target.path("userCount").asInt()).isEqualTo(1);
        assertThat(target.path("registrationCount").asInt()).isEqualTo(4);
        assertThat(target.path("isPartner").asBoolean()).isTrue();
        assertThat(target.path("createdBy").asText()).isEqualTo(ORGANIZER);
        assertThat(target.path("createdAt").asText()).isNotEmpty();
        assertThat(source.path("name").asText()).isEqualTo(UNIC);
        assertThat(source.path("userCount").asInt()).isEqualTo(2);
        assertThat(source.path("registrationCount").asInt()).isZero();
        assertThat(source.path("isPartner").asBoolean()).isFalse();
        assertThat(source.has("logoUrl")).isFalse();
        assertThat(unic.path("organizerCanMerge").asBoolean()).isTrue();

        assertThat(asked).as("one call per holder").hasSize(2);
        assertThat(asked).allSatisfy(keys -> assertThat(keys)
                .contains(UNIC, UNIC_AG, WEB_ONE, WEB_TWO, PARTNER_A, PARTNER_B)
                .doesNotContain(KANTON_BE, KANTON_ZH)
                .doesNotHaveDuplicates());
        holders.verify();
    }

    @Test
    @WithMockUser(username = ORGANIZER, roles = {"ORGANIZER"})
    @DisplayName("no partner, no logo, the same users: the older company is the target, though its key is larger")
    void should_suggestByUsersAgeAndKey_when_noPartnerNoLogo() throws Exception {
        expectCounts(ExpectedCount.once());
        // zqwebtwo has the larger key; made the older one, so only the age can make it the target
        jdbc.update("UPDATE companies SET created_at = created_at - interval '1 day' WHERE name = ?", WEB_TWO);

        Map<Set<String>, JsonNode> pairs = ours(list());

        JsonNode web = pairs.get(Set.of(WEB_ONE, WEB_TWO));
        assertThat(web.path("target").path("name").asText()).as("same users, created first").isEqualTo(WEB_TWO);
        assertThat(web.path("source").path("registrationCount").asInt()).isEqualTo(2);
    }

    @Test
    @WithMockUser(username = ORGANIZER, roles = {"ORGANIZER"})
    @DisplayName("both partners: organizerCanMerge false")
    void should_notOfferTheMerge_when_bothArePartners() throws Exception {
        expectCounts(ExpectedCount.once());

        JsonNode pair = ours(list()).get(Set.of(PARTNER_A, PARTNER_B));

        assertThat(pair.path("organizerCanMerge").asBoolean()).isFalse();
        assertThat(pair.path("target").path("isPartner").asBoolean()).isTrue();
        assertThat(pair.path("source").path("isPartner").asBoolean()).isTrue();
    }

    @Test
    @WithMockUser(username = ORGANIZER, roles = {"ORGANIZER"})
    @DisplayName("a holder that does not answer: 502 DUPLICATE_COUNTS_INCOMPLETE")
    void should_answer502_when_aHolderIsDown() throws Exception {
        holders.expect(POST, COUNTS).atHost(HOLDER_A).respondPerRequest(HttpStatus.OK, call -> answer(Map.of()));
        holders.expect(POST, COUNTS).atHost(HOLDER_B).respondWith(request -> {
            throw new ConnectException("connection refused");
        });

        mockMvc.perform(get(URL))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.errorCode").value("DUPLICATE_COUNTS_INCOMPLETE"));
    }

    // ---------------------------------------------------------------- dismissals

    @Test
    @WithMockUser(username = ORGANIZER, roles = {"ORGANIZER"})
    @DisplayName("dismiss in either order: 204, the pair is gone from the list, a repeat is 204")
    void should_hidePair_when_dismissed() throws Exception {
        expectCounts(ExpectedCount.once());

        dismiss(UNIC_AG, UNIC).andExpect(status().isNoContent());
        dismiss(UNIC, UNIC_AG).andExpect(status().isNoContent());

        Map<Set<String>, JsonNode> pairs = ours(list());
        assertThat(pairs).doesNotContainKey(Set.of(UNIC, UNIC_AG)).containsKey(Set.of(WEB_ONE, WEB_TWO));
        assertThat(jdbc.queryForObject("SELECT dismissed_by FROM company_duplicate_dismissals d JOIN companies a "
                + "ON a.id = d.company_a_id WHERE a.name IN (?, ?)", String.class, UNIC, UNIC_AG)).isEqualTo(ORGANIZER);
    }

    @Test
    @WithMockUser(username = ORGANIZER, roles = {"ORGANIZER"})
    @DisplayName("deleting either company removes its dismissals")
    void should_removeDismissal_when_aCompanyIsDeleted() throws Exception {
        dismiss(UNIC, UNIC_AG).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM company_duplicate_dismissals d JOIN companies c "
                + "ON c.id IN (d.company_a_id, d.company_b_id) WHERE c.name = ?", Long.class, UNIC_AG)).isOne();

        jdbc.update("DELETE FROM user_profiles WHERE company_id = ?", UNIC);
        jdbc.update("DELETE FROM companies WHERE name = ?", UNIC);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM company_duplicate_dismissals d JOIN companies c "
                + "ON c.id IN (d.company_a_id, d.company_b_id) WHERE c.name = ?", Long.class, UNIC_AG)).isZero();
    }

    @Test
    @WithMockUser(username = ORGANIZER, roles = {"ORGANIZER"})
    @DisplayName("unknown company 404, the same company twice 400, a missing name 400")
    void should_refuseInvalidDismissals() throws Exception {
        dismiss(UNIC, "zqnosuchco").andExpect(status().isNotFound());
        dismiss(UNIC, UNIC).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_DISMISSAL"));
        mockMvc.perform(post(DISMISSALS).contentType(MediaType.APPLICATION_JSON).content("{\"nameA\": \"x\"}"))
                .andExpect(status().isBadRequest());

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM company_duplicate_dismissals", Long.class)).isZero();
    }

    // ---------------------------------------------------------------- authorization

    @Test
    @DisplayName("anonymous: 401 for the list and a dismissal")
    void should_answer401_when_anonymous() throws Exception {
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
        dismiss(UNIC, UNIC_AG).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "dup.anna", roles = {"ATTENDEE"})
    @DisplayName("not an organizer: 403 for the list and a dismissal")
    void should_answer403_when_notAnOrganizer() throws Exception {
        mockMvc.perform(get(URL)).andExpect(status().isForbidden());
        dismiss(UNIC, UNIC_AG).andExpect(status().isForbidden());
    }
}
