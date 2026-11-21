package ch.batbern.events.security;

import ch.batbern.events.config.SecurityConfig;
import ch.batbern.shared.test.security.TestTokens;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Unit tests for JwtStompInterceptor.
 * W4.1 Task 7.5: Verifies valid JWT passes, invalid/expired JWT rejected.
 */
@ExtendWith(MockitoExtension.class)
class JwtStompInterceptorTest {

    @Mock
    private JwtDecoder jwtDecoder;

    @Mock
    private MessageChannel channel;

    private JwtStompInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new JwtStompInterceptor(jwtDecoder);
    }

    @Test
    @DisplayName("should_allowConnect_when_validJwtProvided")
    void should_allowConnect_when_validJwtProvided() {
        Jwt jwt = Jwt.withTokenValue("valid-token")
                .header("alg", "HS256")
                .claim("sub", "marco.organizer")
                .claim("role", "ORGANIZER")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        when(jwtDecoder.decode(anyString())).thenReturn(jwt);

        Message<?> message = buildStompConnectMessage("Bearer valid-token");

        Message<?> result = interceptor.preSend(message, channel);

        assertThat(result).isNotNull();
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(result);
        assertThat(accessor.getUser()).isNotNull();
        assertThat(accessor.getUser().getName()).isEqualTo("marco.organizer");
    }

    @Test
    @DisplayName("should_rejectConnect_when_invalidJwtProvided")
    void should_rejectConnect_when_invalidJwtProvided() {
        when(jwtDecoder.decode(anyString())).thenThrow(new JwtException("Invalid token"));

        Message<?> message = buildStompConnectMessage("Bearer invalid-token");

        Message<?> result = interceptor.preSend(message, channel);

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("should_passThrough_when_noAuthorizationHeader")
    void should_passThrough_when_noAuthorizationHeader() {
        // Non-Watch STOMP clients (e.g. web frontend via SockJS) connect without auth header.
        // The interceptor must pass them through to preserve existing SockJS behavior.
        Message<?> message = buildStompConnectMessageWithoutAuth();

        Message<?> result = interceptor.preSend(message, channel);

        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("should_rejectConnect_when_malformedAuthorizationHeader")
    void should_rejectConnect_when_malformedAuthorizationHeader() {
        // Bearer prefix missing → reject
        Message<?> message = buildStompConnectMessage("Basic sometoken");

        Message<?> result = interceptor.preSend(message, channel);

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("should_passThrough_when_notConnectFrame")
    void should_passThrough_when_notConnectFrame() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setDestination("/app/watch/events/BATbern56/join");
        Message<?> message = MessageBuilder
                .createMessage(new byte[0], accessor.getMessageHeaders());

        assertThatNoException().isThrownBy(() -> interceptor.preSend(message, channel));
    }

    @Test
    @DisplayName("should_acceptAgentConnect_when_realSharedDecoderVerifiesAgentKey")
    void should_acceptAgentConnect_when_realSharedDecoderVerifiesAgentKey() {
        // Agentic interaction 0.1: EMS's real decoder, not the mock; agent kind on via the test key.
        JwtStompInterceptor real = new JwtStompInterceptor(realSharedDecoder());
        String token = TestTokens.agentToken("agent-sub-1", "agent.planner", "run-1", "marco.organizer");

        Message<?> result = real.preSend(buildStompConnectMessage("Bearer " + token), channel);

        assertThat(result).isNotNull();
        assertThat(StompHeaderAccessor.wrap(result).getUser().getName()).isEqualTo("agent-sub-1");
    }

    @Test
    @DisplayName("should_rejectAgentConnect_when_signedWithOtherKey")
    void should_rejectAgentConnect_when_signedWithOtherKey() {
        JwtStompInterceptor real = new JwtStompInterceptor(realSharedDecoder());
        String token = TestTokens.agent("agent-sub-1", "agent.planner", "run-1", "marco.organizer")
                .signedWith(TestTokens.newRsaKeyPair()).build();

        assertThat(real.preSend(buildStompConnectMessage("Bearer " + token), channel)).isNull();
    }

    /** The decoder bean EMS's {@link SecurityConfig} builds, with the test agent key configured. */
    private static JwtDecoder realSharedDecoder() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                        "https://jwks.example.invalid/keys")
                .withProperty("agent.jwt.public-key", TestTokens.agentPublicKeyPem());
        return new SecurityConfig().jwtDecoder(environment);
    }

    // MARK: - Helpers

    private Message<?> buildStompConnectMessage(String authHeader) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.addNativeHeader("Authorization", authHeader);
        accessor.setLeaveMutable(true); // keep mutable so preSend can setUser
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private Message<?> buildStompConnectMessageWithoutAuth() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
