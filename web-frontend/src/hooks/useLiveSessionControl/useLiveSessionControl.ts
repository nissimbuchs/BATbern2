/**
 * useLiveSessionControl Hook
 *
 * Real-time session control for live events via WebSocket STOMP.
 * Mirrors the Watch app's session control functionality (W4.3) for the web.
 *
 * - Fetches initial session state via REST (GET /watch/organizers/me/active-events)
 * - Connects to event-management-service WebSocket for real-time updates
 * - Exposes extend/delay actions identical to Watch app (EXTEND_SESSION, DELAY_TO_PREVIOUS)
 * - Button visibility logic mirrors Watch: extend in last 10 min, delay in first 10 min
 *
 * Auth: Cognito organizer JWT (different from Watch pairing JWT)
 * WebSocket: Direct connection to event-management-service (bypasses API Gateway)
 */

import { useState, useEffect, useRef, useCallback } from 'react';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { fetchAuthSession } from 'aws-amplify/auth';
import apiClient from '@/services/api/apiClient';
import { useConfig } from '@/contexts/useConfig';
import type { components } from '@/types/generated/events-api.types';

type WatchSessionDetail = components['schemas']['WatchSessionDetail'];
type ActiveEventsResponse = components['schemas']['ActiveEventsResponse'];

export type ConnectionStatus = 'connecting' | 'connected' | 'reconnecting' | 'offline';

/** Session object as it arrives in a cascade STATE_UPDATE (DELAY/EXTEND). */
interface CascadeSession extends Partial<WatchSessionDetail> {
  sessionSlug: string;
  /** Replaces scheduledEndTime after an extend or delay cascade. */
  newScheduledEndTime?: string | null;
  /** Replaces scheduledStartTime after a delay cascade. */
  newScheduledStartTime?: string | null;
}

interface WatchStateUpdate {
  type: string;
  trigger?: string;
  sessions?: CascadeSession[];
}

interface WatchActionPayload {
  type: 'EXTEND_SESSION' | 'DELAY_TO_PREVIOUS';
  sessionSlug: string;
  minutes: number;
}

export interface LiveSessionControlState {
  sessions: WatchSessionDetail[];
  activeSession: WatchSessionDetail | null;
  nextSession: WatchSessionDetail | null;
  remainingSeconds: number;
  elapsedSeconds: number;
  shouldShowExtend: boolean;
  shouldShowDelay: boolean;
  connectionStatus: ConnectionStatus;
  sendExtend: (minutes: number) => void;
  sendDelay: (minutes: number) => void;
  isActionInFlight: boolean;
  isLoadingInitial: boolean;
}

/**
 * Derive the WebSocket base URL from the runtime apiBaseUrl.
 * Mirrors BATbernAPIConfig.webSocketBaseURL in the Watch app.
 *
 * localhost:  port+2 to bypass API Gateway (cannot proxy WS upgrades)
 * staging/production: same origin as REST API — ALB handles WS upgrade natively
 */
function deriveWebSocketBaseUrl(apiBaseUrl: string): string {
  const url = new URL(apiBaseUrl);
  if (url.hostname === 'localhost' || url.hostname === '127.0.0.1') {
    const apiPort = parseInt(url.port || '8000', 10);
    return `http://localhost:${apiPort + 2}`;
  }
  return `${url.protocol}//${url.host}`;
}

/**
 * Derive session status from scheduled times for the web view.
 *
 * The Watch app uses an explicit state machine: sessions only become ACTIVE when
 * the organizer manually advances them (END_SESSION action). STATE_UPDATE broadcasts
 * therefore carry status:'SCHEDULED' for sessions that are live by the clock.
 *
 * The web live-control page uses a schedule-based view instead:
 * - COMPLETED is respected from the server (explicit organizer end / actualEndTime)
 * - ACTIVE / SCHEDULED are always derived from the current time vs scheduled times
 *   so the page correctly reflects what is happening right now regardless of watch state
 */
function deriveStatus(
  session: WatchSessionDetail,
  now: number
): 'SCHEDULED' | 'ACTIVE' | 'COMPLETED' {
  if (session.actualEndTime || session.status === 'COMPLETED') return 'COMPLETED';

  const start = session.scheduledStartTime ? new Date(session.scheduledStartTime).getTime() : null;
  const end = session.scheduledEndTime ? new Date(session.scheduledEndTime).getTime() : null;

  if (start !== null && end !== null) {
    if (now >= start && now < end) return 'ACTIVE';
    if (now >= end) return 'COMPLETED';
  }
  return 'SCHEDULED';
}

function enrichSessions(sessions: WatchSessionDetail[], now: number): WatchSessionDetail[] {
  return sessions.map((s) => ({ ...s, status: deriveStatus(s, now) }));
}

function sortSessions(sessions: WatchSessionDetail[]): WatchSessionDetail[] {
  return [...sessions].sort((a, b) =>
    (a.scheduledStartTime ?? '').localeCompare(b.scheduledStartTime ?? '')
  );
}

