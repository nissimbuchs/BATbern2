/**
 * useLiveSessionControl Hook
 *
 * Real-time session control for live events via REST polling (Story 15.1).
 * Mirrors the Watch app's session control functionality (W4.3) for the web.
 *
 * - Polls GET /events/{eventCode}/live-timing adaptively (3–5 s while a session is
 *   active, backing off when idle or the tab is hidden), sending If-None-Match so an
 *   unchanged snapshot costs a 304.
 * - Exposes extend/delay actions identical to the Watch app (EXTEND_SESSION,
 *   DELAY_TO_PREVIOUS) via POST .../live-timing/actions.
 * - Button visibility logic mirrors Watch: extend while active, delay in first 10 min.
 *
 * Auth: Cognito organizer JWT (apiClient adds it). The authenticated poll also refreshes
 * server-side organizer presence. Replaces the previous STOMP/WebSocket implementation —
 * no per-task in-memory state, so polls landing on different Fargate tasks are consistent.
 */

import { useState, useEffect, useRef, useCallback } from 'react';
import { liveTimingService } from '@/services/liveTimingService';
import type { components } from '@/types/generated/events-api.types';

type WatchSessionDetail = components['schemas']['WatchSessionDetail'];
type LiveTimingActionRequest = components['schemas']['LiveTimingActionRequest'];

export type ConnectionStatus = 'connecting' | 'connected' | 'reconnecting' | 'offline';

/** Adaptive poll cadence (ms). */
const POLL_INTERVAL_ACTIVE = 4000;
const POLL_INTERVAL_IDLE = 15000;
const POLL_INTERVAL_HIDDEN = 30000;

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
 * Derive session status from scheduled times for the web view.
 *
 * The Watch app uses an explicit state machine: sessions only become ACTIVE when the
 * organizer manually advances them, so the snapshot can carry status:'SCHEDULED' for
 * sessions that are live by the clock. The web live-control page uses a schedule-based
 * view instead:
 * - COMPLETED is respected from the server (explicit organizer end / actualEndTime)
 * - ACTIVE / SCHEDULED are derived from the current time vs scheduled times so the page
 *   reflects what is happening right now regardless of watch state.
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

export function useLiveSessionControl(eventCode: string | undefined): LiveSessionControlState {
  const [sessions, setSessions] = useState<WatchSessionDetail[]>([]);
  const [connectionStatus, setConnectionStatus] = useState<ConnectionStatus>('connecting');
  const [isActionInFlight, setIsActionInFlight] = useState(false);
  const [isLoadingInitial, setIsLoadingInitial] = useState(true);
  const [currentTime, setCurrentTime] = useState(Date.now());

  const etagRef = useRef<string | null>(null);
  // Latest sessions, readable inside the polling loop without re-subscribing.
  const sessionsRef = useRef<WatchSessionDetail[]>([]);
  // Action queued while a POST failed (offline) — retried on the next successful poll.
  const pendingActionRef = useRef<LiveTimingActionRequest | null>(null);
  // Latest dispatch function, so the polling loop can flush a queued action.
  const dispatchActionRef = useRef<
    ((payload: LiveTimingActionRequest) => Promise<void>) | undefined
  >(undefined);

  const applySnapshot = useCallback(
    (next: WatchSessionDetail[] | undefined, etag: string | null) => {
      etagRef.current = etag;
      const sorted = sortSessions((next ?? []).filter((s) => s.scheduledStartTime != null));
      sessionsRef.current = sorted;
      setSessions(sorted);
    },
    []
  );

  // 1-second ticker to drive countdown and re-derive statuses.
  useEffect(() => {
    const id = setInterval(() => setCurrentTime(Date.now()), 1000);
    return () => clearInterval(id);
  }, []);

  // Dispatch a timing action over REST; queue + flag reconnecting if it fails.
  const dispatchAction = useCallback(
    async (payload: LiveTimingActionRequest) => {
      if (!eventCode) return;
      try {
        const result = await liveTimingService.postLiveTimingAction(eventCode, payload);
        applySnapshot(result.data.sessions, result.etag);
        setConnectionStatus('connected');
        setIsActionInFlight(false);
      } catch {
        // Offline / transient — queue for the next successful poll to retry.
        pendingActionRef.current = payload;
        setConnectionStatus('reconnecting');
      }
    },
    [eventCode, applySnapshot]
  );
  dispatchActionRef.current = dispatchAction;

  // Adaptive REST polling loop (replaces the STOMP subscription).
  useEffect(() => {
    if (!eventCode) return;

    let cancelled = false;
    let timeoutId: ReturnType<typeof setTimeout> | null = null;

    const computeInterval = (): number => {
      if (typeof document !== 'undefined' && document.hidden) return POLL_INTERVAL_HIDDEN;
      const now = Date.now();
      const hasActive = sessionsRef.current.some((s) => deriveStatus(s, now) === 'ACTIVE');
      return hasActive ? POLL_INTERVAL_ACTIVE : POLL_INTERVAL_IDLE;
    };

    const poll = async () => {
      if (cancelled) return;
      try {
        const result = await liveTimingService.getLiveTiming(eventCode, etagRef.current);
        if (cancelled) return;
        if (result.status === 200 && result.data) {
          applySnapshot(result.data.sessions, result.etag);
        } else {
          // 304 — unchanged; keep the etag for the next conditional poll.
          etagRef.current = result.etag;
        }
        setConnectionStatus('connected');

        // Flush a queued action now that connectivity is back.
        if (pendingActionRef.current) {
          const queued = pendingActionRef.current;
          pendingActionRef.current = null;
          await dispatchActionRef.current?.(queued);
        }
      } catch {
        if (!cancelled) setConnectionStatus('reconnecting');
      } finally {
        if (!cancelled) {
          setIsLoadingInitial(false);
          timeoutId = setTimeout(poll, computeInterval());
        }
      }
    };

    void poll();

    return () => {
      cancelled = true;
      if (timeoutId) clearTimeout(timeoutId);
    };
  }, [eventCode, applySnapshot]);

  // Derived timing state — recomputed every second via currentTime.
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

  // Extend/reduce button: always visible when a session is active.
  const shouldShowExtend = activeSession !== null;
  // Delay button: first 10 minutes of session (mirrors Watch W4.3).
  const shouldShowDelay = activeSession !== null && elapsedSeconds < 600;

  const sendExtend = useCallback(
    (minutes: number) => {
      if (!activeSession) return;
      // No isActionInFlight for extend/reduce — user can adjust repeatedly.
      void dispatchAction({
        type: 'EXTEND_SESSION',
        sessionSlug: activeSession.sessionSlug,
        minutes,
      });
    },
    [activeSession, dispatchAction]
  );

  const sendDelay = useCallback(
    (minutes: number) => {
      if (!activeSession) return;
      setIsActionInFlight(true);
      // Safety reset: re-enable if the action does not resolve within 5 s.
      setTimeout(() => setIsActionInFlight(false), 5000);
      void dispatchAction({
        type: 'DELAY_TO_PREVIOUS',
        sessionSlug: activeSession.sessionSlug,
        minutes,
      });
    },
    [activeSession, dispatchAction]
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
