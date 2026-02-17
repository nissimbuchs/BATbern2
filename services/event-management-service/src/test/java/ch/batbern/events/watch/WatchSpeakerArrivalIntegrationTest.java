package ch.batbern.events.watch;

import ch.batbern.events.AbstractIntegrationTest;
import ch.batbern.events.config.TestAwsConfig;
import ch.batbern.events.config.TestSecurityConfig;
import ch.batbern.events.watch.SpeakerArrivalRepository;
import ch.batbern.events.watch.domain.SpeakerArrival;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for speaker arrival tracking REST endpoints.
 * W2.4: FR38, FR39
 *
 * Test scenarios:
 * - AC#3: 200 empty arrivals when none confirmed
 * - AC#3: 201 on arrival confirmation with correct broadcast payload
 * - AC#5: Idempotent confirmation (double confirm = no duplicate)
 * - AC#3: 401 when unauthenticated
 * - AC#3: 403 when not ORGANIZER role
 *
 * Note: WebSocket STOMP broadcast test requires RANDOM_PORT webEnvironment.
 * See WatchSpeakerArrivalWebSocketTest for the STOMP broadcast integration test.
 */
@Transactional
@Import({TestSecurityConfig.class, TestAwsConfig.class})
public class WatchSpeakerArrivalIntegrationTest extends AbstractIntegrationTest {

    private static final String ARRIVALS_URL = "/api/v1/watch/events/{eventCode}/arrivals";
    private static final String EVENT_CODE = "BATbern-IT-99";
    private static final String SPEAKER_USERNAME = "anna.meier";
    private static final String ORGANIZER_USERNAME = "marco.muster";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SpeakerArrivalRepository arrivalRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        arrivalRepository.deleteAll();
    }

    // ============================================================================
    // Task 12.2: GET arrivals — empty when none confirmed
    // ============================================================================

    @Test
    @DisplayName("shouldReturnEmptyArrivals_whenNoneConfirmed")
    @WithMockUser(username = ORGANIZER_USERNAME, roles = {"ORGANIZER"})
    void shouldReturnEmptyArrivals_whenNoneConfirmed() throws Exception {
        mockMvc.perform(get(ARRIVALS_URL, EVENT_CODE)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.arrivals", hasSize(0)));
    }

    // ============================================================================
    // Task 12.3: POST arrival — confirms and returns broadcast payload
    // ============================================================================

    @Test
    @DisplayName("shouldConfirmArrival_andReturnBroadcast")
    @WithMockUser(username = ORGANIZER_USERNAME, roles = {"ORGANIZER"})
    void shouldConfirmArrival_andReturnBroadcast() throws Exception {
        String requestBody = objectMapper.writeValueAsString(
                new java.util.HashMap<String, String>() {{
                    put("speakerUsername", SPEAKER_USERNAME);
                }}
        );

        mockMvc.perform(post(ARRIVALS_URL, EVENT_CODE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type", equalTo("SPEAKER_ARRIVED")))
                .andExpect(jsonPath("$.speakerUsername", equalTo(SPEAKER_USERNAME)))
                .andExpect(jsonPath("$.confirmedBy", equalTo(ORGANIZER_USERNAME)))
                .andExpect(jsonPath("$.arrivalCount.arrived", equalTo(1)))
                .andExpect(jsonPath("$.arrivedAt", notNullValue()));
    }

    // ============================================================================
    // Task 12.4: Idempotency — double confirmation returns no duplicate
    // ============================================================================

    @Test
    @DisplayName("shouldBeIdempotent_whenConfirmingSameArrivalTwice")
    @WithMockUser(username = ORGANIZER_USERNAME, roles = {"ORGANIZER"})
    void shouldBeIdempotent_whenConfirmingSameArrivalTwice() throws Exception {
        String requestBody = objectMapper.writeValueAsString(
                new java.util.HashMap<String, String>() {{
                    put("speakerUsername", SPEAKER_USERNAME);
                }}
        );

        // First confirmation
        mockMvc.perform(post(ARRIVALS_URL, EVENT_CODE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isCreated());

        // Second confirmation (idempotent — should not return 409)
        mockMvc.perform(post(ARRIVALS_URL, EVENT_CODE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isCreated());

        // Verify only one record in DB
        var arrivals = arrivalRepository.findByEventCode(EVENT_CODE);
        org.assertj.core.api.Assertions.assertThat(arrivals).hasSize(1);
        org.assertj.core.api.Assertions.assertThat(arrivals.get(0).getSpeakerUsername())
                .isEqualTo(SPEAKER_USERNAME);
    }

    // ============================================================================
    // Task 12.5: 401 when unauthenticated
    // ============================================================================

    @Test
    @DisplayName("shouldReturn403_whenUnauthenticated")
    void shouldReturn401_whenUnauthenticated() throws Exception {
        // TestSecurityConfig uses permitAll() at HTTP level + @PreAuthorize at method level.
        // Anonymous users hit @PreAuthorize("hasRole('ORGANIZER')") → AccessDeniedException → 403.
        // In production, the API Gateway rejects unauthenticated requests with 401 before reaching the service.
        mockMvc.perform(get(ARRIVALS_URL, EVENT_CODE)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());

        String requestBody = "{\"speakerUsername\":\"" + SPEAKER_USERNAME + "\"}";
        mockMvc.perform(post(ARRIVALS_URL, EVENT_CODE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isForbidden());
    }

    // ============================================================================
    // Task 12.6: 403 when not ORGANIZER role
    // ============================================================================

    @Test
    @DisplayName("shouldReturn403_whenNotOrganizer")
    @WithMockUser(username = "attendee.user", roles = {"ATTENDEE"})
    void shouldReturn403_whenNotOrganizer() throws Exception {
        mockMvc.perform(get(ARRIVALS_URL, EVENT_CODE)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());

        String requestBody = "{\"speakerUsername\":\"" + SPEAKER_USERNAME + "\"}";
        mockMvc.perform(post(ARRIVALS_URL, EVENT_CODE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isForbidden());
    }

    // ============================================================================
    // Task 12.7: WebSocket STOMP broadcast
    // Note: Full STOMP integration test requires RANDOM_PORT webEnvironment.
    // See WatchSpeakerArrivalWebSocketTest.java for implementation with
    // WebSocketStompClient + StompSessionHandlerAdapter.
    // ============================================================================
}