/**
 * Merge an incoming STATE_UPDATE sessions array into the existing list.
 *
 * Cascade updates (DELAY/EXTEND) carry only the affected sessions and use
 * newScheduledEndTime / newScheduledStartTime instead of the standard fields.
 * Full snapshots (ORGANIZER_JOINED) carry all sessions with standard fields.
 *
 * Strategy: update existing sessions by slug, add any new ones, keep the rest.
 */
function mergeSessions(
  prev: WatchSessionDetail[],
  incoming: CascadeSession[]
): WatchSessionDetail[] {
  const updateMap = new Map(incoming.map((s) => [s.sessionSlug, s]));

  const merged = prev.map((existing): WatchSessionDetail => {
    const delta = updateMap.get(existing.sessionSlug);
    if (!delta) return existing;
    return {
      ...existing,
      ...delta,
      // Cascade deltas carry new* fields; apply them to the canonical fields
      scheduledEndTime:
        delta.newScheduledEndTime ?? delta.scheduledEndTime ?? existing.scheduledEndTime,
      scheduledStartTime:
        delta.newScheduledStartTime ?? delta.scheduledStartTime ?? existing.scheduledStartTime,
      actualEndTime: delta.actualEndTime ?? existing.actualEndTime,
      actualStartTime: delta.actualStartTime ?? existing.actualStartTime,
    } as WatchSessionDetail;
  });

  // Add sessions from the update not yet in our list (full-snapshot case)
  const existingSlugs = new Set(prev.map((s) => s.sessionSlug));
  const added = incoming.filter(
    (s): s is WatchSessionDetail =>
      !existingSlugs.has(s.sessionSlug) && s.scheduledStartTime != null
  );

  return sortSessions([...merged, ...added].filter((s) => s.scheduledStartTime != null));
}

