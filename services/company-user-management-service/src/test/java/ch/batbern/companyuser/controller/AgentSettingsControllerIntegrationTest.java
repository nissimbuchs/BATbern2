package ch.batbern.companyuser.controller;

import ch.batbern.companyuser.config.TestAwsConfig;
import ch.batbern.companyuser.domain.User;
import ch.batbern.companyuser.repository.UserRepository;
import ch.batbern.companyuser.service.AgentSettingsService;
import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.shared.test.security.TestTokens;
import ch.batbern.shared.types.Role;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Agentic interaction, epic agent identity, story 4: the owner switches the agent and keeps the
 * allowlist and the organizer delegate in CUMS ({@code GET} / {@code PATCH /api/v1/agent-settings}).
 *
 * <p>Production chain and real tokens: a Cognito-shaped JWT through the real
 * {@link JwtAuthenticationConverter}, and real agent tokens ({@link TestTokens}) whose roles come from
 * {@code role_assignments} by their {@code sub}. Every test commits (no class-level
 * {@code @Transactional}); {@code @AfterEach} restores the migrated singleton (off, no delegate, empty
 * allowlist, never changed) and removes the users.
 */
@Import(TestAwsConfig.class)
@DisplayName("CUMS: agent settings (switch, allowlist, delegate)")
class AgentSettingsControllerIntegrationTest extends AbstractIntegrationTest {

    private static final String URL = "/api/v1/agent-settings";

    private static final String ORG_A = "hongan.agentset";
    private static final String ORG_A_SUB = "a0000000-0000-4000-8000-0000000004a1";
    private static final String ORG_B = "matthias.agentset";
    private static final String ORG_B_SUB = "a0000000-0000-4000-8000-0000000004a2";
    private static final String SPEAKER = "jeanluc.agentset";
    private static final String SPEAKER_SUB = "a0000000-0000-4000-8000-0000000004a3";
    private static final String ATTENDEE = "fang.agentset";
    private static final String ATTENDEE_SUB = "a0000000-0000-4000-8000-0000000004a4";
    private static final String MISSING = "nobody.agentset";
    private static final Set<String> USERNAMES = Set.of(ORG_A, ORG_B, SPEAKER, ATTENDEE);

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private JwtAuthenticationConverter jwtAuthenticationConverter;
    @Autowired
    private AgentSettingsService agentSettingsService;

    @BeforeEach
    void seed() {
        cleanUp();
        userRepository.saveAndFlush(user(ORG_A, ORG_A_SUB, "Hông Ân", "Meier", Role.ORGANIZER));
        userRepository.saveAndFlush(user(ORG_B, ORG_B_SUB, "Matthias", "Brünnimann", Role.ORGANIZER));
        userRepository.saveAndFlush(user(SPEAKER, SPEAKER_SUB, "Jean-Luc", "O’Neill", Role.SPEAKER));
        userRepository.saveAndFlush(user(ATTENDEE, ATTENDEE_SUB, "芳", "王", Role.ATTENDEE));
    }

