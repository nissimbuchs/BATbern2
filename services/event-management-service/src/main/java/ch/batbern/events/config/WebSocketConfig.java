package ch.batbern.events.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocket Configuration for Real-Time Push Notifications
 * Story BAT-7: Notifications API Consolidation
 *
 * Enables STOMP over WebSocket for real-time notification delivery.
 * Organizers receive instant updates when domain events occur.
 *
 * Architecture:
 * - STOMP protocol over WebSocket
 * - Simple in-memory message broker (no external message queue needed)
 * - Per-user notification topics: /topic/notifications/{username}
 *
 * Frontend Connection:
 * 1. Connect to /ws endpoint
 * 2. Subscribe to /topic/notifications/{username}
 * 3. Receive NotificationResponse JSON when events occur
 *
 * Security:
 * - CORS configured to allow frontend origin
 * - Authentication via JWT in HTTP headers (future enhancement)
 * - SockJS fallback for browsers without WebSocket support
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    /**
     * Configure message broker.
     * - /topic prefix for pub-sub messaging (one-to-many)
     * - /app prefix for application-bound messages
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Enable simple in-memory broker for /topic destinations
        config.enableSimpleBroker("/topic");

        // Prefix for application-bound messages (e.g., @MessageMapping)
        config.setApplicationDestinationPrefixes("/app");
    }

    /**
     * Register STOMP endpoints.
     * - /ws: Main WebSocket endpoint
     * - withSockJS(): Fallback for browsers without WebSocket support
     * - setAllowedOriginPatterns("*"): Allow connections from frontend
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")  // Allow all origins (dev/prod)
                .withSockJS();  // Enable SockJS fallback
    }
}
