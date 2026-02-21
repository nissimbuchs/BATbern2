package ch.batbern.events.watch;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * STOMP channel interceptor that validates JWT on CONNECT frames.
 * W4.1 Task 7: Authenticates Watch organizer clients connecting to the raw WebSocket endpoint.
 *
 * On CONNECT: extract Authorization header, validate JWT via existing JwtDecoder bean,
 * then set Principal on the STOMP session for use in @MessageMapping handlers.
 * Rejects invalid/expired tokens by returning null (suppresses the message).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtStompInterceptor implements ChannelInterceptor {

    private final JwtDecoder jwtDecoder;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() != StompCommand.CONNECT) {
            return message;
        }

        String authHeader = accessor.getFirstNativeHeader("Authorization");
        if (authHeader == null) {
            // No Authorization header — non-Watch STOMP client (e.g. web frontend via SockJS).
            // Pass through; @MessageMapping handlers check Principal as needed.
            return message;
        }
        if (!authHeader.startsWith("Bearer ")) {
            log.warn("STOMP CONNECT rejected: malformed Authorization header");
            return null;
        }

        String token = authHeader.substring(7);
        try {
            Jwt jwt = jwtDecoder.decode(token);
            String username = jwt.getSubject();
            UsernamePasswordAuthenticationToken principal =
                    new UsernamePasswordAuthenticationToken(username, null, List.of());
            principal.setDetails(jwt); // expose full claims for @MessageMapping handlers
            accessor.setUser(principal);
            log.debug("STOMP CONNECT accepted for user: {}", username);
        } catch (JwtException e) {
            log.warn("STOMP CONNECT rejected: JWT validation failed — {}", e.getMessage());
            return null;
        }

        return message;
    }
}
