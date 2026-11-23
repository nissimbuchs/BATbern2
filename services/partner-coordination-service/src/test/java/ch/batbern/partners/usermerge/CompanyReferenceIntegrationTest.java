package ch.batbern.partners.usermerge;

import ch.batbern.partners.config.TestAwsConfig;
import ch.batbern.partners.config.TestSecurityConfig;
import ch.batbern.partners.domain.Partner;
import ch.batbern.partners.domain.PartnershipLevel;
import ch.batbern.partners.repository.PartnerRepository;
import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.shared.utils.DateTimeUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * partner-coordination's part of the company-reference contract CUMS owns (#1251) against real
 * PostgreSQL: a company merge or rename moves topic suggestions, topic votes and the partner from the
 * source key to the target. A vote of the source on a topic the target voted for too is removed (the
 * target's stays); both companies partners refuses with 409. A rerun moves nothing. Every test
 * commits; {@code @BeforeEach} and {@code @AfterEach} remove the rows. The request and answer shapes
 * are checked against the CUMS contract in {@code S2sProviderContractIntegrationTest}.
 */
@Import({TestSecurityConfig.class, TestAwsConfig.class})
@DisplayName("Company-reference contract: partner-coordination moves its company keys")
class CompanyReferenceIntegrationTest extends AbstractIntegrationTest {

    private static final String SOURCE = "crsource";
    private static final String TARGET = "crtarget";
    private static final String OTHER = "crother";
    private static final String SUGGESTER = "cr.suggester";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PartnerRepository partnerRepository;
    @Autowired
    private CacheManager cacheManager;
    @Autowired
    private CompanyReferenceService service;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private UUID sourcePartner;
    private UUID sharedTopic;
    private UUID sourceTopic;

    @BeforeEach
    void seed() {
        cleanUp();
        sourcePartner = partner(SOURCE);
        sourceTopic = topic(SOURCE, "Topic of the source");
        sharedTopic = topic(TARGET, "Topic both voted for");
        UUID otherTopic = topic(OTHER, "Topic of someone else");
        vote(sourceTopic, SOURCE);
        vote(sharedTopic, SOURCE);
        vote(sharedTopic, TARGET);
        vote(otherTopic, OTHER);
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM topic_suggestions WHERE suggested_by = ?", SUGGESTER);
        for (String key : new String[] {SOURCE, TARGET, OTHER}) {
            partnerRepository.findByCompanyName(key).ifPresent(partnerRepository::delete);
        }
    }

    private UUID partner(String key) {
        return partnerRepository.save(Partner.builder().companyName(key).partnershipLevel(PartnershipLevel.GOLD)
                .partnershipStartDate(DateTimeUtils.todaySwiss().minusYears(1)).build()).getId();
    }

    private UUID topic(String company, String title) {
        return jdbc.queryForObject("INSERT INTO topic_suggestions (company_name, suggested_by, title, source) "
                + "VALUES (?, ?, ?, 'PARTNER') RETURNING id", UUID.class, company, SUGGESTER, title);
    }

    private void vote(UUID topic, String company) {
        jdbc.update("INSERT INTO topic_votes (topic_id, company_name) VALUES (?, ?)", topic, company);
    }

    private int count(String table, String company) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE company_name = ?", Integer.class,
                company);
        return n == null ? 0 : n;
    }

    private String merge(String source, String target) {
        return """
                {"source": "%s", "target": "%s", "performedBy": "organizer.alice", "trigger": "MERGE"}"""
                .formatted(source, target);
    }

    @Test
    @DisplayName("preview counts suggestions, votes and the partner, names the vote collision, changes nothing")
    void preview_countsAndChangesNothing() throws Exception {
        mockMvc.perform(get("/internal/company-references/preview").param("source", SOURCE).param("target", TARGET))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.moves['topic_suggestions.company_name']").value(1))
                .andExpect(jsonPath("$.moves['topic_votes.company_name']").value(2))
                .andExpect(jsonPath("$.moves['partners.company_name']").value(1))
                .andExpect(jsonPath("$.conflicts.topic_votes").value(1))
                .andExpect(jsonPath("$.blockers").isEmpty());

        assertThat(count("topic_votes", SOURCE)).isEqualTo(2);
        assertThat(count("partners", SOURCE)).isEqualTo(1);
    }

    @Test
    @DisplayName("merge moves suggestions, votes and the partner, keeps one vote on a collision; a rerun moves nothing")
    void merge_movesRows_keepsOneVote_andIsIdempotent() throws Exception {
        String body = mockMvc.perform(post("/internal/company-references/merge")
                        .contentType(MediaType.APPLICATION_JSON).content(merge(SOURCE, TARGET)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        JsonNode result = objectMapper.readTree(body);
        assertThat(result.path("moved").path("topic_suggestions.company_name").asInt()).isEqualTo(1);
        assertThat(result.path("moved").path("topic_votes.company_name").asInt()).isEqualTo(1);
        assertThat(result.path("moved").path("partners.company_name").asInt()).isEqualTo(1);
        assertThat(result.path("removed").path("topic_votes.company_name").asInt()).isEqualTo(1);
        assertThat(result.path("movedKeys").path("partners.company_name").get(0).asText())
                .isEqualTo(sourcePartner.toString());
        assertThat(result.path("movedKeys").path("topic_votes.company_name").get(0).asText())
                .isEqualTo(sourceTopic.toString());
        assertThat(result.path("removedKeys").path("topic_votes.company_name").get(0).asText())
                .isEqualTo(sharedTopic.toString());

        assertThat(count("topic_suggestions", SOURCE)).isZero();
        assertThat(count("topic_suggestions", TARGET)).isEqualTo(2);
        assertThat(count("topic_votes", SOURCE)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM topic_votes WHERE topic_id = ?", Integer.class,
                sharedTopic)).as("one vote left on the shared topic").isEqualTo(1);
        assertThat(count("topic_votes", TARGET)).isEqualTo(2);
        assertThat(count("topic_votes", OTHER)).isEqualTo(1);
        assertThat(partnerRepository.findById(sourcePartner)).get().extracting(Partner::getCompanyName)
                .isEqualTo(TARGET);

        mockMvc.perform(post("/internal/company-references/merge")
                        .contentType(MediaType.APPLICATION_JSON).content(merge(SOURCE, TARGET)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.moved['topic_suggestions.company_name']").value(0))
                .andExpect(jsonPath("$.moved['topic_votes.company_name']").value(0))
                .andExpect(jsonPath("$.moved['partners.company_name']").value(0))
                .andExpect(jsonPath("$.removed").isEmpty())
                .andExpect(jsonPath("$.movedKeys").isEmpty());
        assertThat(count("topic_votes", TARGET)).isEqualTo(2);
    }

    @Test
    @DisplayName("both companies partners: preview names the blocker, merge answers 409 and moves nothing")
    void bothPartners_refused() throws Exception {
        partner(TARGET);

        mockMvc.perform(get("/internal/company-references/preview").param("source", SOURCE).param("target", TARGET))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blockers[0]").value("MERGE_BLOCKED_BOTH_PARTNERS"));
        mockMvc.perform(post("/internal/company-references/merge")
                        .contentType(MediaType.APPLICATION_JSON).content(merge(SOURCE, TARGET)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("MERGE_BLOCKED_BOTH_PARTNERS"));

        assertThat(count("topic_suggestions", SOURCE)).isEqualTo(1);
        assertThat(count("topic_votes", SOURCE)).isEqualTo(2);
        assertThat(count("partners", SOURCE)).isEqualTo(1);
    }

    @Test
    @DisplayName("only the target a partner: suggestions and votes move, the target's partner stays")
    void targetPartnerOnly_keepsIt() throws Exception {
        partnerRepository.findByCompanyName(SOURCE).ifPresent(partnerRepository::delete);
        UUID targetPartner = partner(TARGET);

        mockMvc.perform(post("/internal/company-references/merge")
                        .contentType(MediaType.APPLICATION_JSON).content(merge(SOURCE, TARGET)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.moved['partners.company_name']").value(0));

        assertThat(count("topic_votes", SOURCE)).isZero();
        assertThat(partnerRepository.findByCompanyName(TARGET)).get().extracting(Partner::getId)
                .isEqualTo(targetPartner);
    }

    @Test
    @DisplayName("same key, a target that is no company key, or a missing field: 400, nothing moves")
    void invalidRequests_moveNothing() throws Exception {
        mockMvc.perform(post("/internal/company-references/merge")
                        .contentType(MediaType.APPLICATION_JSON).content(merge(SOURCE, SOURCE)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_MERGE"));
        mockMvc.perform(post("/internal/company-references/merge")
                        .contentType(MediaType.APPLICATION_JSON).content(merge(SOURCE, "no-such-key-too-long")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/internal/company-references/merge")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"source\": \"" + SOURCE + "\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/internal/company-references/preview").param("source", SOURCE).param("target", SOURCE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_MERGE"));

        assertThat(count("partners", SOURCE)).isEqualTo(1);
    }

    @Test
    @DisplayName("counts: partner, suggestions and votes per key for every key asked, keys without rows left out")
    void counts_perKey() throws Exception {
        mockMvc.perform(post("/internal/company-references/counts").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"keys\": [\"" + SOURCE + "\", \"" + TARGET + "\", \"crnothing\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.counts." + SOURCE + "['partners.company_name']").value(1))
                .andExpect(jsonPath("$.counts." + SOURCE + "['topic_suggestions.company_name']").value(1))
                .andExpect(jsonPath("$.counts." + SOURCE + "['topic_votes.company_name']").value(2))
                .andExpect(jsonPath("$.counts." + TARGET + "['partners.company_name']").doesNotExist())
                .andExpect(jsonPath("$.counts." + TARGET + "['topic_suggestions.company_name']").value(1))
                .andExpect(jsonPath("$.counts." + TARGET + "['topic_votes.company_name']").value(1))
                .andExpect(jsonPath("$.counts." + OTHER).doesNotExist())
                .andExpect(jsonPath("$.counts.crnothing").doesNotExist());

        assertThat(count("partners", SOURCE)).isEqualTo(1);
    }

    @Test
    @DisplayName("counts without keys, or with an empty key: 400")
    void counts_invalid() throws Exception {
        mockMvc.perform(post("/internal/company-references/counts").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"keys\": []}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/internal/company-references/counts").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"keys\": [\"\"]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("the caches are cleared after the commit, not inside the transaction")
    void merge_clearsCachesAfterCommit() {
        Cache companies = cacheManager.getCache("companyApiCache");
        companies.put(SOURCE, "Source AG");

        new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
            service.merge(SOURCE, TARGET, "organizer.alice", "MERGE");
            assertThat(companies.get(SOURCE)).as("still cached before the commit").isNotNull();
        });

        assertThat(companies.get(SOURCE)).isNull();
        assertThat(count("partners", TARGET)).isEqualTo(1);
    }
}