    /** Restores the singleton as V41 leaves it, then removes the users. */
    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM agent_allowlist");
        jdbc.update("INSERT INTO agent_settings (id, enabled) VALUES (1, false) "
                + "ON CONFLICT (id) DO UPDATE SET enabled = false, delegate_username = NULL, "
                + "updated_at = NULL, updated_by = NULL");
        USERNAMES.forEach(name -> userRepository.findByUsername(name).ifPresent(userRepository::delete));
        userRepository.flush();
    }

    private static User user(String username, String sub, String first, String last, Role role) {
        return User.builder()
                .username(username)
                .email(username + "@example.com")
                .firstName(first)
                .lastName(last)
                .cognitoUserId(sub)
                .roles(new HashSet<>(Set.of(role)))
                .build();
    }

    /** A Cognito-shaped token through the service's real role mapping. */
    private RequestPostProcessor cognito(String sub, String username, String role) {
        Converter<Jwt, Collection<GrantedAuthority>> realConversion =
                token -> jwtAuthenticationConverter.convert(token).getAuthorities();
        return jwt().jwt(t -> t.subject(sub)
                        .claim("custom:username", username)
                        .claim("custom:role", role))
                .authorities(realConversion);
    }

    private RequestPostProcessor organizerA() {
        return cognito(ORG_A_SUB, ORG_A, "ORGANIZER");
    }

    private static String agentBearer(String sub, String username) {
        return "Bearer " + TestTokens.agentToken(sub, username, "run-agent-settings-1", "organizer-assistant");
    }

    private static MockHttpServletRequestBuilder patchJson(String body) {
        return patch(URL).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private Boolean storedEnabled() {
        return jdbc.queryForObject("SELECT enabled FROM agent_settings WHERE id = 1", Boolean.class);
    }

    @Test
    @DisplayName("fresh database: the switch is off, the allowlist empty, no delegate")
    void should_readOff_when_freshDatabase() throws Exception {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM agent_settings", Integer.class)).isEqualTo(1);

        mockMvc.perform(get(URL).with(organizerA()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.allowlist", empty()))
                .andExpect(jsonPath("$.delegateUsername").value(nullValue()))
                .andExpect(jsonPath("$.updatedAt").value(nullValue()))
                .andExpect(jsonPath("$.updatedBy").value(nullValue()));
    }

    @Test
    @DisplayName("a missing row reads as off and the first PATCH creates it")
    void should_readOff_when_rowMissing() throws Exception {
        jdbc.update("DELETE FROM agent_settings");

        mockMvc.perform(get(URL).with(organizerA()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.allowlist", empty()))
                .andExpect(jsonPath("$.delegateUsername").value(nullValue()));
        assertThat(agentSettingsService.isEnabled()).isFalse();
        assertThat(agentSettingsService.delegateUsername()).isEmpty();

        mockMvc.perform(patchJson("{\"enabled\": true}").with(organizerA()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true));
        assertThat(storedEnabled()).isTrue();
    }

    @Test
    @DisplayName("an organizer switches on: allowlist and delegate unchanged, updated_by is the username")
    void should_switchOn_when_organizerPatchesEnabled() throws Exception {
        mockMvc.perform(patchJson("{\"allowlist\": [\"" + ORG_B + "\"], \"delegateUsername\": \"" + ORG_B + "\"}")
                        .with(organizerA()))
                .andExpect(status().isOk());

        mockMvc.perform(patchJson("{\"enabled\": true}").with(organizerA()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.allowlist", contains(ORG_B)))
                .andExpect(jsonPath("$.delegateUsername").value(ORG_B))
                .andExpect(jsonPath("$.updatedAt").value(notNullValue()))
                .andExpect(jsonPath("$.updatedBy").value(ORG_A));

        assertThat(jdbc.queryForObject("SELECT updated_by FROM agent_settings WHERE id = 1", String.class))
                .isEqualTo(ORG_A);
        mockMvc.perform(get(URL).with(organizerA()))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.allowlist", contains(ORG_B)))
                .andExpect(jsonPath("$.delegateUsername").value(ORG_B));
        assertThat(agentSettingsService.isEnabled()).isTrue();

        mockMvc.perform(patchJson("{\"enabled\": false}").with(organizerA()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.allowlist", contains(ORG_B)));
        assertThat(storedEnabled()).isFalse();
    }

    @Test
    @DisplayName("allowlist: duplicates collapse, sorted, a second PATCH replaces the whole list")
    void should_replaceAllowlist_when_organizerPatchesAllowlist() throws Exception {
        mockMvc.perform(patchJson("{\"allowlist\": [\"" + ORG_B + "\", \"" + ORG_A + "\", \"" + ORG_B + "\"]}")
                        .with(organizerA()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowlist", contains(ORG_A, ORG_B)))
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.delegateUsername").value(nullValue()));
        assertThat(jdbc.queryForList("SELECT added_by FROM agent_allowlist", String.class))
                .containsOnly(ORG_A);
        assertThat(agentSettingsService.isAllowlisted(ORG_A)).isTrue();
        assertThat(agentSettingsService.isAllowlisted(SPEAKER)).isFalse();

        mockMvc.perform(patchJson("{\"allowlist\": [\"" + ORG_B + "\"]}").with(organizerA()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowlist", contains(ORG_B)));

        mockMvc.perform(get(URL).with(organizerA()))
                .andExpect(jsonPath("$.allowlist", contains(ORG_B)));
        assertThat(agentSettingsService.isAllowlisted(ORG_A)).isFalse();

        mockMvc.perform(patchJson("{\"allowlist\": []}").with(organizerA()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowlist", empty()));
    }

    @Test
    @DisplayName("delegate: set, then an empty string clears it; the allowlist stays")
    void should_setAndClearDelegate_when_organizerPatchesDelegate() throws Exception {
        mockMvc.perform(patchJson("{\"allowlist\": [\"" + ORG_A + "\"]}").with(organizerA()))
                .andExpect(status().isOk());

        mockMvc.perform(patchJson("{\"delegateUsername\": \"" + ORG_A + "\"}").with(organizerA()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.delegateUsername").value(ORG_A))
                .andExpect(jsonPath("$.allowlist", contains(ORG_A)))
                .andExpect(jsonPath("$.enabled").value(false));
        assertThat(agentSettingsService.delegateUsername()).contains(ORG_A);

        mockMvc.perform(patchJson("{\"delegateUsername\": \"\"}").with(organizerA()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.delegateUsername").value(nullValue()))
                .andExpect(jsonPath("$.allowlist", contains(ORG_A)))
                .andExpect(jsonPath("$.enabled").value(false));
        mockMvc.perform(get(URL).with(organizerA()))
                .andExpect(jsonPath("$.delegateUsername").value(nullValue()));
        assertThat(agentSettingsService.delegateUsername()).isEmpty();
    }

    @Test
    @DisplayName("a non-organizer or unknown username: 400 naming them, nothing stored")
    void should_refuseWith400_when_usernameIsNoOrganizer() throws Exception {
        // positive control: a valid change is stored and stays after the refused ones
        mockMvc.perform(patchJson("{\"allowlist\": [\"" + ORG_A + "\"]}").with(organizerA()))
                .andExpect(status().isOk());

        mockMvc.perform(patchJson("{\"enabled\": true, \"allowlist\": [\"" + ORG_B + "\", \"" + ATTENDEE
                        + "\", \"" + MISSING + "\"]}").with(organizerA()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", allOf(containsString(ATTENDEE), containsString(MISSING),
                        not(containsString(ORG_B)))))
                .andExpect(jsonPath("$.details.usernames", contains(ATTENDEE, MISSING)));

        mockMvc.perform(patchJson("{\"delegateUsername\": \"" + SPEAKER + "\"}").with(organizerA()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString(SPEAKER)));

        mockMvc.perform(get(URL).with(organizerA()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.allowlist", contains(ORG_A)))
                .andExpect(jsonPath("$.delegateUsername").value(nullValue()));
    }

    @Test
    @DisplayName("speaker and attendee: 403 on GET and PATCH, the switch stays off")
    void should_refuseWith403_when_callerIsNoOrganizer() throws Exception {
        mockMvc.perform(get(URL).with(cognito(SPEAKER_SUB, SPEAKER, "SPEAKER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(patchJson("{\"enabled\": true}").with(cognito(SPEAKER_SUB, SPEAKER, "SPEAKER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(URL).with(cognito(ATTENDEE_SUB, ATTENDEE, "ATTENDEE")))
                .andExpect(status().isForbidden());
        mockMvc.perform(patchJson("{\"enabled\": true}").with(cognito(ATTENDEE_SUB, ATTENDEE, "ATTENDEE")))
                .andExpect(status().isForbidden());

        assertThat(storedEnabled()).isFalse();
    }

    @Test
    @DisplayName("an agent token of an organizer: 403 on GET and PATCH; the same body by Cognito switches on")
    void should_refuseWith403_when_agentTokenOfOrganizer() throws Exception {
        mockMvc.perform(get(URL).header(HttpHeaders.AUTHORIZATION, agentBearer(ORG_A_SUB, ORG_A)))
                .andExpect(status().isForbidden());
        mockMvc.perform(patchJson("{\"enabled\": true}")
                        .header(HttpHeaders.AUTHORIZATION, agentBearer(ORG_A_SUB, ORG_A)))
                .andExpect(status().isForbidden());
        assertThat(storedEnabled()).isFalse();

        // positive control: the same organizer, the same body, with a Cognito token
        mockMvc.perform(patchJson("{\"enabled\": true}").with(organizerA()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true));
        assertThat(storedEnabled()).isTrue();
    }

    @Test
    @DisplayName("anonymous: 401 on GET and PATCH")
    void should_refuseWith401_when_anonymous() throws Exception {
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
        mockMvc.perform(patchJson("{\"enabled\": true}")).andExpect(status().isUnauthorized());
        assertThat(storedEnabled()).isFalse();
    }

    @Test
    @DisplayName("a person who loses ORGANIZER is no longer allowlisted or delegate for the issuing reads")
    void should_notCountAllowlistOrDelegate_when_organizerRoleRemoved() throws Exception {
        mockMvc.perform(patchJson("{\"allowlist\": [\"" + ORG_B + "\"], \"delegateUsername\": \"" + ORG_B + "\"}")
                        .with(organizerA()))
                .andExpect(status().isOk());
        // positive control: an organizer counts
        assertThat(agentSettingsService.isAllowlisted(ORG_B)).isTrue();
        assertThat(agentSettingsService.delegateUsername()).contains(ORG_B);

        jdbc.update("UPDATE role_assignments SET role = 'ATTENDEE' WHERE role = 'ORGANIZER' "
                + "AND user_id = (SELECT id FROM user_profiles WHERE username = ?)", ORG_B);

        assertThat(agentSettingsService.isAllowlisted(ORG_B)).isFalse();
        assertThat(agentSettingsService.delegateUsername()).isEmpty();
    }

    @Test
    @DisplayName("a deleted profile drops out of the allowlist, a deleted delegate becomes null")
    void should_dropDeletedProfile_when_profileDeleted() throws Exception {
        mockMvc.perform(patchJson("{\"allowlist\": [\"" + ORG_A + "\", \"" + ORG_B + "\"], \"delegateUsername\": \""
                        + ORG_B + "\"}").with(organizerA()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowlist", contains(ORG_A, ORG_B)))
                .andExpect(jsonPath("$.delegateUsername").value(ORG_B));

        userRepository.findByUsername(ORG_B).ifPresent(userRepository::delete);
        userRepository.flush();

        mockMvc.perform(get(URL).with(organizerA()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowlist", contains(ORG_A)))
                .andExpect(jsonPath("$.delegateUsername").value(nullValue()));
    }
}
