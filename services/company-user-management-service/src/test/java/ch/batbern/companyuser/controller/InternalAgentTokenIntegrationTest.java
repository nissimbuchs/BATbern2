package ch.batbern.companyuser.controller;

import ch.batbern.companyuser.config.TestAwsConfig;
import ch.batbern.companyuser.domain.AgentPrincipalKind;
import ch.batbern.companyuser.domain.User;
import ch.batbern.companyuser.dto.generated.UpdateAgentSettingsRequest;
import ch.batbern.companyuser.repository.UserRepository;
import ch.batbern.companyuser.service.AgentSettingsService;
import ch.batbern.shared.security.Actors;
import ch.batbern.shared.security.AgentTokenValidator;
import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.shared.test.security.TestTokens;
import ch.batbern.shared.types.Role;
import com.jayway.jsonpath.JsonPath;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static ch.batbern.shared.test.contract.ProviderContract.assertResponseMatchesSpec;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Agent identity, story 6: CUMS mints delegated agent tokens ({@code POST /internal/agent-tokens})
 * for the agent service against an active run grant, re-checking switch and allowlist on every mint.
 *
 * <p>Grants are issued through the real {@code POST /internal/agent-grants}; minted tokens are decoded
 * with the context's real {@link JwtDecoder} and used on the public API through the production chain.
 * Every response, refusals included, is checked against {@code cums-internal.openapi.yml}. Every test
 * commits (no class-level {@code @Transactional}); {@code @AfterEach} removes companies, grants,
 * allowlist, settings and users.
 */
@Import(TestAwsConfig.class)
@DisplayName("CUMS: delegated agent tokens")
class InternalAgentTokenIntegrationTest extends AbstractIntegrationTest {

    private static final String CUMS_INTERNAL = "docs/api/internal/cums-internal.openapi.yml";
    private static final String GRANTS = "/internal/agent-grants";
    private static final String TOKENS = "/internal/agent-tokens";
    private static final String HEADER = "X-Agent-Caller-Secret";

    /** The values of {@code application-test.yml}. */
    private static final String EMS_SECRET = "test-only-ems-caller-secret";
    private static final String AGENT_SERVICE_SECRET = "test-only-agent-service-caller-secret";

    private static final String REQUESTER = "hongan.agenttoken";
    private static final String REQUESTER_SUB = "a0000000-0000-4000-8000-0000000006a1";
    private static final String DELEGATE = "sean.agenttoken";
    private static final String DELEGATE_SUB = "a0000000-0000-4000-8000-0000000006a2";
    private static final String NO_LOGIN = "luca.agenttoken";
    private static final Set<String> USERNAMES = Set.of(REQUESTER, DELEGATE, NO_LOGIN);
    private static final String COMPANY_DISPLAY = "Agent Token Brünnimann AG";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private AgentSettingsService agentSettingsService;
    @Autowired
    private JwtDecoder jwtDecoder;

    /** Switched on; all three allowlisted organizers, DELEGATE the delegate; NO_LOGIN has no sub. */
    @BeforeEach
    void seed() {
        cleanUp();
        userRepository.saveAndFlush(user(REQUESTER, REQUESTER_SUB, "Hông Ân", "Meier"));
        userRepository.saveAndFlush(user(DELEGATE, DELEGATE_SUB, "Sean", "O’Neill"));
        userRepository.saveAndFlush(user(NO_LOGIN, null, "Luca", "Brünnimann"));
        agentSettingsService.update(new UpdateAgentSettingsRequest()
                .enabled(true)
                .allowlist(List.of(REQUESTER, DELEGATE, NO_LOGIN))
                .delegateUsername(DELEGATE), Actors.system("agent-token-test"));
    }

    /** Removes companies and grants, restores the singleton as V41 leaves it, then removes the users. */
    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM companies WHERE display_name = ?", COMPANY_DISPLAY);
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

    private record Grant(String runId, String secret) {
    }

