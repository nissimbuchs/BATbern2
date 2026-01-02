/**
 * Notification WebSocket Client
 *
 * Story BAT-7 - Notifications API Consolidation
 *
 * Real-time WebSocket client for push notifications
 * Features:
 * - STOMP over WebSocket connection
 * - Per-user notification topics
 * - Automatic reconnection
 * - Connection state management
 * - Error handling
 */

import { Client, StompConfig } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import type { Notification } from '@/types/notification';

/**
 * Connection states for the WebSocket client
 */
export enum ConnectionState {
  DISCONNECTED = 'DISCONNECTED',
  CONNECTING = 'CONNECTING',
  CONNECTED = 'CONNECTED',
  RECONNECTING = 'RECONNECTING',
  ERROR = 'ERROR',
}

/**
 * Notification callback type
 */
export type NotificationCallback = (notification: Notification) => void;

/**
 * Connection state callback type
 */
export type ConnectionStateCallback = (state: ConnectionState) => void;

/**
 * Determine WebSocket URL based on current hostname
 *
 * Local Development: Connect directly to event-management-service
 * - Uses VITE_API_PORT (from BASE_PORT) + 2 for dynamic port allocation
 * - Example: BASE_PORT=8000 → API Gateway on 8000, Event Management on 8002
 * - Example: BASE_PORT=9000 → API Gateway on 9000, Event Management on 9002
 * - WebSocket connections bypass API Gateway (doesn't support WebSocket proxying)
 *
 * Staging/Production: Connect to API Gateway which routes to event-management-service
 */
function getWebSocketUrl(): string {
  const hostname = window.location.hostname;

  if (hostname === 'localhost' || hostname === '127.0.0.1') {
    // Local development: Calculate event-management-service port from API Gateway port
    // API Gateway = BASE_PORT, Event Management = BASE_PORT+2
    const apiPort = parseInt(import.meta.env.VITE_API_PORT || '8000', 10);
    const eventMgmtPort = apiPort + 2;
    return `http://localhost:${eventMgmtPort}`;
  }

  if (hostname === 'staging.batbern.ch') {
    return 'https://api.staging.batbern.ch';
  }

  return 'https://api.batbern.ch';
}

/**
 * Notification WebSocket Client Class
 *
 * Manages STOMP-over-WebSocket connection for real-time push notifications
 */
class NotificationWebSocketClient {
  private client: Client | null = null;
  private username: string | null = null;
  private connectionState: ConnectionState = ConnectionState.DISCONNECTED;
  private notificationCallbacks: Set<NotificationCallback> = new Set();
  private stateCallbacks: Set<ConnectionStateCallback> = new Set();
  private subscriptionId: string | null = null;

  /**
   * Connect to WebSocket server for a specific user
   *
   * @param username Username to subscribe to notifications for
   */
  connect(username: string): void {
    if (this.client && this.connectionState === ConnectionState.CONNECTED) {
      console.warn('WebSocket already connected');
      return;
    }

    this.username = username;
    this.updateConnectionState(ConnectionState.CONNECTING);

    // Create STOMP client with SockJS
    const stompConfig: StompConfig = {
      webSocketFactory: () => {
        return new SockJS(`${getWebSocketUrl()}/ws`) as WebSocket;
      },
      debug: (str: string) => {
        if (import.meta.env.DEV) {
          console.log('STOMP Debug:', str);
        }
      },
      reconnectDelay: 5000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      onConnect: () => {
        this.onConnected();
      },
      onStompError: (frame) => {
        this.onError(frame);
      },
      onWebSocketClose: () => {
        this.onDisconnected();
      },
      onWebSocketError: (event) => {
        console.error('WebSocket error:', event);
        this.updateConnectionState(ConnectionState.ERROR);
      },
    };

    this.client = new Client(stompConfig);
    this.client.activate();
  }

  /**
   * Disconnect from WebSocket server
   */
  disconnect(): void {
    if (this.client) {
      this.client.deactivate();
      this.client = null;
      this.subscriptionId = null;
      this.updateConnectionState(ConnectionState.DISCONNECTED);
    }
  }

  /**
   * Subscribe to notification updates
   *
   * @param callback Function to call when a notification is received
   * @returns Unsubscribe function
   */
  onNotification(callback: NotificationCallback): () => void {
    this.notificationCallbacks.add(callback);

    // Return unsubscribe function
    return () => {
      this.notificationCallbacks.delete(callback);
    };
  }

  /**
   * Subscribe to connection state changes
   *
   * @param callback Function to call when connection state changes
   * @returns Unsubscribe function
   */
  onConnectionStateChange(callback: ConnectionStateCallback): () => void {
    this.stateCallbacks.add(callback);
    // Immediately call with current state
    callback(this.connectionState);

    // Return unsubscribe function
    return () => {
      this.stateCallbacks.delete(callback);
    };
  }

  /**
   * Get current connection state
   */
  getConnectionState(): ConnectionState {
    return this.connectionState;
  }

  /**
   * Check if currently connected
   */
  isConnected(): boolean {
    return this.connectionState === ConnectionState.CONNECTED;
  }

  /**
   * Handle successful connection
   */
  private onConnected(): void {
    console.log('WebSocket connected successfully');
    this.updateConnectionState(ConnectionState.CONNECTED);

    if (!this.client || !this.username) {
      return;
    }

    // Subscribe to user's notification topic
    const topic = `/topic/notifications/${this.username}`;
    const subscription = this.client.subscribe(topic, (message) => {
      this.handleNotificationMessage(message.body);
    });

    this.subscriptionId = subscription.id;
    console.log(`Subscribed to notification topic: ${topic}`);
  }

  /**
   * Handle disconnection
   */
  private onDisconnected(): void {
    console.log('WebSocket disconnected');
    this.subscriptionId = null;

    if (this.connectionState === ConnectionState.CONNECTED) {
      // Unexpected disconnection - will auto-reconnect
      this.updateConnectionState(ConnectionState.RECONNECTING);
    } else {
      this.updateConnectionState(ConnectionState.DISCONNECTED);
    }
  }

  /**
   * Handle STOMP errors
   */
  private onError(frame: { headers: Record<string, string>; body: string }): void {
    console.error('STOMP error:', frame);
    this.updateConnectionState(ConnectionState.ERROR);
  }

  /**
   * Handle incoming notification message
   */
  private handleNotificationMessage(messageBody: string): void {
    try {
      const notification: Notification = JSON.parse(messageBody);

      // Notify all subscribers
      this.notificationCallbacks.forEach((callback) => {
        try {
          callback(notification);
        } catch (error) {
          console.error('Error in notification callback:', error);
        }
      });
    } catch (error) {
      console.error('Failed to parse notification message:', error);
    }
  }

  /**
   * Update connection state and notify listeners
   */
  private updateConnectionState(newState: ConnectionState): void {
    if (this.connectionState === newState) {
      return;
    }

    this.connectionState = newState;
    this.stateCallbacks.forEach((callback) => {
      try {
        callback(newState);
      } catch (error) {
        console.error('Error in state change callback:', error);
      }
    });
  }
}

// Export singleton instance
export const notificationWebSocketClient = new NotificationWebSocketClient();
export default notificationWebSocketClient;