export function useLiveSessionControl(eventCode: string | undefined): LiveSessionControlState {
  const { apiBaseUrl } = useConfig();
  const wsBaseUrl = deriveWebSocketBaseUrl(apiBaseUrl);
  const [sessions, setSessions] = useState<WatchSessionDetail[]>([]);
  const [connectionStatus, setConnectionStatus] = useState<ConnectionStatus>('connecting');
  const [isActionInFlight, setIsActionInFlight] = useState(false);
  const [isLoadingInitial, setIsLoadingInitial] = useState(true);
  const [currentTime, setCurrentTime] = useState(Date.now());
  const clientRef = useRef<Client | null>(null);
  const eventCodeRef = useRef(eventCode);
  eventCodeRef.current = eventCode;
  // Action queued while disconnected — flushed on next onConnect
  const pendingActionRef = useRef<WatchActionPayload | null>(null);

  // 1-second ticker to drive countdown and re-derive statuses
  useEffect(() => {
    const id = setInterval(() => setCurrentTime(Date.now()), 1000);
    return () => clearInterval(id);
  }, []);

  // Fetch initial session data via REST (fallback before WebSocket delivers state)
  useEffect(() => {
    if (!eventCode) return;

    let cancelled = false;
    setIsLoadingInitial(true);

    apiClient
      .get<ActiveEventsResponse>('/watch/organizers/me/active-events')
      .then((res) => {
        if (cancelled) return;
        const event = res.data.activeEvents.find((e) => e.eventCode === eventCode);
        if (event?.sessions?.length) {
          setSessions(sortSessions(event.sessions.filter((s) => s.scheduledStartTime != null))); // raw — enrichSessions applied at render time
        }
      })
      .catch(() => {
        // REST fetch failed — WebSocket state update will provide session data
      })
      .finally(() => {
        if (!cancelled) setIsLoadingInitial(false);
      });

    return () => {
      cancelled = true;
    };
  }, [eventCode]);

  // WebSocket connection lifecycle
  useEffect(() => {
    if (!eventCode) return;

    let isMounted = true;

    const setupConnection = async () => {
      let token: string | null = null;
      try {
        const session = await fetchAuthSession();
        token = session.tokens?.idToken?.toString() ?? null;
      } catch {
        // Proceed without token — server will reject unauthenticated actions
      }

      if (!isMounted) return;

      const client = new Client({
        webSocketFactory: () => new SockJS(`${wsBaseUrl}/ws`) as WebSocket,
        connectHeaders: token ? { Authorization: `Bearer ${token}` } : {},
        debug: () => {},
        reconnectDelay: 3000,
        heartbeatIncoming: 10000,
        heartbeatOutgoing: 10000,

        onConnect: () => {
          if (!isMounted) return;
          setConnectionStatus('connected');

          // Register organizer presence in the event
          client.publish({
            destination: `/app/watch/events/${eventCode}/join`,
          });

          // Subscribe to real-time state broadcasts
          client.subscribe(`/topic/events/${eventCode}/state`, (message) => {
            if (!isMounted) return;
            try {
              const update: WatchStateUpdate = JSON.parse(message.body);
              if (update.sessions && update.sessions.length > 0) {
                setSessions((prev) => mergeSessions(prev, update.sessions!));
              }
              setIsActionInFlight(false);
            } catch {
              // Ignore malformed messages
            }
          });

          // Flush any action that was queued while the connection was down
          const pending = pendingActionRef.current;
          if (pending) {
            pendingActionRef.current = null;
            client.publish({
              destination: `/app/watch/events/${eventCode}/action`,
              body: JSON.stringify(pending),
            });
          }
        },

        onStompError: () => {
          if (!isMounted) return;
          // STOMP-level error (auth / protocol) — reconnect won't help
          setConnectionStatus('offline');
        },

        onWebSocketClose: () => {
          if (!isMounted) return;
          // Transient drop — client will auto-reconnect via reconnectDelay
          setConnectionStatus((prev) => (prev === 'connected' ? 'reconnecting' : prev));
        },

        onWebSocketError: () => {
          if (!isMounted) return;
          // Network error — client will auto-reconnect
          setConnectionStatus('reconnecting');
        },

        onDisconnect: () => {
          if (!isMounted) return;
          // Server-initiated disconnect — client will auto-reconnect
          setConnectionStatus('reconnecting');
        },
      });

      clientRef.current = client;
      client.activate();
    };

    void setupConnection();

    return () => {
      isMounted = false;
      if (clientRef.current) {
        if (clientRef.current.connected && eventCode) {
          clientRef.current.publish({
            destination: `/app/watch/events/${eventCode}/leave`,
          });
        }
        void clientRef.current.deactivate();
        clientRef.current = null;
      }
    };
  }, [eventCode, wsBaseUrl]);

  // Derived timing state — recomputed every second via currentTime
  // Enrich sessions with client-side derived status (REST returns null status)
  const enriched = enrichSessions(sessions, currentTime);
  const activeSession = enriched.find((s) => s.status === 'ACTIVE') ?? null;
  const nextSession =
    enriched.find((s) => s.status === 'SCHEDULED' && s.scheduledStartTime != null) ?? null;

  let remainingSeconds = 0;
  let elapsedSeconds = 0;

  if (activeSession) {
    if (activeSession.scheduledEndTime) {
      remainingSeconds = Math.max(
        0,
        Math.floor((new Date(activeSession.scheduledEndTime).getTime() - currentTime) / 1000)
      );
    }
    const effectiveStart = activeSession.actualStartTime ?? activeSession.scheduledStartTime;
    if (effectiveStart) {
      elapsedSeconds = Math.max(
        0,
        Math.floor((currentTime - new Date(effectiveStart).getTime()) / 1000)
      );
    }
  }

  // Extend/reduce button: always visible when a session is active
  const shouldShowExtend = activeSession !== null;
  // Delay button: first 10 minutes of session (mirrors Watch W4.3)
  const shouldShowDelay = activeSession !== null && elapsedSeconds < 600;

  /**
   * Publish a STOMP action, or queue it for the next reconnect if currently disconnected.
   * Calls client.activate() to ensure reconnection is in progress.
   */
  const publishOrQueue = useCallback((payload: WatchActionPayload) => {
    const client = clientRef.current;
    if (!client) return;
    if (client.connected) {
      client.publish({
        destination: `/app/watch/events/${eventCodeRef.current}/action`,
        body: JSON.stringify(payload),
      });
    } else {
      // Queue — will be flushed in onConnect after the next successful reconnect
      pendingActionRef.current = payload;
      // Ensure reconnect is in progress (activate() is idempotent)
      client.activate();
      setConnectionStatus('reconnecting');
    }
  }, []);

  const sendExtend = useCallback(
    (minutes: number) => {
      if (!activeSession) return;
      // No isActionInFlight for extend/reduce — user can adjust repeatedly
      publishOrQueue({ type: 'EXTEND_SESSION', sessionSlug: activeSession.sessionSlug, minutes });
    },
    [activeSession, publishOrQueue]
  );

  const sendDelay = useCallback(
    (minutes: number) => {
      if (!activeSession) return;
      setIsActionInFlight(true);
      // Safety reset: re-enable if no STATE_UPDATE arrives within 5 s
      setTimeout(() => setIsActionInFlight(false), 5000);
      publishOrQueue({
        type: 'DELAY_TO_PREVIOUS',
        sessionSlug: activeSession.sessionSlug,
        minutes,
      });
    },
    [activeSession, publishOrQueue]
  );

  return {
    sessions: enriched,
    activeSession,
    nextSession,
    remainingSeconds,
    elapsedSeconds,
    shouldShowExtend,
    shouldShowDelay,
    connectionStatus,
    sendExtend,
    sendDelay,
    isActionInFlight,
    isLoadingInitial,
  };
}