    private MvcResult perform(MockHttpServletRequestBuilder request, int expectedStatus) throws Exception {
        MvcResult result = mockMvc.perform(request).andExpect(status().is(expectedStatus)).andReturn();
        assertResponseMatchesSpec(CUMS_INTERNAL, result);
        return result;
    }

    private Grant grant(String body) throws Exception {
        MvcResult result = perform(post(GRANTS).header(HEADER, body.contains("EMS_HANDOFF") ? EMS_SECRET
                        : AGENT_SERVICE_SECRET).contentType(MediaType.APPLICATION_JSON).content(body), 201);
        String json = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        return new Grant(JsonPath.read(json, "$.runId"), JsonPath.read(json, "$.grantSecret"));
    }

    private Grant requesterGrant(String username) throws Exception {
        return grant("{\"trigger\": \"EMS_HANDOFF\", \"username\": \"" + username + "\"}");
    }

    private static MockHttpServletRequestBuilder mintRequest(String callerSecret, String body) {
        MockHttpServletRequestBuilder request = post(TOKENS).contentType(MediaType.APPLICATION_JSON).content(body);
        return callerSecret == null ? request : request.header(HEADER, callerSecret);
    }

    private static String body(Grant grant) {
        return body(grant.runId(), grant.secret());
    }

    private static String body(String runId, String secret) {
        return "{\"runId\": \"" + runId + "\", \"grantSecret\": \"" + secret + "\"}";
    }

    /** Mints and returns {@code token} and {@code expiresAt}. */
    private Map<String, Object> minted(Grant grant) throws Exception {
        return JsonPath.read(perform(mintRequest(AGENT_SERVICE_SECRET, body(grant)), 200)
                .getResponse().getContentAsString(StandardCharsets.UTF_8), "$");
    }

    /** Expects the refusal with status and {@code details.code}, and no token in the body. */
    private void refused(MockHttpServletRequestBuilder request, int expectedStatus, String code) throws Exception {
        String body = perform(request, expectedStatus).getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(JsonPath.<String>read(body, "$.details.code")).as(body).isEqualTo(code);
        assertThat(body).doesNotContain("\"token\"").doesNotContain("eyJ");
    }

    private void switchOff() {
        jdbc.update("UPDATE agent_settings SET enabled = false WHERE id = 1");
    }

    // ------------------------------------------------------------------ minting

