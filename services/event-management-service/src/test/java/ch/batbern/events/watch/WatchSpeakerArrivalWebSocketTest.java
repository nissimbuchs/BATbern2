package ch.batbern.events.watch;

import ch.batbern.events.AbstractIntegrationTest;
import ch.batbern.events.config.TestAwsConfig;
import ch.batbern.events.config.TestSecurityConfig;
import ch.batbern.events.watch.dto.SpeakerArrivalBroadcast;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

import java.lang.reflect.Type;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * STOMP broadcast integration test for speaker arrival tracking.
 * Task 12.7: Verifies that POST /arrivals triggers a real STOMP broadcast
 * received by subscribed Watch clients.
 *
 * Uses RANDOM_PORT so WebSocketStompClient can connect to the actual server.
 * MockMvc shares the same ApplicationContext → same in-memory STOMP broker.
 * @WithMockUser propagates through MockMvc dispatch even in RANDOM_PORT mode.
 *
 * W2.4: FR38 — broadcast to all organizer watches within 3 seconds.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import({TestSecurityConfig.class, TestAwsConfig.class})
class WatchSpeakerArrivalWebSocketTest extends AbstractIntegrationTest {

    private static final String EVENT_CODE = "BATbernWS99";
    private static final String SPEAKER_USERNAME = "anna.meier";
    private static final String ORGANIZER_USERNAME = "marco.muster";

    @LocalServerPort
    private int port;

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

    @Test
    @WithMockUser(username = ORGANIZER_USERNAME, roles = {"ORGANIZER"})
    @DisplayName("shouldBroadcastToArrivalsTopicViaWebSocket_whenArrivalConfirmedViaREST")
    void shouldBroadcastToArrivalsTopicViaWebSocket_whenArrivalConfirmedViaREST()
            throws Exception {

        CompletableFuture<SpeakerArrivalBroadcast> broadcastFuture = new CompletableFuture<>();

        WebSocketStompClient stompClient = new WebSocketStompClient(
                new SockJsClient(List.of(new WebSocketTransport(new StandardWebSocketClient())))
        );
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());

        String wsUrl = "ws://localhost:" + port + "/ws";
        StompSession stompSession = stompClient
                .connectAsync(wsUrl, new WebSocketHttpHeaders(), new StompSessionHandlerAdapter() {})
                .get(5, TimeUnit.SECONDS);

        stompSession.subscribe(
                "/topic/events/" + EVENT_CODE + "/arrivals",
                new StompFrameHandler() {
                    @Override
                    public Type getPayloadType(StompHeaders headers) {
                        return SpeakerArrivalBroadcast.class;
                    }

                    @Override
                    public void handleFrame(StompHeaders headers, Object payload) {
                        broadcastFuture.complete((SpeakerArrivalBroadcast) payload);
                    }
                }
        );

        // Allow STOMP subscription to register with the in-memory broker
        Thread.sleep(200);

        // Trigger broadcast via REST — MockMvc shares the same ApplicationContext
        // and therefore the same in-memory STOMP broker as the STOMP client above.
        String requestBody = objectMapper.writeValueAsString(
                new java.util.HashMap<String, String>() {{
                    put("speakerUsername", SPEAKER_USERNAME);
                }}
        );
        mockMvc.perform(post("/api/v1/watch/events/{eventCode}/arrivals", EVENT_CODE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated());

        // Assert broadcast received within 3 seconds (FR38)
        SpeakerArrivalBroadcast broadcast = broadcastFuture.get(3, TimeUnit.SECONDS);
        assertThat(broadcast.type()).isEqualTo("SPEAKER_ARRIVED");
        assertThat(broadcast.speakerUsername()).isEqualTo(SPEAKER_USERNAME);
        assertThat(broadcast.confirmedBy()).isEqualTo(ORGANIZER_USERNAME);
        assertThat(broadcast.arrivalCount().arrived()).isEqualTo(1);
        assertThat(broadcast.eventCode()).isEqualTo(EVENT_CODE);

        stompSession.disconnect();
        stompClient.stop();
    }
}
