package ch.batbern.events.controller;

import ch.batbern.events.config.TestAwsConfig;
import ch.batbern.events.config.TestSecurityConfig;
import ch.batbern.events.repository.AppSettingRepository;
import ch.batbern.events.service.PresentationSettings;
import ch.batbern.shared.test.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Moderator presentation settings in EMS (Story 4.9, moved from CUMS): stored in app_settings
 * under the key {@code presentation} as JSON, written through {@code PUT /admin/settings/presentation}
 * (validated), read anonymously through {@code GET /public/settings/presentation}.
 */
@Import({TestSecurityConfig.class, TestAwsConfig.class})
class PresentationSettingsIntegrationTest extends AbstractIntegrationTest {

    private static final String PUBLIC_URL = "/api/v1/public/settings/presentation";
    private static final String ADMIN_URL = "/api/v1/admin/settings/presentation";
    /** The default CUMS returned when its row was absent, kept verbatim by the move. */
    private static final String DEFAULT_ABOUT_TEXT =
            "BATbern ist eine unabhängige Plattform, die Berner Architekten und Ingenieure vernetzt. "
            + "Wir veranstalten regelmässige Treffen, bei denen Fachleute Erfahrungen austauschen"
            + " und neue Impulse gewinnen.";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AppSettingRepository appSettingRepository;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    @AfterEach
    void removeStoredValue() {
        appSettingRepository.findBySettingKey("presentation").ifPresent(appSettingRepository::delete);
    }

    @Test
    void should_returnDefaults_when_nothingStoredAndCallerIsAnonymous() throws Exception {
        mockMvc.perform(get(PUBLIC_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aboutText").value(DEFAULT_ABOUT_TEXT))
                .andExpect(jsonPath("$.partnerCount").value(9));
    }

    @Test
    @WithMockUser(username = "carla.muster", roles = "ORGANIZER")
    void should_storeAndServe_when_organizerPutsValidJson() throws Exception {
        String value = objectMapper.writeValueAsString(Map.of(
                "aboutText", "Plattform für Brünnimann & O’Neill", "partnerCount", 12));

        mockMvc.perform(put(ADMIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("value", value))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.key").value("presentation"));

        mockMvc.perform(get(PUBLIC_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aboutText").value("Plattform für Brünnimann & O’Neill"))
                .andExpect(jsonPath("$.partnerCount").value(12));
        assertThat(appSettingRepository.findBySettingKey("presentation").orElseThrow().getUpdatedBy())
                .isEqualTo("carla.muster");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "not json",
        "[]",
        "{\"partnerCount\": 3}",
        "{\"aboutText\": \"  \", \"partnerCount\": 3}",
        "{\"aboutText\": \"x\", \"partnerCount\": -1}",
        "{\"aboutText\": \"x\", \"partnerCount\": \"3\"}",
        "{\"aboutText\": \"x\", \"partnerCount\": 2.5}",
        "{\"aboutText\": \"x\", \"partnerCount\": 3, \"extra\": true}"
    })
    @WithMockUser(username = "carla.muster", roles = "ORGANIZER")
    void should_reject400AndKeepTheStoredValue_when_valueIsNotValidPresentationJson(String value)
            throws Exception {
        String valid = "{\"aboutText\":\"kept\",\"partnerCount\":4}";
        mockMvc.perform(put(ADMIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("value", valid))))
                .andExpect(status().isOk());

        mockMvc.perform(put(ADMIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("value", value))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get(PUBLIC_URL))
                .andExpect(jsonPath("$.aboutText").value("kept"))
                .andExpect(jsonPath("$.partnerCount").value(4));
    }

    @Test
    @WithMockUser(roles = "SPEAKER")
    void should_return403_when_nonOrganizerWrites() throws Exception {
        mockMvc.perform(put(ADMIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":\"{\\\"aboutText\\\":\\\"x\\\",\\\"partnerCount\\\":1}\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void should_return401_when_anonymousWrites() throws Exception {
        mockMvc.perform(put(ADMIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":\"{\\\"aboutText\\\":\\\"x\\\",\\\"partnerCount\\\":1}\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void should_notServeTheFormerCumsWritePath() throws Exception {
        mockMvc.perform(put("/api/v1/settings/presentation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"aboutText\":\"x\",\"partnerCount\":1}"))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isIn(401, 404, 405));
    }

    /**
     * The one-off move (V152): when the former CUMS table exists, its row is copied into the key
     * {@code presentation}; the table stays (an old CUMS image still maps it, rollback safety) and
     * a follow-up migration drops it one release later. A key already stored is kept. Runs the
     * migration's own SQL in a transaction that is rolled back, DDL included.
     */
    @Test
    void should_copyTheCumsRowAndKeepTheTable_when_migrationRunsWithTheFormerTablePresent() throws Exception {
        String sql = migrationSql();
        transactionTemplate.executeWithoutResult(tx -> {
            jdbcTemplate.execute("CREATE TABLE presentation_settings (id SERIAL PRIMARY KEY, "
                    + "about_text TEXT NOT NULL, partner_count INT NOT NULL DEFAULT 0)");
            jdbcTemplate.update(
                    "INSERT INTO presentation_settings (id, about_text, partner_count) VALUES (1, ?, ?)",
                    "Seit 2005 \"unabhängig\"\nzweite Zeile", 11);

            jdbcTemplate.execute(sql);

            assertThat(jdbcTemplate.queryForObject(
                    "SELECT to_regclass('presentation_settings') IS NOT NULL", Boolean.class)).isTrue();
            String stored = jdbcTemplate.queryForObject(
                    "SELECT setting_value FROM app_settings WHERE setting_key = 'presentation'", String.class);
            assertThat(PresentationSettings.parse(objectMapper, stored))
                    .isEqualTo(new PresentationSettings("Seit 2005 \"unabhängig\"\nzweite Zeile", 11));

            // a second run keeps the stored key (ON CONFLICT DO NOTHING)
            jdbcTemplate.update("UPDATE presentation_settings SET partner_count = 99 WHERE id = 1");
            jdbcTemplate.execute(sql);
            assertThat(PresentationSettings.parse(objectMapper, jdbcTemplate.queryForObject(
                    "SELECT setting_value FROM app_settings WHERE setting_key = 'presentation'", String.class))
                    .partnerCount()).isEqualTo(11);
            tx.setRollbackOnly();
        });
        assertThat(appSettingRepository.findBySettingKey("presentation")).isEmpty();
    }

    @Test
    void should_doNothing_when_migrationRunsWithoutTheFormerTable() throws Exception {
        String sql = migrationSql();
        transactionTemplate.executeWithoutResult(tx -> {
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT to_regclass('presentation_settings') IS NULL", Boolean.class)).isTrue();

            jdbcTemplate.execute(sql);

            assertThat(jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM app_settings WHERE setting_key = 'presentation'", Integer.class))
                    .isZero();
            tx.setRollbackOnly();
        });
    }

    private static String migrationSql() throws Exception {
        return new ClassPathResource("db/migration/V152__move_presentation_settings_to_app_settings.sql")
                .getContentAsString(StandardCharsets.UTF_8);
    }
}
