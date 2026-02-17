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

import static org.hamcrest.Matchers.hasLength;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.notNullValue;
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

    // --- Unauthorized access ---

    @Test
    @DisplayName("shouldReturn401_whenNotAuthenticated")
    void shouldReturn401_whenNotAuthenticated() throws Exception {
        mockMvc.perform(post("/api/v1/users/{username}/watch-pairing", "john.doe")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
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
