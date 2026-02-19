package ch.batbern.events.config;

import ch.batbern.events.watch.JwtStompInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocket Configuration for Real-Time Push Notifications and Watch organizer sync.
 *
 * Story BAT-7: Notifications API Consolidation — SockJS endpoint for web frontend
 * W4.1 Task 6: Raw WebSocket endpoint for Watch clients (no SockJS, raw STOMP)
 * W4.1 Task 7.4: JWT channel interceptor for Watch STOMP authentication
 *
 * Endpoints:
 * - /ws           — SockJS + raw WebSocket (web frontend; existing)
 * - /api/v1/watch/ws — raw WebSocket only (Watch clients; new in W4.1)
 *
 * Authentication:
 * - /ws: permitAll at HTTP level (SockJS requires open handshake, then STOMP-level auth in future)
 * - /api/v1/watch/ws: STOMP CONNECT validated by JwtStompInterceptor
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtStompInterceptor jwtStompInterceptor;

    /**
     * Configure message broker.
     * - /topic prefix for pub-sub messaging (one-to-many)
     * - /queue prefix for point-to-point (user-specific, e.g. state snapshots)
     * - /app prefix for application-bound messages (@MessageMapping)
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic", "/queue");
        config.setApplicationDestinationPrefixes("/app");
        config.setUserDestinationPrefix("/user");
    }

    /**
     * Register STOMP endpoints.
     *
     * /ws — existing SockJS endpoint for web frontend (unchanged)
     * /api/v1/watch/ws — new raw WebSocket endpoint for watchOS clients
     *   Raw WebSocket (no SockJS): watchOS URLSessionWebSocketTask speaks raw WebSocket,
     *   not SockJS. SockJS adds HTTP polling fallback that Watch clients don't need.
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS();

        registry.addEndpoint("/api/v1/watch/ws")
                .setAllowedOriginPatterns("*");
    }

    /**
     * Register JwtStompInterceptor on the inbound channel.
     * Validates JWT on STOMP CONNECT frames from Watch clients.
     * W4.1 Task 7.4.
     */
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(jwtStompInterceptor);
    }
}
