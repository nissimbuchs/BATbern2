package ch.batbern.companyuser.controller;

import ch.batbern.companyuser.config.TestAwsConfig;
import ch.batbern.companyuser.domain.AgentGrant;
import ch.batbern.companyuser.domain.User;
import ch.batbern.companyuser.dto.generated.UpdateAgentSettingsRequest;
import ch.batbern.companyuser.repository.UserRepository;
import ch.batbern.companyuser.service.AgentGrantService;
import ch.batbern.companyuser.service.AgentSettingsService;
import ch.batbern.companyuser.watch.WatchJwtService;
import ch.batbern.shared.security.Actors;
import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.shared.test.security.TestTokens;
import ch.batbern.shared.types.Role;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static ch.batbern.shared.test.contract.ProviderContract.assertResponseMatchesSpec;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Agent identity, story 5: CUMS issues run grants ({@code POST /internal/agent-grants}) and answers
 * agent eligibility ({@code GET /internal/agent-eligibility}), each checking its caller secret
 * itself; {@link AgentGrantService#findActiveGrant} is the seam story 6 mints tokens against.
 *
 * <p>Production chain and real tokens: a Cognito-shaped JWT through the real
 * {@link JwtAuthenticationConverter}, real agent tokens ({@link TestTokens}) and a real Watch token
 * from {@link WatchJwtService}. Every response, refusals included, is checked against
 * {@code cums-internal.openapi.yml}. Every test commits (no class-level {@code @Transactional});
 * {@code @AfterEach} removes the grants, empties the allowlist, restores the V41 singleton and removes
 * the users.
 */
@Import(TestAwsConfig.class)
@DisplayName("CUMS: agent run grants and agent eligibility")
class InternalAgentGrantIntegrationTest extends AbstractIntegrationTest {

    private static final String CUMS_INTERNAL = "docs/api/internal/cums-internal.openapi.yml";
    private static final String GRANTS = "/internal/agent-grants";
    private static final String ELIGIBILITY = "/internal/agent-eligibility";
    private static final String HEADER = "X-Agent-Caller-Secret";

    /** The values of {@code application-test.yml}. */
    private static final String EMS_SECRET = "test-only-ems-caller-secret";
    private static final String AGENT_SERVICE_SECRET = "test-only-agent-service-caller-secret";
    /** Listed in {@code agent.grants.scheduler-jobs} of {@code application-test.yml}. */
    private static final String LISTED_JOB = "test-digest";

    private static final String REQUESTER = "hongan.agentgrant";
    private static final String REQUESTER_SUB = "a0000000-0000-4000-8000-0000000005a1";
    private static final String DELEGATE = "luca.agentgrant";
    private static final String DELEGATE_SUB = "a0000000-0000-4000-8000-0000000005a2";
    private static final String OUTSIDER = "matthias.agentgrant";
    private static final String OUTSIDER_SUB = "a0000000-0000-4000-8000-0000000005a3";
    private static final String FORMER = "jeanluc.agentgrant";
    private static final String FORMER_SUB = "a0000000-0000-4000-8000-0000000005a4";
    private static final String UNKNOWN = "nobody.agentgrant";
    private static final Set<String> USERNAMES = Set.of(REQUESTER, DELEGATE, OUTSIDER, FORMER);

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
    @Autowired
    private AgentGrantService agentGrantService;
    @Autowired
    private WatchJwtService watchJwtService;

    /** Switched on; REQUESTER, DELEGATE and FORMER allowlisted; DELEGATE the delegate. */
    @BeforeEach
    void seed() {
        cleanUp();
        userRepository.saveAndFlush(user(REQUESTER, REQUESTER_SUB, "Hông Ân", "Meier"));
        userRepository.saveAndFlush(user(DELEGATE, DELEGATE_SUB, "Luca", "Brünnimann"));
        userRepository.saveAndFlush(user(OUTSIDER, OUTSIDER_SUB, "Matthias", "Keller"));
        userRepository.saveAndFlush(user(FORMER, FORMER_SUB, "Jean-Luc", "O’Neill"));
        agentSettingsService.update(new UpdateAgentSettingsRequest()
                .enabled(true)
                .allowlist(List.of(REQUESTER, DELEGATE, FORMER))
                .delegateUsername(DELEGATE), Actors.system("agent-grant-test"));
    }

    /** Removes the grants, restores the singleton as V41 leaves it, then removes the users. */
    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM agent_grants");
        jdbc.update("DELETE FROM agent_allowlist");
        jdbc.update("INSERT INTO agent_settings (id, enabled) VALUES (1, false) "
                + "ON CONFLICT (id) DO UPDATE SET enabled = false, delegate_username = NULL, "
                + "updated_at = NULL, updated_by = NULL");
        USERNAMES.forEach(name -> userRepository.findByUsername(name).ifPresent(userRepository::delete));
        userRepository.flush();
    }

    private static User user(String username, String sub, String first, String last) {
        return User.builder()
                .username(username)
                .email(username + "@example.com")
                .firstName(first)
                .lastName(last)
                .cognitoUserId(sub)
                .roles(new HashSet<>(Set.of(Role.ORGANIZER)))
                .build();
    }

    // ------------------------------------------------------------------ helpers

    /** A Cognito-shaped token through the service's real role mapping, as agent-service forwards it. */
    private RequestPostProcessor cognito(String sub, String username) {
        Converter<Jwt, Collection<GrantedAuthority>> realConversion =
                token -> jwtAuthenticationConverter.convert(token).getAuthorities();
        return jwt().jwt(t -> t.subject(sub)
                        .claim("custom:username", username)
                        .claim("custom:role", "ORGANIZER"))
                .authorities(realConversion);
    }

    private static MockHttpServletRequestBuilder grantRequest(String callerSecret, String body) {
        MockHttpServletRequestBuilder request = post(GRANTS).contentType(MediaType.APPLICATION_JSON).content(body);
        return callerSecret == null ? request : request.header(HEADER, callerSecret);
    }

    private static String emsHandoff(String username) {
        return "{\"trigger\": \"EMS_HANDOFF\", \"username\": \"" + username + "\"}";
    }

    private static String linkedRun(String runId, String secret) {
        return "{\"trigger\": \"LINKED_RUN\", \"linkedRunId\": \"" + runId
                + "\", \"linkedRunGrantSecret\": \"" + secret + "\"}";
    }

    private static String schedulerJob(String job) {
        return "{\"trigger\": \"SCHEDULER_JOB\", \"jobName\": \"" + job + "\"}";
    }

    private static final String ORGANIZER_LOGIN = "{\"trigger\": \"ORGANIZER_LOGIN\"}";

    private MvcResult perform(MockHttpServletRequestBuilder request, int expectedStatus) throws Exception {
        MvcResult result = mockMvc.perform(request).andExpect(status().is(expectedStatus)).andReturn();
        assertResponseMatchesSpec(CUMS_INTERNAL, result);
        return result;
    }

    /** Issues a grant and returns the answer as a map. */
    private Map<String, Object> issued(MockHttpServletRequestBuilder request) throws Exception {
        return JsonPath.read(perform(request, 201).getResponse().getContentAsString(StandardCharsets.UTF_8), "$");
    }

    /** Expects the refusal with status and {@code details.code}. */
    private void refused(MockHttpServletRequestBuilder request, int expectedStatus, String code) throws Exception {
        MvcResult result = perform(request, expectedStatus);
        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        if (code != null) {
            assertThat(JsonPath.<String>read(body, "$.details.code")).as(body).isEqualTo(code);
        }
    }

    private Map<String, Object> requesterGrant() throws Exception {
        return issued(grantRequest(EMS_SECRET, emsHandoff(REQUESTER)));
    }

    private int grantRows() {
        return jdbc.queryForObject("SELECT count(*) FROM agent_grants", Integer.class);
    }

    private void switchOff() {
        jdbc.update("UPDATE agent_settings SET enabled = false WHERE id = 1");
    }

    private void switchOn() {
        jdbc.update("UPDATE agent_settings SET enabled = true WHERE id = 1");
    }

    private void expire(String runId) {
        jdbc.update("UPDATE agent_grants SET issued_at = now() - interval '2 hours', "
                + "expires_at = now() - interval '1 hour' WHERE run_id = ?", runId);
    }

    private static String sha256Hex(String value) throws NoSuchAlgorithmException {
        return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    // ------------------------------------------------------------------ issuing, one per trigger

    @Test
    @DisplayName("EMS handoff: 201 REQUESTER, one hour, only the hash stored, findable through the seam")
    void should_issueRequesterGrant_when_emsHandsOff() throws Exception {
        Map<String, Object> grant = requesterGrant();

        String runId = (String) grant.get("runId");
        String secret = (String) grant.get("grantSecret");
        assertThat(grant.get("principalKind")).isEqualTo("REQUESTER");
        assertThat(grant.get("username")).isEqualTo(REQUESTER);
        assertThat(grant.get("trigger")).isEqualTo("EMS_HANDOFF");
        assertThat(grant.get("linkedRunId")).isNull();
        assertThat(grant.get("jobName")).isNull();
        assertThat(runId).hasSize(36);
        assertThat(secret).hasSize(43).doesNotContain("=", "+", "/");
        Instant issuedAt = Instant.parse((String) grant.get("issuedAt"));
        Instant expiresAt = Instant.parse((String) grant.get("expiresAt"));
        assertThat(Duration.between(issuedAt, expiresAt)).isEqualTo(Duration.ofHours(1));

        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM agent_grants WHERE run_id = ?", runId);
        assertThat(row.get("secret_hash")).isNotEqualTo(secret).isEqualTo(sha256Hex(secret));
        assertThat(row.get("principal_kind")).isEqualTo("requester");
        assertThat(row.get("trigger_kind")).isEqualTo("ems_handoff");
        assertThat(row.get("username")).isEqualTo(REQUESTER);
        assertThat(row.get("requested_by")).isEqualTo("system:ems-agent-handoff");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM agent_grants WHERE secret_hash LIKE ?",
                Integer.class, "%" + secret + "%")).isZero();

        assertThat(agentGrantService.findActiveGrant(runId, secret))
                .get().extracting(AgentGrant::getUsername).isEqualTo(REQUESTER);
    }

    @Test
    @DisplayName("organizer login: 201 ORGANIZER, the username from the forwarded Cognito JWT")
    void should_issueOrganizerGrant_when_organizerLogsIn() throws Exception {
        Map<String, Object> grant = issued(grantRequest(AGENT_SERVICE_SECRET, ORGANIZER_LOGIN)
                .with(cognito(REQUESTER_SUB, REQUESTER)));

        assertThat(grant.get("principalKind")).isEqualTo("ORGANIZER");
        assertThat(grant.get("username")).isEqualTo(REQUESTER);
        assertThat(grant.get("trigger")).isEqualTo("ORGANIZER_LOGIN");
        assertThat(jdbc.queryForObject("SELECT requested_by FROM agent_grants WHERE run_id = ?",
                String.class, grant.get("runId"))).isEqualTo(REQUESTER);
    }

    @Test
    @DisplayName("organizer login without custom:username: the JWT's sub is the username")
    void should_useSub_when_cognitoJwtHasNoUsernameClaim() throws Exception {
        // A Cognito sub that is a username only happens without the PreTokenGen claim; the sub must
        // then be an allowlisted username for the grant to pass.
        Map<String, Object> grant = issued(grantRequest(AGENT_SERVICE_SECRET, ORGANIZER_LOGIN)
                .with(jwt().jwt(t -> t.subject(REQUESTER))));

        assertThat(grant.get("username")).isEqualTo(REQUESTER);
    }

    @Test
    @DisplayName("linked run: 201 ORGANIZER_DELEGATE for the delegate, linkedRunId set")
    void should_issueDelegateGrant_when_linkedToActiveRequesterRun() throws Exception {
        Map<String, Object> parent = requesterGrant();

        Map<String, Object> grant = issued(grantRequest(AGENT_SERVICE_SECRET,
                linkedRun((String) parent.get("runId"), (String) parent.get("grantSecret"))));

        assertThat(grant.get("principalKind")).isEqualTo("ORGANIZER_DELEGATE");
        assertThat(grant.get("username")).isEqualTo(DELEGATE);
        assertThat(grant.get("trigger")).isEqualTo("LINKED_RUN");
        assertThat(grant.get("linkedRunId")).isEqualTo(parent.get("runId"));
        assertThat(grant.get("jobName")).isNull();
        assertThat(jdbc.queryForObject("SELECT requested_by FROM agent_grants WHERE run_id = ?",
                String.class, grant.get("runId"))).isEqualTo("system:agent-service");
    }

    @Test
    @DisplayName("scheduler job: 201 ORGANIZER_DELEGATE for the delegate, jobName set")
    void should_issueDelegateGrant_when_listedSchedulerJob() throws Exception {
        Map<String, Object> grant = issued(grantRequest(AGENT_SERVICE_SECRET, schedulerJob(LISTED_JOB)));

        assertThat(grant.get("principalKind")).isEqualTo("ORGANIZER_DELEGATE");
        assertThat(grant.get("username")).isEqualTo(DELEGATE);
        assertThat(grant.get("trigger")).isEqualTo("SCHEDULER_JOB");
        assertThat(grant.get("jobName")).isEqualTo(LISTED_JOB);
        assertThat(grant.get("linkedRunId")).isNull();
    }

    // ------------------------------------------------------------------ switch off

    @Test
    @DisplayName("switch off: EMS handoff 403 AGENT_SWITCHED_OFF, no row; on again 201")
    void should_refuseEmsHandoff_when_switchedOff() throws Exception {
        switchOff();
        refused(grantRequest(EMS_SECRET, emsHandoff(REQUESTER)), 403, "AGENT_SWITCHED_OFF");
        assertThat(grantRows()).isZero();

        switchOn();
        requesterGrant();
        assertThat(grantRows()).isEqualTo(1);
    }

    @Test
    @DisplayName("switch off: organizer login 403 AGENT_SWITCHED_OFF, no row; on again 201")
    void should_refuseOrganizerLogin_when_switchedOff() throws Exception {
        switchOff();
        refused(grantRequest(AGENT_SERVICE_SECRET, ORGANIZER_LOGIN).with(cognito(REQUESTER_SUB, REQUESTER)),
                403, "AGENT_SWITCHED_OFF");
        assertThat(grantRows()).isZero();

        switchOn();
        issued(grantRequest(AGENT_SERVICE_SECRET, ORGANIZER_LOGIN).with(cognito(REQUESTER_SUB, REQUESTER)));
    }

    @Test
    @DisplayName("switch off: linked run 403 AGENT_SWITCHED_OFF, only the parent row; on again 201")
    void should_refuseLinkedRun_when_switchedOff() throws Exception {
        Map<String, Object> parent = requesterGrant();
        String body = linkedRun((String) parent.get("runId"), (String) parent.get("grantSecret"));

        switchOff();
        refused(grantRequest(AGENT_SERVICE_SECRET, body), 403, "AGENT_SWITCHED_OFF");
        assertThat(grantRows()).isEqualTo(1);

        switchOn();
        issued(grantRequest(AGENT_SERVICE_SECRET, body));
        assertThat(grantRows()).isEqualTo(2);
    }

    @Test
    @DisplayName("switch off: scheduler job 403 AGENT_SWITCHED_OFF, no row; on again 201")
    void should_refuseSchedulerJob_when_switchedOff() throws Exception {
        switchOff();
        refused(grantRequest(AGENT_SERVICE_SECRET, schedulerJob(LISTED_JOB)), 403, "AGENT_SWITCHED_OFF");
        assertThat(grantRows()).isZero();

        switchOn();
        issued(grantRequest(AGENT_SERVICE_SECRET, schedulerJob(LISTED_JOB)));
    }

    @Test
    @DisplayName("switch off wins over a principal that is not listed (check order)")
    void should_answerSwitchedOff_before_principal() throws Exception {
        switchOff();
        refused(grantRequest(EMS_SECRET, emsHandoff(OUTSIDER)), 403, "AGENT_SWITCHED_OFF");
    }

    // ------------------------------------------------------------------ principal not eligible

    @ParameterizedTest(name = "EMS handoff for {0}")
    @ValueSource(strings = {OUTSIDER, UNKNOWN})
    @DisplayName("EMS handoff for a user not allowlisted or unknown: 403 PRINCIPAL_NOT_ELIGIBLE, no row")
    void should_refuseEmsHandoff_when_userNotListed(String username) throws Exception {
        refused(grantRequest(EMS_SECRET, emsHandoff(username)), 403, "PRINCIPAL_NOT_ELIGIBLE");
        assertThat(grantRows()).isZero();

        requesterGrant(); // positive control: the listed organizer gets one
        assertThat(grantRows()).isEqualTo(1);
    }

    @Test
    @DisplayName("EMS handoff for an allowlisted user who lost ORGANIZER: 403 PRINCIPAL_NOT_ELIGIBLE")
    void should_refuseEmsHandoff_when_userLostOrganizer() throws Exception {
        issued(grantRequest(EMS_SECRET, emsHandoff(FORMER))); // positive control, still an organizer
        jdbc.update("DELETE FROM agent_grants");

        jdbc.update("DELETE FROM role_assignments WHERE user_id = "
                + "(SELECT id FROM user_profiles WHERE username = ?)", FORMER);

        refused(grantRequest(EMS_SECRET, emsHandoff(FORMER)), 403, "PRINCIPAL_NOT_ELIGIBLE");
        assertThat(grantRows()).isZero();
    }

    @Test
    @DisplayName("organizer login of an organizer not on the allowlist: 403 PRINCIPAL_NOT_ELIGIBLE")
    void should_refuseOrganizerLogin_when_notListed() throws Exception {
        refused(grantRequest(AGENT_SERVICE_SECRET, ORGANIZER_LOGIN).with(cognito(OUTSIDER_SUB, OUTSIDER)),
                403, "PRINCIPAL_NOT_ELIGIBLE");
        assertThat(grantRows()).isZero();

        issued(grantRequest(AGENT_SERVICE_SECRET, ORGANIZER_LOGIN).with(cognito(REQUESTER_SUB, REQUESTER))); // positive control
    }

    @Test
    @DisplayName("organizer login of an allowlisted user who lost ORGANIZER: 403 PRINCIPAL_NOT_ELIGIBLE")
    void should_refuseOrganizerLogin_when_lostOrganizer() throws Exception {
        issued(grantRequest(AGENT_SERVICE_SECRET, ORGANIZER_LOGIN).with(cognito(FORMER_SUB, FORMER))); // positive control
        jdbc.update("DELETE FROM agent_grants");

        jdbc.update("DELETE FROM role_assignments WHERE user_id = "
                + "(SELECT id FROM user_profiles WHERE username = ?)", FORMER);

        refused(grantRequest(AGENT_SERVICE_SECRET, ORGANIZER_LOGIN).with(cognito(FORMER_SUB, FORMER)),
                403, "PRINCIPAL_NOT_ELIGIBLE");
        assertThat(grantRows()).isZero();
    }

    @Test
    @DisplayName("no delegate: linked run and scheduler job 403 PRINCIPAL_NOT_ELIGIBLE, no row")
    void should_refuseDelegateTriggers_when_noDelegate() throws Exception {
        Map<String, Object> parent = requesterGrant();
        String linked = linkedRun((String) parent.get("runId"), (String) parent.get("grantSecret"));
        jdbc.update("UPDATE agent_settings SET delegate_username = NULL WHERE id = 1");

        refused(grantRequest(AGENT_SERVICE_SECRET, linked), 403, "PRINCIPAL_NOT_ELIGIBLE");
        refused(grantRequest(AGENT_SERVICE_SECRET, schedulerJob(LISTED_JOB)), 403, "PRINCIPAL_NOT_ELIGIBLE");
        assertThat(grantRows()).isEqualTo(1);

        jdbc.update("UPDATE agent_settings SET delegate_username = ? WHERE id = 1", DELEGATE);
        issued(grantRequest(AGENT_SERVICE_SECRET, linked));
        issued(grantRequest(AGENT_SERVICE_SECRET, schedulerJob(LISTED_JOB)));
    }

    @Test
    @DisplayName("a delegate who is not allowlisted: 403 PRINCIPAL_NOT_ELIGIBLE (R5 holds for the delegate)")
    void should_refuseDelegateTriggers_when_delegateNotListed() throws Exception {
        jdbc.update("UPDATE agent_settings SET delegate_username = ? WHERE id = 1", OUTSIDER);

        refused(grantRequest(AGENT_SERVICE_SECRET, schedulerJob(LISTED_JOB)), 403, "PRINCIPAL_NOT_ELIGIBLE");
        assertThat(grantRows()).isZero();

        jdbc.update("UPDATE agent_settings SET delegate_username = ? WHERE id = 1", DELEGATE);
        issued(grantRequest(AGENT_SERVICE_SECRET, schedulerJob(LISTED_JOB))); // positive control
    }

    @Test
    @DisplayName("linked run with a delegate who is not allowlisted: 403 PRINCIPAL_NOT_ELIGIBLE, no row")
    void should_refuseLinkedRun_when_delegateNotListed() throws Exception {
        Map<String, Object> parent = requesterGrant();
        String linked = linkedRun((String) parent.get("runId"), (String) parent.get("grantSecret"));
        jdbc.update("UPDATE agent_settings SET delegate_username = ? WHERE id = 1", OUTSIDER);

        refused(grantRequest(AGENT_SERVICE_SECRET, linked), 403, "PRINCIPAL_NOT_ELIGIBLE");
        assertThat(grantRows()).isEqualTo(1);

        jdbc.update("UPDATE agent_settings SET delegate_username = ? WHERE id = 1", DELEGATE);
        issued(grantRequest(AGENT_SERVICE_SECRET, linked)); // positive control
        assertThat(grantRows()).isEqualTo(2);
    }

    @Test
    @DisplayName("a delegate who lost ORGANIZER: 403 PRINCIPAL_NOT_ELIGIBLE")
    void should_refuseDelegateTriggers_when_delegateLostOrganizer() throws Exception {
        jdbc.update("UPDATE agent_settings SET delegate_username = ? WHERE id = 1", FORMER);
        issued(grantRequest(AGENT_SERVICE_SECRET, schedulerJob(LISTED_JOB))); // positive control
        jdbc.update("DELETE FROM agent_grants");

        jdbc.update("DELETE FROM role_assignments WHERE user_id = "
                + "(SELECT id FROM user_profiles WHERE username = ?)", FORMER);

        refused(grantRequest(AGENT_SERVICE_SECRET, schedulerJob(LISTED_JOB)), 403, "PRINCIPAL_NOT_ELIGIBLE");
        assertThat(grantRows()).isZero();
    }

    // ------------------------------------------------------------------ caller secret

    @ParameterizedTest(name = "{0} with {1} secret")
    @CsvSource({
        "EMS_HANDOFF, missing",
        "EMS_HANDOFF, wrong",
        "EMS_HANDOFF, other",
        "ORGANIZER_LOGIN, missing",
        "ORGANIZER_LOGIN, wrong",
        "ORGANIZER_LOGIN, other",
        "LINKED_RUN, missing",
        "LINKED_RUN, wrong",
        "LINKED_RUN, other",
        "SCHEDULER_JOB, missing",
        "SCHEDULER_JOB, wrong",
        "SCHEDULER_JOB, other",
    })
    @DisplayName("caller secret missing, wrong or the other caller's: 401 CALLER_SECRET_INVALID, no row")
    void should_refuse_when_callerSecretInvalid(String trigger, String variant) throws Exception {
        String rightSecret = "EMS_HANDOFF".equals(trigger) ? EMS_SECRET : AGENT_SERVICE_SECRET;
        String otherSecret = "EMS_HANDOFF".equals(trigger) ? AGENT_SERVICE_SECRET : EMS_SECRET;
        String body;
        int before = 0;
        switch (trigger) {
            case "EMS_HANDOFF" -> body = emsHandoff(REQUESTER);
            case "ORGANIZER_LOGIN" -> body = ORGANIZER_LOGIN;
            case "LINKED_RUN" -> {
                Map<String, Object> parent = requesterGrant();
                body = linkedRun((String) parent.get("runId"), (String) parent.get("grantSecret"));
                before = 1;
            }
            default -> body = schedulerJob(LISTED_JOB);
        }
        String presented = switch (variant) {
            case "missing" -> null;
            case "wrong" -> rightSecret + "-wrong";
            default -> otherSecret;
        };

        refused(grantRequest(presented, body).with(cognito(REQUESTER_SUB, REQUESTER)), 401, "CALLER_SECRET_INVALID");
        assertThat(grantRows()).isEqualTo(before);

        issued(grantRequest(rightSecret, body).with(cognito(REQUESTER_SUB, REQUESTER))); // positive control
        assertThat(grantRows()).isEqualTo(before + 1);
    }

    @Test
    @DisplayName("caller secret is checked before the body and the switch: an unknown caller learns nothing")
    void should_answerCallerSecret_before_bodyAndSwitch() throws Exception {
        switchOff();
        refused(grantRequest("guess", emsHandoff(REQUESTER)), 401, "CALLER_SECRET_INVALID");
        refused(grantRequest("guess", "{\"trigger\": \"NO_SUCH_TRIGGER\"}"), 401, "CALLER_SECRET_INVALID");
        refused(grantRequest(null, "{}"), 401, "CALLER_SECRET_INVALID");
    }

    // ------------------------------------------------------------------ organizer login

    @Test
    @DisplayName("organizer login without a forwarded JWT: 401 ORGANIZER_LOGIN_REQUIRED, no row")
    void should_refuseOrganizerLogin_when_noJwt() throws Exception {
        refused(grantRequest(AGENT_SERVICE_SECRET, ORGANIZER_LOGIN), 401, "ORGANIZER_LOGIN_REQUIRED");
        assertThat(grantRows()).isZero();

        issued(grantRequest(AGENT_SERVICE_SECRET, ORGANIZER_LOGIN).with(cognito(REQUESTER_SUB, REQUESTER)));
    }

    @Test
    @DisplayName("organizer login with a real agent token: 401 ORGANIZER_LOGIN_REQUIRED, no row")
    void should_refuseOrganizerLogin_when_agentToken() throws Exception {
        String agentToken = TestTokens.agentToken(REQUESTER_SUB, REQUESTER, "run-agent-grant-1", "organizer");

        refused(grantRequest(AGENT_SERVICE_SECRET, ORGANIZER_LOGIN)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + agentToken), 401, "ORGANIZER_LOGIN_REQUIRED");
        assertThat(grantRows()).isZero();

        issued(grantRequest(AGENT_SERVICE_SECRET, ORGANIZER_LOGIN).with(cognito(REQUESTER_SUB, REQUESTER))); // positive control
    }

    @Test
    @DisplayName("organizer login with a real Watch token: 401 ORGANIZER_LOGIN_REQUIRED, no row")
    void should_refuseOrganizerLogin_when_watchToken() throws Exception {
        String watchToken = watchJwtService.generateTokenWithExpiry(REQUESTER).jwt();

        refused(grantRequest(AGENT_SERVICE_SECRET, ORGANIZER_LOGIN)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + watchToken), 401, "ORGANIZER_LOGIN_REQUIRED");
        assertThat(grantRows()).isZero();

        issued(grantRequest(AGENT_SERVICE_SECRET, ORGANIZER_LOGIN).with(cognito(REQUESTER_SUB, REQUESTER))); // positive control
    }

    @Test
    @DisplayName("a Cognito-shaped JWT that carries act is no organizer login either")
    void should_refuseOrganizerLogin_when_jwtCarriesAct() throws Exception {
        refused(grantRequest(AGENT_SERVICE_SECRET, ORGANIZER_LOGIN)
                .with(jwt().jwt(t -> t.subject(REQUESTER_SUB).claim("custom:username", REQUESTER)
                        .claim("act", Map.of("run", "run-x", "principal", "organizer-assistant")))),
                401, "ORGANIZER_LOGIN_REQUIRED");
        assertThat(grantRows()).isZero();

        issued(grantRequest(AGENT_SERVICE_SECRET, ORGANIZER_LOGIN).with(cognito(REQUESTER_SUB, REQUESTER))); // positive control
    }

    @Test
    @DisplayName("a bearer token that does not decode: the filter's bare 401 before the caller secret, no row")
    void should_refuseWithFilter401_when_bearerUndecodable() throws Exception {
        Instant past = Instant.now().minus(Duration.ofHours(1));
        String expired = TestTokens.agent(REQUESTER_SUB, REQUESTER, "run-agent-grant-2", "organizer-assistant")
                .claims(b -> b.issueTime(Date.from(past.minusSeconds(60))).expirationTime(Date.from(past)))
                .build();

        MvcResult result = mockMvc.perform(grantRequest(EMS_SECRET, emsHandoff(REQUESTER))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + expired))
                .andExpect(status().isUnauthorized())
                .andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("details");
        assertThat(grantRows()).isZero();

        requesterGrant(); // positive control: the same request without the broken bearer
        assertThat(grantRows()).isEqualTo(1);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"malformed", "wrong-signature"})
    @DisplayName("an undecodable bearer and no caller secret: the filter's bare 401, no row (review C6)")
    void should_refuseWithFilter401_when_bearerUndecodableAndNoCallerSecret(String kind) throws Exception {
        String bearer = "malformed".equals(kind)
                ? "not-a-jwt"
                : TestTokens.agent(REQUESTER_SUB, REQUESTER, "run-agent-grant-3", "organizer")
                        .signedWith(TestTokens.newRsaKeyPair()).build();

        MvcResult result = mockMvc.perform(grantRequest(null, emsHandoff(REQUESTER))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearer))
                .andExpect(status().isUnauthorized())
                .andReturn();
        assertThat(result.getResponse().getContentAsString()).isEmpty();
        assertThat(result.getResponse().getHeader(HttpHeaders.WWW_AUTHENTICATE))
                .startsWith("Bearer").contains("error=\"invalid_token\"");
        assertThat(grantRows()).isZero();

        requesterGrant(); // positive control: the same body with the caller secret and no bearer
        assertThat(grantRows()).isEqualTo(1);
    }

    // ------------------------------------------------------------------ linked run, job

    @Test
    @DisplayName("linked run unknown: 403 LINKED_RUN_INVALID, no row")
    void should_refuseLinkedRun_when_unknownRun() throws Exception {
        Map<String, Object> parent = requesterGrant();

        refused(grantRequest(AGENT_SERVICE_SECRET,
                linkedRun("00000000-0000-4000-8000-000000000000", (String) parent.get("grantSecret"))),
                403, "LINKED_RUN_INVALID");
        assertThat(grantRows()).isEqualTo(1);

        issued(grantRequest(AGENT_SERVICE_SECRET,
                linkedRun((String) parent.get("runId"), (String) parent.get("grantSecret"))));
    }

    @Test
    @DisplayName("linked run with a wrong grant secret: 403 LINKED_RUN_INVALID, no row")
    void should_refuseLinkedRun_when_wrongGrantSecret() throws Exception {
        Map<String, Object> parent = requesterGrant();
        Map<String, Object> other = requesterGrant();

        refused(grantRequest(AGENT_SERVICE_SECRET,
                linkedRun((String) parent.get("runId"), (String) other.get("grantSecret"))),
                403, "LINKED_RUN_INVALID");
        assertThat(grantRows()).isEqualTo(2);

        issued(grantRequest(AGENT_SERVICE_SECRET,
                linkedRun((String) parent.get("runId"), (String) parent.get("grantSecret")))); // positive control
    }

    @Test
    @DisplayName("linked run expired: 403 LINKED_RUN_INVALID, no row")
    void should_refuseLinkedRun_when_expired() throws Exception {
        Map<String, Object> parent = requesterGrant();
        String body = linkedRun((String) parent.get("runId"), (String) parent.get("grantSecret"));
        expire((String) parent.get("runId"));

        refused(grantRequest(AGENT_SERVICE_SECRET, body), 403, "LINKED_RUN_INVALID");
        assertThat(grantRows()).isEqualTo(1);

        Map<String, Object> fresh = requesterGrant(); // positive control: an active parent links
        issued(grantRequest(AGENT_SERVICE_SECRET,
                linkedRun((String) fresh.get("runId"), (String) fresh.get("grantSecret"))));
    }

    @Test
    @DisplayName("linked run that is not a REQUESTER grant: 403 LINKED_RUN_INVALID, no row")
    void should_refuseLinkedRun_when_parentNotRequester() throws Exception {
        Map<String, Object> organizer = issued(grantRequest(AGENT_SERVICE_SECRET, ORGANIZER_LOGIN)
                .with(cognito(REQUESTER_SUB, REQUESTER)));
        Map<String, Object> job = issued(grantRequest(AGENT_SERVICE_SECRET, schedulerJob(LISTED_JOB)));

        refused(grantRequest(AGENT_SERVICE_SECRET,
                linkedRun((String) organizer.get("runId"), (String) organizer.get("grantSecret"))),
                403, "LINKED_RUN_INVALID");
        refused(grantRequest(AGENT_SERVICE_SECRET,
                linkedRun((String) job.get("runId"), (String) job.get("grantSecret"))),
                403, "LINKED_RUN_INVALID");
        assertThat(grantRows()).isEqualTo(2);

        Map<String, Object> requester = requesterGrant(); // positive control: a requester parent links
        issued(grantRequest(AGENT_SERVICE_SECRET,
                linkedRun((String) requester.get("runId"), (String) requester.get("grantSecret"))));
    }

    @Test
    @DisplayName("scheduler job not listed: 403 SCHEDULER_JOB_UNKNOWN, no row")
    void should_refuseSchedulerJob_when_notListed() throws Exception {
        refused(grantRequest(AGENT_SERVICE_SECRET, schedulerJob("weekly-cleanup")), 403, "SCHEDULER_JOB_UNKNOWN");
        assertThat(grantRows()).isZero();

        issued(grantRequest(AGENT_SERVICE_SECRET, schedulerJob(LISTED_JOB)));
    }

    // ------------------------------------------------------------------ bad body

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', value = {
        "EMS_HANDOFF         | {\"trigger\": \"NO_SUCH_TRIGGER\"}",
        "EMS_HANDOFF         | {\"username\": \"hongan.agentgrant\"}",
        "EMS_HANDOFF         | {\"trigger\": \"EMS_HANDOFF\"}",
        "EMS_HANDOFF         | {\"trigger\": \"EMS_HANDOFF\", \"username\": \"\"}",
        "EMS_HANDOFF         | {\"trigger\": \"EMS_HANDOFF\", \"username\": \"hongan.agentgrant\", \"jobName\": \"test-digest\"}",
        "EMS_HANDOFF         | {\"trigger\": \"EMS_HANDOFF\", \"username\": \"hongan.agentgrant\", \"unknown\": 1}",
        "ORGANIZER_LOGIN     | {\"trigger\": \"ORGANIZER_LOGIN\", \"username\": \"hongan.agentgrant\"}",
        "LINKED_RUN          | {\"trigger\": \"LINKED_RUN\", \"linkedRunId\": \"00000000-0000-4000-8000-000000000000\"}",
        "LINKED_RUN          | {\"trigger\": \"LINKED_RUN\", \"linkedRunGrantSecret\": \"x\"}",
        "SCHEDULER_JOB       | {\"trigger\": \"SCHEDULER_JOB\"}",
        "SCHEDULER_JOB       | {\"trigger\": \"SCHEDULER_JOB\", \"jobName\": \"test-digest\", \"username\": \"luca.agentgrant\"}",
    })
    @DisplayName("bad body: 400, no row")
    void should_answer400_when_bodyBad(String caller, String body) throws Exception {
        String secret = "EMS_HANDOFF".equals(caller) ? EMS_SECRET : AGENT_SERVICE_SECRET;

        refused(grantRequest(secret, body).with(cognito(REQUESTER_SUB, REQUESTER)), 400, "INVALID_AGENT_REQUEST");
        assertThat(grantRows()).isZero();

        // positive control: the nearest valid body for the same caller and trigger
        Map<String, Object> parent = "LINKED_RUN".equals(caller) ? requesterGrant() : null;
        String valid = switch (caller) {
            case "EMS_HANDOFF" -> emsHandoff(REQUESTER);
            case "ORGANIZER_LOGIN" -> ORGANIZER_LOGIN;
            case "LINKED_RUN" -> linkedRun((String) parent.get("runId"), (String) parent.get("grantSecret"));
            default -> schedulerJob(LISTED_JOB);
        };
        issued(grantRequest(secret, valid).with(cognito(REQUESTER_SUB, REQUESTER)));
    }

    // ------------------------------------------------------------------ eligibility

    private static MockHttpServletRequestBuilder eligibility(String callerSecret, String username) {
        MockHttpServletRequestBuilder request = get(ELIGIBILITY);
        if (username != null) {
            request.param("username", username);
        }
        return callerSecret == null ? request : request.header(HEADER, callerSecret);
    }

    @Test
    @DisplayName("eligibility of a listed organizer: 200 eligible true")
    void should_answerEligible_when_listed() throws Exception {
        MvcResult result = perform(eligibility(EMS_SECRET, REQUESTER), 200);

        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(JsonPath.<String>read(body, "$.username")).isEqualTo(REQUESTER);
        assertThat(JsonPath.<Boolean>read(body, "$.eligible")).isTrue();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {OUTSIDER, UNKNOWN})
    @DisplayName("eligibility of a user not listed or unknown: 403 PRINCIPAL_NOT_ELIGIBLE")
    void should_refuseEligibility_when_notListed(String username) throws Exception {
        refused(eligibility(EMS_SECRET, username), 403, "PRINCIPAL_NOT_ELIGIBLE");
    }

    @Test
    @DisplayName("eligibility of an allowlisted user who lost ORGANIZER: 403 PRINCIPAL_NOT_ELIGIBLE")
    void should_refuseEligibility_when_lostOrganizer() throws Exception {
        perform(eligibility(EMS_SECRET, FORMER), 200);
        jdbc.update("DELETE FROM role_assignments WHERE user_id = "
                + "(SELECT id FROM user_profiles WHERE username = ?)", FORMER);

        refused(eligibility(EMS_SECRET, FORMER), 403, "PRINCIPAL_NOT_ELIGIBLE");
    }

    @Test
    @DisplayName("eligibility with the switch off: 403 AGENT_SWITCHED_OFF")
    void should_refuseEligibility_when_switchedOff() throws Exception {
        switchOff();
        refused(eligibility(EMS_SECRET, REQUESTER), 403, "AGENT_SWITCHED_OFF");
    }

    @ParameterizedTest(name = "secret {0}")
    @ValueSource(strings = {"missing", "wrong", "agent-service"})
    @DisplayName("eligibility with a missing, wrong or the agent service's secret: 401 CALLER_SECRET_INVALID")
    void should_refuseEligibility_when_callerSecretInvalid(String variant) throws Exception {
        String presented = switch (variant) {
            case "missing" -> null;
            case "wrong" -> EMS_SECRET + "-wrong";
            default -> AGENT_SERVICE_SECRET;
        };

        refused(eligibility(presented, REQUESTER), 401, "CALLER_SECRET_INVALID");
        perform(eligibility(EMS_SECRET, REQUESTER), 200); // positive control
    }

    @Test
    @DisplayName("eligibility without username: 400; a bad secret still answers 401 first")
    void should_answer400_when_eligibilityWithoutUsername() throws Exception {
        refused(eligibility(EMS_SECRET, null), 400, "INVALID_AGENT_REQUEST");
        refused(eligibility(EMS_SECRET, ""), 400, "INVALID_AGENT_REQUEST");
        refused(eligibility(EMS_SECRET, "a".repeat(101)), 400, "INVALID_AGENT_REQUEST");
        refused(eligibility(null, null), 401, "CALLER_SECRET_INVALID");
    }

    // ------------------------------------------------------------------ the seam for story 6

    @Test
    @DisplayName("findActiveGrant: found with run id and secret; empty for a wrong secret, an unknown run, an expired row")
    void should_findOnlyActiveGrant_when_runIdAndSecretMatch() throws Exception {
        Map<String, Object> grant = requesterGrant();
        String runId = (String) grant.get("runId");
        String secret = (String) grant.get("grantSecret");

        assertThat(agentGrantService.findActiveGrant(runId, secret)).isPresent();
        assertThat(agentGrantService.findActiveGrant(runId, secret + "x")).isEmpty();
        assertThat(agentGrantService.findActiveGrant(runId, null)).isEmpty();
        assertThat(agentGrantService.findActiveGrant("00000000-0000-4000-8000-000000000000", secret)).isEmpty();
        assertThat(agentGrantService.findActiveGrant(null, secret)).isEmpty();

        expire(runId);
        assertThat(agentGrantService.findActiveGrant(runId, secret)).isEmpty();
    }
}
