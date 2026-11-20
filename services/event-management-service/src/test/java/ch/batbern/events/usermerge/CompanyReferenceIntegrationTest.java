package ch.batbern.events.usermerge;

import ch.batbern.events.config.CacheConfig;
import ch.batbern.events.config.TestAwsConfig;
import ch.batbern.events.config.TestSecurityConfig;
import ch.batbern.shared.test.AbstractIntegrationTest;
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
 * event-management's part of the company-reference contract CUMS owns (#1251) against real
 * PostgreSQL: a company merge or rename moves {@code registrations.attendee_company_id} from the
 * source key to the target; a rerun moves nothing. Every test commits; {@code @BeforeEach} and
 * {@code @AfterEach} remove the rows. The request and answer shapes are checked against the CUMS
 * contract in {@code S2sProviderContractIntegrationTest}.
 */
@Import({TestSecurityConfig.class, TestAwsConfig.class})
@DisplayName("Company-reference contract: event-management moves its company keys")
class CompanyReferenceIntegrationTest extends AbstractIntegrationTest {

    private static final String SOURCE = "crsource";
    private static final String TARGET = "crtarget";
    private static final String OTHER = "crother";
    private static final String CODE = "BATbern9841";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private CacheManager cacheManager;
    @Autowired
    private CompanyReferenceService service;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private UUID sourceA;
    private UUID sourceB;

    @BeforeEach
    void seed() {
        cleanUp();
        UUID event = UUID.randomUUID();
        jdbc.update("INSERT INTO events (id, event_code, event_number, title, description, event_date, "
                + "registration_deadline, venue_name, venue_address, venue_capacity, organizer_username, "
                + "event_type, workflow_state, created_at, updated_at) VALUES (?, ?, 9841, 'Company merge test', "
                + "'x', NOW() + interval '30 days', NOW() + interval '20 days', 'Venue', 'Address', 100, "
                + "'organizer1', 'evening', 'created', NOW(), NOW())", event, CODE);
        sourceA = registration(event, "cr.anna", SOURCE);
        sourceB = registration(event, "cr.beat", SOURCE);
        registration(event, "cr.carla", TARGET);
        registration(event, "cr.dario", OTHER);
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM events WHERE event_code = ?", CODE);
    }

    private UUID registration(UUID event, String username, String company) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO registrations (id, registration_code, event_id, attendee_username, attendee_email, "
                + "attendee_company_id, status, registration_date, created_at, updated_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, 'confirmed', NOW(), NOW(), NOW())",
                id, "REG-" + id, event, username, username + "@example.com", company);
        return id;
    }

    private int count(String company) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM registrations WHERE attendee_company_id = ?",
                Integer.class, company);
        return n == null ? 0 : n;
    }

    private String merge(String source, String target) {
        return """
                {"source": "%s", "target": "%s", "performedBy": "organizer.alice", "trigger": "MERGE"}"""
                .formatted(source, target);
    }

    @Test
    @DisplayName("preview counts the registrations under the source and changes nothing")
    void preview_countsAndChangesNothing() throws Exception {
        mockMvc.perform(get("/internal/company-references/preview").param("source", SOURCE).param("target", TARGET))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.moves['registrations.attendee_company_id']").value(2))
                .andExpect(jsonPath("$.conflicts").isEmpty())
                .andExpect(jsonPath("$.blockers").isEmpty());

        assertThat(count(SOURCE)).isEqualTo(2);
    }

    @Test
    @DisplayName("merge moves the source's registrations, names them, leaves others; a rerun moves nothing")
    void merge_movesRows_andIsIdempotent() throws Exception {
        String body = mockMvc.perform(post("/internal/company-references/merge")
                        .contentType(MediaType.APPLICATION_JSON).content(merge(SOURCE, TARGET)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        JsonNode result = objectMapper.readTree(body);
        assertThat(result.path("moved").path("registrations.attendee_company_id").asInt()).isEqualTo(2);
        assertThat(result.path("movedKeys").path("registrations.attendee_company_id").toString())
                .contains(sourceA.toString(), sourceB.toString());
        assertThat(result.path("removed").isEmpty()).isTrue();
        assertThat(count(SOURCE)).isZero();
        assertThat(count(TARGET)).isEqualTo(3);
        assertThat(count(OTHER)).isEqualTo(1);

        mockMvc.perform(post("/internal/company-references/merge")
                        .contentType(MediaType.APPLICATION_JSON).content(merge(SOURCE, TARGET)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.moved['registrations.attendee_company_id']").value(0))
                .andExpect(jsonPath("$.movedKeys").isEmpty());
        assertThat(count(TARGET)).isEqualTo(3);
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

        assertThat(count(SOURCE)).isEqualTo(2);
    }

    @Test
    @DisplayName("the caches are cleared after the commit, not inside the transaction")
    void merge_clearsCachesAfterCommit() {
        Cache users = cacheManager.getCache(CacheConfig.USER_API_CACHE);
        users.put("company:" + SOURCE, "Source AG");

        new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
            service.merge(SOURCE, TARGET, "organizer.alice", "MERGE");
            assertThat(users.get("company:" + SOURCE)).as("still cached before the commit").isNotNull();
        });

        assertThat(users.get("company:" + SOURCE)).isNull();
        assertThat(count(TARGET)).isEqualTo(3);
    }
}
