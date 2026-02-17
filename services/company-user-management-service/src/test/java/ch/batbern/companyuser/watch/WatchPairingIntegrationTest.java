package ch.batbern.companyuser.watch;

import ch.batbern.companyuser.config.TestAwsConfig;
import ch.batbern.companyuser.domain.Role;
import ch.batbern.companyuser.domain.User;
import ch.batbern.companyuser.domain.WatchPairing;
import ch.batbern.companyuser.repository.UserRepository;
import ch.batbern.companyuser.watch.repository.WatchPairingRepository;
import ch.batbern.shared.test.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for WatchPairingController
 * Story W2.1: Pairing Code Backend & Web Frontend
 * AC: 1 (generate code), 2 (max watches), 3 (expiry), 4 (unpair)
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(TestAwsConfig.class)
@DisplayName("WatchPairing Integration Tests")
class WatchPairingIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WatchPairingRepository watchPairingRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private User testOrganizer;

    @BeforeEach
    void setUp() {
        watchPairingRepository.deleteAll();
        userRepository.deleteAll();

        testOrganizer = User.builder()
                .username("john.doe")
                .email("john.doe@example.com")
                .firstName("John")
                .lastName("Doe")
                .cognitoUserId("john.doe")
                .companyId("GoogleZH")
                .roles(new HashSet<>(Set.of(Role.ORGANIZER)))
                .build();
        testOrganizer = userRepository.save(testOrganizer);
    }

    // --- AC1: Generate Pairing Code ---

    @Test
    @WithMockUser(username = "john.doe", roles = {"ORGANIZER"})
    @DisplayName("shouldGeneratePairingCode_whenValidOrganizer")
    void shouldGeneratePairingCode_whenValidOrganizer() throws Exception {
        mockMvc.perform(post("/api/v1/users/{username}/watch-pairing", "john.doe")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.pairingCode").value(matchesPattern("\\d{6}")))
                .andExpect(jsonPath("$.expiresAt").value(notNullValue()))
                .andExpect(jsonPath("$.hoursUntilExpiry").value(24));
    }

    @Test
    @WithMockUser(username = "john.doe", roles = {"ORGANIZER"})
    @DisplayName("shouldReturn403_whenGeneratingCodeForOtherUser")
    void shouldReturn403_whenGeneratingCodeForOtherUser() throws Exception {
        // Other user in DB
        userRepository.save(User.builder()
                .username("jane.doe")
                .email("jane.doe@example.com")
                .firstName("Jane")
                .lastName("Doe")
                .cognitoUserId("jane.doe")
                .companyId("GoogleZH")
                .roles(new HashSet<>(Set.of(Role.ORGANIZER)))
                .build());

        mockMvc.perform(post("/api/v1/users/{username}/watch-pairing", "jane.doe")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "john.doe", roles = {"ATTENDEE"})
    @DisplayName("shouldReturn403_whenNonOrganizerGeneratesCode")
    void shouldReturn403_whenNonOrganizerGeneratesCode() throws Exception {
        mockMvc.perform(post("/api/v1/users/{username}/watch-pairing", "john.doe")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    // --- AC2: Max 2 watches enforcement ---

    @Test
    @WithMockUser(username = "john.doe", roles = {"ORGANIZER"})
    @DisplayName("shouldReturn409_whenMaxWatchesAlreadyPaired")
    void shouldReturn409_whenMaxWatchesAlreadyPaired() throws Exception {
        // Pre-condition: 2 already-paired watches
        createPairedWatch("watch-1", LocalDateTime.now().minusDays(1));
        createPairedWatch("watch-2", LocalDateTime.now().minusHours(2));

        mockMvc.perform(post("/api/v1/users/{username}/watch-pairing", "john.doe")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Maximum 2 watches paired. Unpair a device first."));
    }

    // --- GET pairing status ---

    @Test
    @WithMockUser(username = "john.doe", roles = {"ORGANIZER"})
    @DisplayName("shouldReturnPairingStatus_withPairedWatchAndPendingCode")
    void shouldReturnPairingStatus_withPairedWatchAndPendingCode() throws Exception {
        // Pre-condition: 1 paired watch + 1 pending code
        createPairedWatch("my-watch", LocalDateTime.now().minusDays(5));
        createPendingCode("123456", LocalDateTime.now().plusHours(20));

        mockMvc.perform(get("/api/v1/users/{username}/watch-pairing", "john.doe")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pairedWatches").isArray())
                .andExpect(jsonPath("$.pairedWatches[0].deviceName").value("my-watch"))
                .andExpect(jsonPath("$.pairedWatches[0].pairedAt").value(notNullValue()))
                .andExpect(jsonPath("$.pendingCode.code").value("123456"))
                .andExpect(jsonPath("$.pendingCode.expiresAt").value(notNullValue()));
    }

    @Test
    @WithMockUser(username = "john.doe", roles = {"ORGANIZER"})
    @DisplayName("shouldReturnEmptyStatus_whenNoPairings")
    void shouldReturnEmptyStatus_whenNoPairings() throws Exception {
        mockMvc.perform(get("/api/v1/users/{username}/watch-pairing", "john.doe")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pairedWatches").isArray())
                .andExpect(jsonPath("$.pairedWatches").isEmpty())
                .andExpect(jsonPath("$.pendingCode").isEmpty());
    }

    // --- AC4: Unpair watch ---

    @Test
    @WithMockUser(username = "john.doe", roles = {"ORGANIZER"})
    @DisplayName("shouldUnpairWatch_whenCalled")
    void shouldUnpairWatch_whenCalled() throws Exception {
        createPairedWatch("my-watch", LocalDateTime.now().minusDays(1));

        mockMvc.perform(delete("/api/v1/users/{username}/watch-pairing/{deviceName}", "john.doe", "my-watch")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNoContent());

        // Verify removed
        mockMvc.perform(get("/api/v1/users/{username}/watch-pairing", "john.doe")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pairedWatches").isEmpty());
    }

    @Test
    @WithMockUser(username = "john.doe", roles = {"ORGANIZER"})
    @DisplayName("shouldReturn404_whenUnpairingNonexistentDevice")
    void shouldReturn404_whenUnpairingNonexistentDevice() throws Exception {
        mockMvc.perform(delete("/api/v1/users/{username}/watch-pairing/{deviceName}", "john.doe", "nonexistent")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "other.user", roles = {"ORGANIZER"})
    @DisplayName("shouldReturn403_whenUnpairingAnotherUsersWatch")
    void shouldReturn403_whenUnpairingAnotherUsersWatch() throws Exception {
        createPairedWatch("my-watch", LocalDateTime.now().minusDays(1));

        mockMvc.perform(delete("/api/v1/users/{username}/watch-pairing/{deviceName}", "john.doe", "my-watch")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    // --- AC3: Expired code ---

    @Test
    @WithMockUser(username = "john.doe", roles = {"ORGANIZER"})
    @DisplayName("shouldNotReturnExpiredCode_inStatus")
    void shouldNotReturnExpiredCode_inStatus() throws Exception {
        // Create expired pending code
        createPendingCode("999999", LocalDateTime.now().minusHours(1));

        mockMvc.perform(get("/api/v1/users/{username}/watch-pairing", "john.doe")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingCode").isEmpty());
    }

    // --- H3: No orphan pending-code accumulation ---

    @Test
    @WithMockUser(username = "john.doe", roles = {"ORGANIZER"})
    @DisplayName("shouldReplaceExistingPendingCode_onNewGeneration")
    void shouldReplaceExistingPendingCode_onNewGeneration() throws Exception {
        createPendingCode("111111", LocalDateTime.now().plusHours(10));

        mockMvc.perform(post("/api/v1/users/{username}/watch-pairing", "john.doe")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated());

        // Only one pending code row should remain
        long pendingCount = watchPairingRepository
                .findByUsername("john.doe").stream()
                .filter(p -> p.getPairingCode() != null)
                .count();
        assert pendingCount == 1 : "Expected 1 pending code row, got " + pendingCount;
    }

    // --- Unauthorized access ---

    @Test
    @DisplayName("shouldReturn401_whenNotAuthenticated")
    void shouldReturn401_whenNotAuthenticated() throws Exception {
        mockMvc.perform(post("/api/v1/users/{username}/watch-pairing", "john.doe")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    // --- W2.2: Pairing code exchange and JWT authentication ---

    @Test
    @DisplayName("shouldExchangeCodeForPairingToken_whenValidCode")
    void shouldExchangeCodeForPairingToken_whenValidCode() throws Exception {
        // Pre-condition: valid, non-expired pairing code
        createPendingCode("482715", LocalDateTime.now().plusHours(20));

        mockMvc.perform(post("/api/v1/watch/pair")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pairingCode\": \"482715\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pairingToken").value(notNullValue()))
                .andExpect(jsonPath("$.organizerUsername").value("john.doe"))
                .andExpect(jsonPath("$.organizerFirstName").value("John"));

        // L2 fix: Use JUnit/Hamcrest assertions instead of Java assert (disabled without -ea flag)
        // Verify database: pairingCode cleared, pairingToken set, pairedAt set
        WatchPairing updated = watchPairingRepository.findByUsername("john.doe")
                .stream()
                .filter(p -> p.getPairingToken() != null)
                .findFirst()
                .orElseThrow();

        assertThat("Pairing code should be cleared after exchange", updated.getPairingCode(), is(nullValue()));
        assertThat("pairedAt should be set after successful pairing", updated.getPairedAt(), is(notNullValue()));
        assertThat("pairingToken should be set", updated.getPairingToken(), is(notNullValue()));
    }

    @Test
    @DisplayName("shouldRejectPairing_whenCodeExpired")
    void shouldRejectPairing_whenCodeExpired() throws Exception {
        // Pre-condition: expired pairing code
        createPendingCode("111111", LocalDateTime.now().minusHours(1));

        mockMvc.perform(post("/api/v1/watch/pair")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pairingCode\": \"111111\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("shouldRejectPairing_whenCodeInvalid")
    void shouldRejectPairing_whenCodeInvalid() throws Exception {
        mockMvc.perform(post("/api/v1/watch/pair")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pairingCode\": \"999999\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("shouldAuthenticateWithPairingToken_whenTokenValid")
    void shouldAuthenticateWithPairingToken_whenTokenValid() throws Exception {
        // Pre-condition: complete pairing to get a pairing token
        createPairedWatch("test-watch", LocalDateTime.now().minusMinutes(5));
        String token = "token-test-watch";

        // H2 fix: capture response to verify JWT claims — task 9.5 spec requires claim validation
        MvcResult result = mockMvc.perform(post("/api/v1/watch/authenticate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pairingToken\": \"" + token + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jwt").value(notNullValue()))
                .andExpect(jsonPath("$.expiresAt").value(notNullValue()))
                .andReturn();

        // Verify JWT contains correct claims: subject=username, role=ORGANIZER (task 9.5)
        @SuppressWarnings("unchecked")
        Map<String, Object> body = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        String jwtString = (String) body.get("jwt");

        SignedJWT signedJWT = SignedJWT.parse(jwtString);
        JWTClaimsSet claims = signedJWT.getJWTClaimsSet();

        assertThat("JWT subject should be organizer username", claims.getSubject(), is("john.doe"));
        assertThat("JWT role claim should be ORGANIZER", claims.getStringClaim("role"), is("ORGANIZER"));
        assertThat("JWT issuer should be batbern-watch", claims.getIssuer(), is("batbern-watch"));
    }

    @Test
    @DisplayName("shouldRejectAuth_whenTokenInvalid")
    void shouldRejectAuth_whenTokenInvalid() throws Exception {
        mockMvc.perform(post("/api/v1/watch/authenticate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pairingToken\": \"nonexistent-token\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("shouldRejectPairing_whenCodeFormatInvalid")
    void shouldRejectPairing_whenCodeFormatInvalid() throws Exception {
        // Only 5 digits — fails bean validation
        mockMvc.perform(post("/api/v1/watch/pair")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pairingCode\": \"12345\"}"))
                .andExpect(status().isBadRequest());
    }

    // --- Helper methods ---

    private WatchPairing createPairedWatch(String deviceName, LocalDateTime pairedAt) {
        WatchPairing pairing = new WatchPairing();
        pairing.setUsername("john.doe");
        pairing.setDeviceName(deviceName);
        pairing.setPairedAt(pairedAt);
        pairing.setPairingToken("token-" + deviceName);
        return watchPairingRepository.save(pairing);
    }

    private WatchPairing createPendingCode(String code, LocalDateTime expiresAt) {
        WatchPairing pairing = new WatchPairing();
        pairing.setUsername("john.doe");
        pairing.setPairingCode(code);
        pairing.setPairingCodeExpiresAt(expiresAt);
        return watchPairingRepository.save(pairing);
    }
}