    @Test
    @DisplayName("mint: 200, the real decoder accepts it, claims exactly the fixed set, lifetime ≤ 300 s")
    void should_mintToken_when_activeGrantOfAllowlistedOrganizer() throws Exception {
        Grant grant = requesterGrant(REQUESTER);

        Map<String, Object> answer = minted(grant);

        String token = (String) answer.get("token");
        Jwt jwt = jwtDecoder.decode(token);
        assertThat(jwt.getHeaders()).containsEntry("alg", "RS256").containsEntry("typ", "JWT");
        assertThat(jwt.getClaims().keySet())
                .containsExactlyInAnyOrder("iss", "sub", "custom:username", "act", "iat", "exp");
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("batbern-agent");
        assertThat(jwt.getSubject()).isEqualTo(REQUESTER_SUB);
        assertThat(jwt.getClaimAsString("custom:username")).isEqualTo(REQUESTER);
        assertThat(jwt.getClaimAsMap("act")).isEqualTo(Map.of("run", grant.runId(), "principal", "requester"));
        Duration lifetime = Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt());
        assertThat(lifetime).isPositive().isLessThanOrEqualTo(AgentTokenValidator.MAX_LIFETIME);
        assertThat(jwt.getIssuedAt()).isBetween(Instant.now().minusSeconds(30), Instant.now().plusSeconds(1));
        assertThat(Instant.parse((String) answer.get("expiresAt"))).isEqualTo(jwt.getExpiresAt());
        assertThat(Actors.forJwt(jwt)).isEqualTo("agent:" + grant.runId() + " for " + REQUESTER);
    }

    @Test
    @DisplayName("a delegate run: act.principal organizer_delegate, sub of the delegate")
    void should_mintDelegateToken_when_linkedRunGrant() throws Exception {
        Grant parent = requesterGrant(REQUESTER);
        Grant linked = grant("{\"trigger\": \"LINKED_RUN\", \"linkedRunId\": \"" + parent.runId()
                + "\", \"linkedRunGrantSecret\": \"" + parent.secret() + "\"}");

        Jwt jwt = jwtDecoder.decode((String) minted(linked).get("token"));

        assertThat(jwt.getSubject()).isEqualTo(DELEGATE_SUB);
        assertThat(jwt.getClaimAsString("custom:username")).isEqualTo(DELEGATE);
        assertThat(jwt.getClaimAsMap("act"))
                .isEqualTo(Map.of("run", linked.runId(), "principal", "organizer_delegate"));
    }

    @Test
    @DisplayName("the minted token on POST /api/v1/companies: 201, created_by = agent:<run> for <username>")
    void should_recordAgentActor_when_mintedTokenUsedOnPublicApi() throws Exception {
        Grant grant = requesterGrant(REQUESTER);
        String token = (String) minted(grant).get("token");

        mockMvc.perform(post("/api/v1/companies")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"AgentTokenCo\", \"displayName\": \"" + COMPANY_DISPLAY + "\"}"))
                .andExpect(status().isCreated());

        assertThat(jdbc.queryForObject("SELECT created_by FROM companies WHERE display_name = ?", String.class,
                COMPANY_DISPLAY)).isEqualTo("agent:" + grant.runId() + " for " + REQUESTER);
    }

    @Test
    @DisplayName("grant near its end: exp = the grant's expiresAt, not iat + 300 s")
    void should_capExpiry_when_grantEndsSooner() throws Exception {
        Grant grant = requesterGrant(REQUESTER);
        jdbc.update("UPDATE agent_grants SET expires_at = now() + interval '90 seconds' WHERE run_id = ?",
                grant.runId());
        Instant grantEnd = jdbc.queryForObject("SELECT expires_at FROM agent_grants WHERE run_id = ?",
                java.sql.Timestamp.class, grant.runId()).toInstant();

        Map<String, Object> answer = minted(grant);

        Jwt jwt = jwtDecoder.decode((String) answer.get("token"));
        assertThat(jwt.getExpiresAt()).isEqualTo(grantEnd.truncatedTo(ChronoUnit.SECONDS));
        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt()))
                .isLessThan(AgentTokenValidator.MAX_LIFETIME);
        assertThat(Instant.parse((String) answer.get("expiresAt"))).isEqualTo(jwt.getExpiresAt());
    }

    @Test
    @DisplayName("same header and claim names as TestTokens.agentToken, which every service's tests use")
    void should_mintSameClaimShape_as_TestTokens() throws Exception {
        SignedJWT minted = SignedJWT.parse((String) minted(requesterGrant(REQUESTER)).get("token"));
        SignedJWT fixture = SignedJWT.parse(TestTokens.agentToken(REQUESTER_SUB, REQUESTER, "run-x", "requester"));

        assertThat(minted.getHeader().toJSONObject()).isEqualTo(fixture.getHeader().toJSONObject());
        assertThat(minted.getJWTClaimsSet().getClaims().keySet())
                .isEqualTo(fixture.getJWTClaimsSet().getClaims().keySet());
        assertThat(((Map<?, ?>) minted.getJWTClaimsSet().getClaim("act")).keySet())
                .isEqualTo(((Map<?, ?>) fixture.getJWTClaimsSet().getClaim("act")).keySet());
        Set<String> principalKinds = Arrays.stream(AgentPrincipalKind.values())
                .map(kind -> kind.name().toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        assertThat(((Map<?, ?>) minted.getJWTClaimsSet().getClaim("act")).get("principal")).isIn(principalKinds);
    }

    // ------------------------------------------------------------------ caller secret

    @Test
    @DisplayName("caller secret missing: 401 CALLER_SECRET_INVALID")
    void should_refuse_when_callerSecretMissing() throws Exception {
        Grant grant = requesterGrant(REQUESTER);
        refused(mintRequest(null, body(grant)), 401, "CALLER_SECRET_INVALID");
        minted(grant); // positive control
    }

    @Test
    @DisplayName("caller secret wrong: 401 CALLER_SECRET_INVALID")
    void should_refuse_when_callerSecretWrong() throws Exception {
        Grant grant = requesterGrant(REQUESTER);
        refused(mintRequest("not-the-secret", body(grant)), 401, "CALLER_SECRET_INVALID");
        minted(grant);
    }

    @Test
    @DisplayName("EMS's caller secret: 401 CALLER_SECRET_INVALID, EMS never mints")
    void should_refuse_when_emsCallerSecret() throws Exception {
        Grant grant = requesterGrant(REQUESTER);
        refused(mintRequest(EMS_SECRET, body(grant)), 401, "CALLER_SECRET_INVALID");
        minted(grant);
    }

    @Test
    @DisplayName("order: a bad caller secret answers 401 before a bad body")
    void should_refuseCallerFirst_when_callerAndBodyBad() throws Exception {
        refused(mintRequest("not-the-secret", "{\"unexpected\": true}"), 401, "CALLER_SECRET_INVALID");
    }

    // ------------------------------------------------------------------ body

    @ParameterizedTest
    @ValueSource(strings = {
        "{\"grantSecret\": \"s\"}",
        "{\"runId\": \"r\"}",
        "{\"runId\": \"\", \"grantSecret\": \"s\"}",
        "{\"runId\": \"r\", \"grantSecret\": \"  \"}",
        "{\"runId\": \"r\", \"grantSecret\": \"s\", \"username\": \"hongan.agenttoken\"}",
        "{\"runId\": \"0123456789012345678901234567890123456\", \"grantSecret\": \"s\"}",
        "{}"
    })
    @DisplayName("bad body: 400 INVALID_AGENT_REQUEST")
    void should_refuse_when_bodyInvalid(String body) throws Exception {
        refused(mintRequest(AGENT_SERVICE_SECRET, body), 400, "INVALID_AGENT_REQUEST");
    }

    @Test
    @DisplayName("grantSecret longer than 100: 400 INVALID_AGENT_REQUEST")
    void should_refuse_when_grantSecretTooLong() throws Exception {
        refused(mintRequest(AGENT_SERVICE_SECRET, body("r", "s".repeat(101))), 400, "INVALID_AGENT_REQUEST");
    }

    @Test
    @DisplayName("no body: 400 INVALID_AGENT_REQUEST")
    void should_refuse_when_noBody() throws Exception {
        refused(post(TOKENS).header(HEADER, AGENT_SERVICE_SECRET).contentType(MediaType.APPLICATION_JSON),
                400, "INVALID_AGENT_REQUEST");
    }

    // ------------------------------------------------------------------ switch

    @Test
    @DisplayName("grant issued while on, switch then off: 403 AGENT_SWITCHED_OFF")
    void should_refuse_when_switchedOffAfterGrant() throws Exception {
        Grant grant = requesterGrant(REQUESTER);
        minted(grant); // positive control

        switchOff();

        refused(mintRequest(AGENT_SERVICE_SECRET, body(grant)), 403, "AGENT_SWITCHED_OFF");
    }

    @Test
    @DisplayName("order: switch off answers before an unknown run (a probe learns nothing)")
    void should_refuseSwitchFirst_when_switchedOffAndRunUnknown() throws Exception {
        switchOff();
        refused(mintRequest(AGENT_SERVICE_SECRET, body("unknown-run", "whatever")), 403, "AGENT_SWITCHED_OFF");
    }

    // ------------------------------------------------------------------ grant

    @Test
    @DisplayName("unknown run: 403 GRANT_INVALID")
    void should_refuse_when_runUnknown() throws Exception {
        Grant grant = requesterGrant(REQUESTER);
        refused(mintRequest(AGENT_SERVICE_SECRET, body("3f2b8c1e-5a47-4d2e-9b61-0c7f4e8a9d15", grant.secret())),
                403, "GRANT_INVALID");
        minted(grant);
    }

    @Test
    @DisplayName("wrong grant secret: 403 GRANT_INVALID")
    void should_refuse_when_grantSecretWrong() throws Exception {
        Grant grant = requesterGrant(REQUESTER);
        refused(mintRequest(AGENT_SERVICE_SECRET, body(grant.runId(), "wrong-secret")), 403, "GRANT_INVALID");
        minted(grant);
    }

    @Test
    @DisplayName("expired grant: 403 GRANT_INVALID")
    void should_refuse_when_grantExpired() throws Exception {
        Grant grant = requesterGrant(REQUESTER);
        minted(grant); // positive control

        jdbc.update("UPDATE agent_grants SET issued_at = now() - interval '2 hours', "
                + "expires_at = now() - interval '1 hour' WHERE run_id = ?", grant.runId());

        refused(mintRequest(AGENT_SERVICE_SECRET, body(grant)), 403, "GRANT_INVALID");
    }

    @Test
    @DisplayName("order: an invalid grant answers before a principal no longer listed")
    void should_refuseGrantFirst_when_grantAndPrincipalBad() throws Exception {
        Grant grant = requesterGrant(REQUESTER);
        jdbc.update("DELETE FROM agent_allowlist WHERE username = ?", REQUESTER);
        refused(mintRequest(AGENT_SERVICE_SECRET, body(grant.runId(), "wrong-secret")), 403, "GRANT_INVALID");
    }

    // ------------------------------------------------------------------ principal

    @Test
    @DisplayName("removed from the allowlist after the grant: 403 PRINCIPAL_NOT_ELIGIBLE")
    void should_refuse_when_removedFromAllowlist() throws Exception {
        Grant grant = requesterGrant(REQUESTER);
        minted(grant); // positive control

        jdbc.update("DELETE FROM agent_allowlist WHERE username = ?", REQUESTER);

        refused(mintRequest(AGENT_SERVICE_SECRET, body(grant)), 403, "PRINCIPAL_NOT_ELIGIBLE");
    }

    @Test
    @DisplayName("lost ORGANIZER after the grant: 403 PRINCIPAL_NOT_ELIGIBLE")
    void should_refuse_when_lostOrganizer() throws Exception {
        Grant grant = requesterGrant(REQUESTER);
        minted(grant); // positive control

        jdbc.update("DELETE FROM role_assignments WHERE user_id = "
                + "(SELECT id FROM user_profiles WHERE username = ?)", REQUESTER);

        refused(mintRequest(AGENT_SERVICE_SECRET, body(grant)), 403, "PRINCIPAL_NOT_ELIGIBLE");
    }

    @Test
    @DisplayName("user without cognito_user_id: 403 PRINCIPAL_NOT_ELIGIBLE (no sub to sign)")
    void should_refuse_when_principalHasNoCognitoLogin() throws Exception {
        Grant grant = requesterGrant(NO_LOGIN); // the grant itself passes: allowlisted organizer

        refused(mintRequest(AGENT_SERVICE_SECRET, body(grant)), 403, "PRINCIPAL_NOT_ELIGIBLE");

        jdbc.update("UPDATE user_profiles SET cognito_user_id = ? WHERE username = ?",
                "a0000000-0000-4000-8000-0000000006a3", NO_LOGIN);
        assertThat(jwtDecoder.decode((String) minted(grant).get("token")).getSubject())
                .isEqualTo("a0000000-0000-4000-8000-0000000006a3"); // positive control
    }
}
