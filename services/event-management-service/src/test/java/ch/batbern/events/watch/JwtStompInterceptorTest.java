package ch.batbern.events.watch;

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
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import java.time.Instant;
import java.util.Map;

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
