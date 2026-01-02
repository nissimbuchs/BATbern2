/**
 * useNotificationWebSocket Hook
 *
 * Story BAT-7 - Notifications API Consolidation
 *
 * React hook for real-time WebSocket notifications
 * Features:
 * - Auto-connect/disconnect based on authentication
 * - Real-time notification updates
 * - Connection state management
 * - Automatic cleanup
 */

import { useEffect, useState, useCallback } from 'react';
import {
  notificationWebSocketClient,
  ConnectionState,
  type NotificationCallback,
} from '@/services/notificationWebSocketClient';
import type { Notification } from '@/types/notification';

/**
 * Hook return type
 */
export interface UseNotificationWebSocketResult {
  /** Current connection state */
  connectionState: ConnectionState;
  /** Whether WebSocket is connected */
  isConnected: boolean;
  /** Connect to WebSocket (if not already connected) */
  connect: () => void;
  /** Disconnect from WebSocket */
  disconnect: () => void;
  /** Subscribe to notifications (returns unsubscribe function) */
  onNotification: (callback: NotificationCallback) => () => void;
}

/**
 * React hook for WebSocket notifications
 *
 * @param username Username to connect for (null/undefined to skip connection)
 * @param options Configuration options
 * @returns WebSocket client interface
 */
export function useNotificationWebSocket(
  username?: string | null,
  options: {
    /** Auto-connect on mount (default: true) */
    autoConnect?: boolean;
    /** Auto-disconnect on unmount (default: true) */
    autoDisconnect?: boolean;
  } = {}
): UseNotificationWebSocketResult {
  const { autoConnect = true, autoDisconnect = true } = options;

  // Track connection state
  const [connectionState, setConnectionState] = useState<ConnectionState>(
    notificationWebSocketClient.getConnectionState()
  );

  // Connect function
  const connect = useCallback(() => {
    if (username) {
      notificationWebSocketClient.connect(username);
    }
  }, [username]);

  // Disconnect function
  const disconnect = useCallback(() => {
    notificationWebSocketClient.disconnect();
  }, []);

  // Subscribe to notifications
  const onNotification = useCallback((callback: NotificationCallback): (() => void) => {
    return notificationWebSocketClient.onNotification(callback);
  }, []);

  // Subscribe to connection state changes
  useEffect(() => {
    const unsubscribe = notificationWebSocketClient.onConnectionStateChange(setConnectionState);
    return unsubscribe;
  }, []);

  // Auto-connect when username changes
  useEffect(() => {
    if (autoConnect && username) {
      connect();
    }
  }, [autoConnect, username, connect]);

  // Auto-disconnect on unmount
  useEffect(() => {
    return () => {
      if (autoDisconnect) {
        disconnect();
      }
    };
  }, [autoDisconnect, disconnect]);

  return {
    connectionState,
    isConnected: connectionState === ConnectionState.CONNECTED,
    connect,
    disconnect,
    onNotification,
  };
}

/**
 * Hook for listening to real-time notifications with callback
 *
 * @param username Username to connect for
 * @param onNotification Callback when notification received
 */
export function useNotificationListener(
  username: string | null | undefined,
  onNotificationReceived?: (notification: Notification) => void
): void {
  const { onNotification } = useNotificationWebSocket(username);

  useEffect(() => {
    if (!onNotificationReceived) {
      return;
    }

    const unsubscribe = onNotification((notification) => {
      onNotificationReceived(notification);
    });

    return unsubscribe;
  }, [onNotification, onNotificationReceived]);
}

export default useNotificationWebSocket;
