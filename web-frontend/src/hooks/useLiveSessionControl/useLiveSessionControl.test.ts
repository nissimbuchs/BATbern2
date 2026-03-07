/**
 * useLiveSessionControl Hook Tests
 *
 * Coverage for Story 10.x live control hook:
 * - deriveWebSocketBaseUrl (localhost vs non-localhost)
 * - deriveStatus / enrichSessions / sortSessions / mergeSessions
 * - REST initial fetch (success, error, event not found)
 * - WebSocket lifecycle callbacks (onConnect, onStompError, onWebSocketClose, etc.)
 * - sendExtend / sendDelay (connected + queued paths)
 * - Computed derived state (activeSession, nextSession, remainingSeconds, elapsedSeconds)
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, act, waitFor } from '@testing-library/react';
import { useLiveSessionControl } from './useLiveSessionControl';

// ─── Module mocks ────────────────────────────────────────────────────────────

// Mutable so individual tests can override for non-localhost URL testing
let mockApiBaseUrl = 'http://localhost:8000';

vi.mock('@/contexts/useConfig', () => ({
  useConfig: () => ({ apiBaseUrl: mockApiBaseUrl }),
}));

vi.mock('aws-amplify/auth', () => ({
  fetchAuthSession: vi.fn(),
}));

vi.mock('@/services/api/apiClient', () => ({
  default: { get: vi.fn() },
}));

vi.mock('sockjs-client', () => ({
  default: vi.fn(function () {
    return {};
  }),
}));

// ─── STOMP Client mock ───────────────────────────────────────────────────────
// We use a "spy container" object so the factory closure always captures the
// same reference regardless of when vi.mock() hoisting runs.
const stomp = {
  onConnect: null as (() => void) | null,
  onStompError: null as (() => void) | null,
  onWebSocketClose: null as (() => void) | null,
  onWebSocketError: null as (() => void) | null,
  onDisconnect: null as (() => void) | null,
  webSocketFactory: null as (() => unknown) | null,
  subscribeCallback: null as ((msg: { body: string }) => void) | null,
  client: null as {
    activate: ReturnType<typeof vi.fn>;
    deactivate: ReturnType<typeof vi.fn>;
    publish: ReturnType<typeof vi.fn>;
    subscribe: ReturnType<typeof vi.fn>;
    connected: boolean;
  } | null,
};

vi.mock('@stomp/stompjs', () => ({
  Client: vi.fn().mockImplementation(function (options: {
    webSocketFactory: () => unknown;
    onConnect: () => void;
    onStompError: () => void;
    onWebSocketClose: () => void;
    onWebSocketError: () => void;
    onDisconnect: () => void;
  }) {
    stomp.onConnect = options.onConnect;
    stomp.onStompError = options.onStompError;
    stomp.onWebSocketClose = options.onWebSocketClose;
    stomp.onWebSocketError = options.onWebSocketError;
    stomp.onDisconnect = options.onDisconnect;
    stomp.webSocketFactory = options.webSocketFactory;

    const client = {
      activate: vi.fn(),
      deactivate: vi.fn(() => Promise.resolve()),
      publish: vi.fn(),
      subscribe: vi.fn().mockImplementation(function (
        _topic: string,
        cb: (msg: { body: string }) => void
      ) {
        stomp.subscribeCallback = cb;
      }),
      connected: false,
    };
    stomp.client = client;
    return client;
  }),
}));

// ─── Imports after mocks ─────────────────────────────────────────────────────

import { Client } from '@stomp/stompjs';
import { fetchAuthSession } from 'aws-amplify/auth';
import SockJS from 'sockjs-client';
import apiClient from '@/services/api/apiClient';

const MockClient = vi.mocked(Client);
const mockFetchAuthSession = vi.mocked(fetchAuthSession);
const MockSockJS = vi.mocked(SockJS);
const mockGet = vi.mocked(apiClient.get);

// ─── Helpers ─────────────────────────────────────────────────────────────────

const NOW = Date.now();

const makeSession = (overrides: Record<string, unknown> = {}) => ({
  sessionSlug: 'talk-1',
  title: 'Cloud Talk',
  scheduledStartTime: new Date(NOW - 5 * 60 * 1000).toISOString(), // 5 min ago → ACTIVE
  scheduledEndTime: new Date(NOW + 55 * 60 * 1000).toISOString(), // 55 min ahead
  status: 'SCHEDULED' as const,
  actualEndTime: null,
  actualStartTime: null,
  ...overrides,
});

const makeEventResponse = (sessions: ReturnType<typeof makeSession>[] = [makeSession()]) => ({
  data: { activeEvents: [{ eventCode: 'BAT142', sessions }] },
});

// ─── Tests ───────────────────────────────────────────────────────────────────

describe('useLiveSessionControl', () => {
  beforeEach(() => {
    // Reset spy container
    stomp.onConnect = null;
    stomp.onStompError = null;
    stomp.onWebSocketClose = null;
    stomp.onWebSocketError = null;
    stomp.onDisconnect = null;
    stomp.webSocketFactory = null;
    stomp.subscribeCallback = null;
    stomp.client = null;
    mockApiBaseUrl = 'http://localhost:8000';

    // Re-establish implementations after any clearing
    mockFetchAuthSession.mockResolvedValue({
      tokens: { idToken: { toString: () => 'mock-jwt-token' } },
    } as Awaited<ReturnType<typeof fetchAuthSession>>);

    mockGet.mockResolvedValue({ data: { activeEvents: [] } });

    // Re-set Client implementation in case it was cleared
    MockClient.mockImplementation(function (options: any) {
      stomp.onConnect = options.onConnect!;
      stomp.onStompError = options.onStompError!;
      stomp.onWebSocketClose = options.onWebSocketClose!;
      stomp.onWebSocketError = options.onWebSocketError!;
      stomp.onDisconnect = options.onDisconnect!;
      stomp.webSocketFactory = options.webSocketFactory as () => unknown;

      const client = {
        activate: vi.fn(),
        deactivate: vi.fn(() => Promise.resolve()),
        publish: vi.fn(),
        subscribe: vi.fn().mockImplementation(function (
          _topic: string,
          cb: (msg: { body: string }) => void
        ) {
          stomp.subscribeCallback = cb;
        }),
        connected: false,
      };
      stomp.client = client;
      return client as unknown as InstanceType<typeof Client>;
    });

    MockSockJS.mockClear();
    vi.clearAllMocks();

    // Re-set after clearAllMocks wipes everything
    mockFetchAuthSession.mockResolvedValue({
      tokens: { idToken: { toString: () => 'mock-jwt-token' } },
    } as Awaited<ReturnType<typeof fetchAuthSession>>);
    mockGet.mockResolvedValue({ data: { activeEvents: [] } });

    MockClient.mockImplementation(function (options: any) {
      stomp.onConnect = options.onConnect!;
      stomp.onStompError = options.onStompError!;
      stomp.onWebSocketClose = options.onWebSocketClose!;
      stomp.onWebSocketError = options.onWebSocketError!;
      stomp.onDisconnect = options.onDisconnect!;
      stomp.webSocketFactory = options.webSocketFactory as () => unknown;

      const client = {
        activate: vi.fn(),
        deactivate: vi.fn(() => Promise.resolve()),
        publish: vi.fn(),
        subscribe: vi.fn().mockImplementation(function (
          _topic: string,
          cb: (msg: { body: string }) => void
        ) {
          stomp.subscribeCallback = cb;
        }),
        connected: false,
      };
      stomp.client = client;
      return client as unknown as InstanceType<typeof Client>;
    });
  });

  // ── Initial state ───────────────────────────────────────────────────────────

  it('should return initial state when eventCode is undefined', () => {
    const { result } = renderHook(() => useLiveSessionControl(undefined));

    expect(result.current.sessions).toEqual([]);
    expect(result.current.activeSession).toBeNull();
    expect(result.current.nextSession).toBeNull();
    expect(result.current.connectionStatus).toBe('connecting');
    expect(result.current.isActionInFlight).toBe(false);
    expect(result.current.isLoadingInitial).toBe(true);
    expect(result.current.shouldShowExtend).toBe(false);
    expect(result.current.shouldShowDelay).toBe(false);
    expect(result.current.remainingSeconds).toBe(0);
    expect(result.current.elapsedSeconds).toBe(0);
  });

  it('should not make REST call when eventCode is undefined', () => {
    renderHook(() => useLiveSessionControl(undefined));
    expect(mockGet).not.toHaveBeenCalled();
  });

  // ── REST fetch ──────────────────────────────────────────────────────────────

  it('should call REST endpoint with eventCode provided', async () => {
    const { result } = renderHook(() => useLiveSessionControl('BAT142'));

    expect(result.current.isLoadingInitial).toBe(true);

    await waitFor(() => expect(result.current.isLoadingInitial).toBe(false));

    expect(mockGet).toHaveBeenCalledWith('/watch/organizers/me/active-events');
  });

  it('should populate sessions from REST response', async () => {
    mockGet.mockResolvedValue(makeEventResponse());

    const { result } = renderHook(() => useLiveSessionControl('BAT142'));

    await waitFor(() => expect(result.current.sessions).toHaveLength(1));

    expect(result.current.sessions[0].sessionSlug).toBe('talk-1');
  });

  it('should leave sessions empty when event not found in REST response', async () => {
    mockGet.mockResolvedValue({
      data: { activeEvents: [{ eventCode: 'OTHER-EVENT', sessions: [makeSession()] }] },
    });

    const { result } = renderHook(() => useLiveSessionControl('BAT142'));

    await waitFor(() => expect(result.current.isLoadingInitial).toBe(false));

    expect(result.current.sessions).toEqual([]);
  });

  it('should handle REST fetch failure gracefully', async () => {
    mockGet.mockRejectedValue(new Error('Network error'));

    const { result } = renderHook(() => useLiveSessionControl('BAT142'));

    await waitFor(() => expect(result.current.isLoadingInitial).toBe(false));

    expect(result.current.sessions).toEqual([]);
    expect(result.current.activeSession).toBeNull();
  });

  // ── deriveStatus / enrichSessions ───────────────────────────────────────────

  it('should derive ACTIVE status for sessions spanning current time', async () => {
    mockGet.mockResolvedValue(makeEventResponse());

    const { result } = renderHook(() => useLiveSessionControl('BAT142'));

    await waitFor(() => expect(result.current.activeSession).not.toBeNull());

    expect(result.current.activeSession!.status).toBe('ACTIVE');
    expect(result.current.shouldShowExtend).toBe(true);
    expect(result.current.shouldShowDelay).toBe(true); // elapsed < 10 min
  });

  it('should derive COMPLETED status when actualEndTime is set', async () => {
    mockGet.mockResolvedValue(
      makeEventResponse([makeSession({ actualEndTime: new Date(NOW - 1000).toISOString() })])
    );

    const { result } = renderHook(() => useLiveSessionControl('BAT142'));

    await waitFor(() => expect(result.current.isLoadingInitial).toBe(false));

    expect(result.current.sessions[0].status).toBe('COMPLETED');
    expect(result.current.activeSession).toBeNull();
  });

  it('should derive COMPLETED status when session end time has passed', async () => {
    mockGet.mockResolvedValue(
      makeEventResponse([
        makeSession({
          scheduledStartTime: new Date(NOW - 2 * 60 * 60 * 1000).toISOString(),
          scheduledEndTime: new Date(NOW - 60 * 60 * 1000).toISOString(),
        }),
      ])
    );

    const { result } = renderHook(() => useLiveSessionControl('BAT142'));

    await waitFor(() => expect(result.current.isLoadingInitial).toBe(false));

    expect(result.current.sessions[0].status).toBe('COMPLETED');
  });

  it('should derive SCHEDULED status for future sessions', async () => {
    const futureSession = makeSession({
      sessionSlug: 'future-talk',
      scheduledStartTime: new Date(NOW + 60 * 60 * 1000).toISOString(),
      scheduledEndTime: new Date(NOW + 2 * 60 * 60 * 1000).toISOString(),
    });
    mockGet.mockResolvedValue(makeEventResponse([futureSession]));

    const { result } = renderHook(() => useLiveSessionControl('BAT142'));

    await waitFor(() => expect(result.current.isLoadingInitial).toBe(false));

    expect(result.current.sessions[0].status).toBe('SCHEDULED');
    expect(result.current.nextSession!.sessionSlug).toBe('future-talk');
    expect(result.current.activeSession).toBeNull();
  });

  it('should set shouldShowDelay to false when elapsed > 10 minutes', async () => {
    mockGet.mockResolvedValue(
      makeEventResponse([
        makeSession({ scheduledStartTime: new Date(NOW - 11 * 60 * 1000).toISOString() }),
      ])
    );

    const { result } = renderHook(() => useLiveSessionControl('BAT142'));

    await waitFor(() => expect(result.current.activeSession).not.toBeNull());

    expect(result.current.shouldShowDelay).toBe(false);
  });

  it('should compute remainingSeconds for active session', async () => {
    mockGet.mockResolvedValue(
      makeEventResponse([
        makeSession({ scheduledEndTime: new Date(NOW + 30 * 60 * 1000).toISOString() }),
      ])
    );

    const { result } = renderHook(() => useLiveSessionControl('BAT142'));

    await waitFor(() => expect(result.current.activeSession).not.toBeNull());

    expect(result.current.remainingSeconds).toBeGreaterThan(1790);
    expect(result.current.remainingSeconds).toBeLessThanOrEqual(1800);
  });

  it('should compute elapsedSeconds using actualStartTime when available', async () => {
    mockGet.mockResolvedValue(
      makeEventResponse([
        makeSession({ actualStartTime: new Date(NOW - 15 * 60 * 1000).toISOString() }),
      ])
    );

    const { result } = renderHook(() => useLiveSessionControl('BAT142'));

    await waitFor(() => expect(result.current.activeSession).not.toBeNull());

    expect(result.current.elapsedSeconds).toBeGreaterThan(890);
    expect(result.current.elapsedSeconds).toBeLessThanOrEqual(900);
  });

  // ── sortSessions ────────────────────────────────────────────────────────────

  it('should sort sessions by scheduledStartTime ascending', async () => {
    const sessions = [
      makeSession({
        sessionSlug: 'later',
        scheduledStartTime: new Date(NOW + 2 * 3600_000).toISOString(),
        scheduledEndTime: new Date(NOW + 3 * 3600_000).toISOString(),
      }),
      makeSession({
        sessionSlug: 'sooner',
        scheduledStartTime: new Date(NOW + 1 * 3600_000).toISOString(),
        scheduledEndTime: new Date(NOW + 2 * 3600_000).toISOString(),
      }),
    ];
    mockGet.mockResolvedValue(makeEventResponse(sessions));

    const { result } = renderHook(() => useLiveSessionControl('BAT142'));

    await waitFor(() => expect(result.current.sessions).toHaveLength(2));

    expect(result.current.sessions[0].sessionSlug).toBe('sooner');
    expect(result.current.sessions[1].sessionSlug).toBe('later');
  });

  // ── WebSocket URL derivation ────────────────────────────────────────────────

  it('should use port+2 for WebSocket URL on localhost', async () => {
    renderHook(() => useLiveSessionControl('BAT142'));

    await waitFor(() => expect(stomp.webSocketFactory).not.toBeNull(), { timeout: 3000 });

    stomp.webSocketFactory!();

    expect(MockSockJS).toHaveBeenCalledWith('http://localhost:8002/ws');
  });

  it('should use same-origin URL for WebSocket on non-localhost', async () => {
    mockApiBaseUrl = 'https://api.batbern.ch';

    renderHook(() => useLiveSessionControl('BAT142'));

    await waitFor(() => expect(stomp.webSocketFactory).not.toBeNull(), { timeout: 3000 });

    stomp.webSocketFactory!();

    expect(MockSockJS).toHaveBeenCalledWith('https://api.batbern.ch/ws');
  });

  // ── WebSocket client lifecycle ──────────────────────────────────────────────

  it('should activate WebSocket client after auth', async () => {
    renderHook(() => useLiveSessionControl('BAT142'));

    await waitFor(() => expect(stomp.client).not.toBeNull(), { timeout: 3000 });

    expect(stomp.client!.activate).toHaveBeenCalled();
  });

  it('should set connectionStatus to connected on onConnect', async () => {
    const { result } = renderHook(() => useLiveSessionControl('BAT142'));

    await waitFor(() => expect(stomp.onConnect).not.toBeNull(), { timeout: 3000 });

    act(() => stomp.onConnect!());

    expect(result.current.connectionStatus).toBe('connected');
  });

  it('should publish join message on WebSocket connect', async () => {
    renderHook(() => useLiveSessionControl('BAT142'));

    await waitFor(() => expect(stomp.onConnect).not.toBeNull(), { timeout: 3000 });

    act(() => stomp.onConnect!());

    expect(stomp.client!.publish).toHaveBeenCalledWith(
      expect.objectContaining({ destination: '/app/watch/events/BAT142/join' })
    );
  });

  it('should subscribe to state topic on WebSocket connect', async () => {
    renderHook(() => useLiveSessionControl('BAT142'));

    await waitFor(() => expect(stomp.onConnect).not.toBeNull(), { timeout: 3000 });

    act(() => stomp.onConnect!());

    expect(stomp.client!.subscribe).toHaveBeenCalledWith(
      '/topic/events/BAT142/state',
      expect.any(Function)
    );
  });

  it('should set connectionStatus to offline on STOMP error', async () => {
    const { result } = renderHook(() => useLiveSessionControl('BAT142'));

    await waitFor(() => expect(stomp.onStompError).not.toBeNull(), { timeout: 3000 });

    act(() => stomp.onStompError!());

    expect(result.current.connectionStatus).toBe('offline');
  });

  it('should transition to reconnecting on WebSocket close when previously connected', async () => {
    const { result } = renderHook(() => useLiveSessionControl('BAT142'));

    await waitFor(() => expect(stomp.onConnect).not.toBeNull(), { timeout: 3000 });

    act(() => stomp.onConnect!());
    expect(result.current.connectionStatus).toBe('connected');

    act(() => stomp.onWebSocketClose!());
    expect(result.current.connectionStatus).toBe('reconnecting');
  });

  it('should not change status on WebSocket close when already offline', async () => {
    const { result } = renderHook(() => useLiveSessionControl('BAT142'));

    await waitFor(() => expect(stomp.onStompError).not.toBeNull(), { timeout: 3000 });

    act(() => stomp.onStompError!()); // → offline
    act(() => stomp.onWebSocketClose!()); // close while offline → stays offline

    expect(result.current.connectionStatus).toBe('offline');
  });

  it('should set connectionStatus to reconnecting on WebSocket error', async () => {
    const { result } = renderHook(() => useLiveSessionControl('BAT142'));

    await waitFor(() => expect(stomp.onWebSocketError).not.toBeNull(), { timeout: 3000 });

    act(() => stomp.onWebSocketError!());

    expect(result.current.connectionStatus).toBe('reconnecting');
  });

  it('should set connectionStatus to reconnecting on STOMP disconnect', async () => {
    const { result } = renderHook(() => useLiveSessionControl('BAT142'));

    await waitFor(() => expect(stomp.onDisconnect).not.toBeNull(), { timeout: 3000 });

    act(() => stomp.onDisconnect!());

    expect(result.current.connectionStatus).toBe('reconnecting');
  });

  // ── STATE_UPDATE message handling ───────────────────────────────────────────

  it('should update sessions from WebSocket STATE_UPDATE', async () => {
    const { result } = renderHook(() => useLiveSessionControl('BAT142'));

    await waitFor(() => expect(stomp.onConnect).not.toBeNull(), { timeout: 3000 });
    act(() => stomp.onConnect!());
    await waitFor(() => expect(stomp.subscribeCallback).not.toBeNull(), { timeout: 3000 });

    act(() => {
      stomp.subscribeCallback!({
        body: JSON.stringify({ type: 'ORGANIZER_JOINED', sessions: [makeSession()] }),
      });
    });

    expect(result.current.sessions.some((s) => s.sessionSlug === 'talk-1')).toBe(true);
    expect(result.current.isActionInFlight).toBe(false);
  });

  it('should apply newScheduledEndTime from cascade STATE_UPDATE', async () => {
    mockGet.mockResolvedValue(makeEventResponse());

    const { result } = renderHook(() => useLiveSessionControl('BAT142'));

    await waitFor(() => expect(stomp.onConnect).not.toBeNull(), { timeout: 3000 });
    act(() => stomp.onConnect!());
    await waitFor(() => expect(stomp.subscribeCallback).not.toBeNull(), { timeout: 3000 });

    const newEnd = new Date(NOW + 90 * 60 * 1000).toISOString();

    act(() => {
      stomp.subscribeCallback!({
        body: JSON.stringify({
          type: 'STATE_UPDATE',
          trigger: 'EXTEND_SESSION',
          sessions: [{ sessionSlug: 'talk-1', newScheduledEndTime: newEnd }],
        }),
      });
    });

    const updated = result.current.sessions.find((s) => s.sessionSlug === 'talk-1');
    expect(updated?.scheduledEndTime).toBe(newEnd);
  });

  it('should add new sessions from full ORGANIZER_JOINED snapshot', async () => {
    mockGet.mockResolvedValue(makeEventResponse([makeSession({ sessionSlug: 'talk-1' })]));

    const { result } = renderHook(() => useLiveSessionControl('BAT142'));

    await waitFor(() => expect(result.current.sessions).toHaveLength(1));
    await waitFor(() => expect(stomp.onConnect).not.toBeNull(), { timeout: 3000 });
    act(() => stomp.onConnect!());
    await waitFor(() => expect(stomp.subscribeCallback).not.toBeNull(), { timeout: 3000 });

    const talk2 = makeSession({
      sessionSlug: 'talk-2',
      scheduledStartTime: new Date(NOW + 60 * 60 * 1000).toISOString(),
      scheduledEndTime: new Date(NOW + 2 * 60 * 60 * 1000).toISOString(),
    });

    act(() => {
      stomp.subscribeCallback!({
        body: JSON.stringify({
          type: 'ORGANIZER_JOINED',
          sessions: [makeSession({ sessionSlug: 'talk-1' }), talk2],
        }),
      });
    });

    expect(result.current.sessions).toHaveLength(2);
  });

  it('should ignore malformed WebSocket messages without throwing', async () => {
    renderHook(() => useLiveSessionControl('BAT142'));

    await waitFor(() => expect(stomp.onConnect).not.toBeNull(), { timeout: 3000 });
    act(() => stomp.onConnect!());
    await waitFor(() => expect(stomp.subscribeCallback).not.toBeNull(), { timeout: 3000 });

    expect(() => {
      act(() => stomp.subscribeCallback!({ body: 'not-valid-json{{' }));
    }).not.toThrow();
  });

  it('should skip STATE_UPDATE with empty sessions array', async () => {
    mockGet.mockResolvedValue(makeEventResponse());

    const { result } = renderHook(() => useLiveSessionControl('BAT142'));

    await waitFor(() => expect(result.current.sessions).toHaveLength(1));
    await waitFor(() => expect(stomp.onConnect).not.toBeNull(), { timeout: 3000 });
    act(() => stomp.onConnect!());
    await waitFor(() => expect(stomp.subscribeCallback).not.toBeNull(), { timeout: 3000 });

    act(() => {
      stomp.subscribeCallback!({
        body: JSON.stringify({ type: 'HEARTBEAT', sessions: [] }),
      });
    });

    expect(result.current.sessions).toHaveLength(1);
  });

  // ── sendExtend / sendDelay ──────────────────────────────────────────────────

  it('should publish EXTEND_SESSION when client is connected', async () => {
    mockGet.mockResolvedValue(makeEventResponse());

    const { result } = renderHook(() => useLiveSessionControl('BAT142'));

    await waitFor(() => expect(result.current.activeSession).not.toBeNull());
    await waitFor(() => expect(stomp.onConnect).not.toBeNull(), { timeout: 3000 });
    stomp.client!.connected = true;
    act(() => stomp.onConnect!());

    act(() => result.current.sendExtend(5));

    const actionCall = stomp.client!.publish.mock.calls.find((c) =>
      (c[0] as { body?: string })?.body?.includes('EXTEND_SESSION')
    );
    expect(actionCall).toBeDefined();
    expect(JSON.parse((actionCall![0] as { body: string }).body)).toMatchObject({
      type: 'EXTEND_SESSION',
      sessionSlug: 'talk-1',
      minutes: 5,
    });
  });

  it('should not publish sendExtend when no active session', async () => {
    mockGet.mockResolvedValue({ data: { activeEvents: [] } });

    const { result } = renderHook(() => useLiveSessionControl('BAT142'));

    await waitFor(() => expect(result.current.isLoadingInitial).toBe(false));
    await waitFor(() => expect(stomp.client).not.toBeNull(), { timeout: 3000 });

    stomp.client!.connected = true;
    act(() => result.current.sendExtend(5));

    const actionCalls = stomp.client!.publish.mock.calls.filter((c) =>
      (c[0] as { destination?: string })?.destination?.includes('/action')
    );
    expect(actionCalls).toHaveLength(0);
  });

  it('should publish DELAY_TO_PREVIOUS and set isActionInFlight', async () => {
    mockGet.mockResolvedValue(makeEventResponse());

    const { result } = renderHook(() => useLiveSessionControl('BAT142'));

    await waitFor(() => expect(result.current.activeSession).not.toBeNull());
    await waitFor(() => expect(stomp.onConnect).not.toBeNull(), { timeout: 3000 });
    stomp.client!.connected = true;
    act(() => stomp.onConnect!());

    act(() => result.current.sendDelay(5));

    expect(result.current.isActionInFlight).toBe(true);
    const actionCall = stomp.client!.publish.mock.calls.find((c) =>
      (c[0] as { body?: string })?.body?.includes('DELAY_TO_PREVIOUS')
    );
    expect(actionCall).toBeDefined();
  });

  it('should not publish sendDelay when no active session', async () => {
    mockGet.mockResolvedValue({ data: { activeEvents: [] } });

    const { result } = renderHook(() => useLiveSessionControl('BAT142'));

    await waitFor(() => expect(result.current.isLoadingInitial).toBe(false));
    await waitFor(() => expect(stomp.client).not.toBeNull(), { timeout: 3000 });

    stomp.client!.connected = true;
    act(() => result.current.sendDelay(5));

    expect(result.current.isActionInFlight).toBe(false);
  });

  // ── publishOrQueue (disconnected path) ─────────────────────────────────────

  it('should queue action and trigger activate when client is disconnected', async () => {
    mockGet.mockResolvedValue(makeEventResponse());

    const { result } = renderHook(() => useLiveSessionControl('BAT142'));

    await waitFor(() => expect(result.current.activeSession).not.toBeNull());
    await waitFor(() => expect(stomp.client).not.toBeNull(), { timeout: 3000 });

    stomp.client!.connected = false;
    act(() => result.current.sendDelay(3));

    expect(result.current.connectionStatus).toBe('reconnecting');
    expect(result.current.isActionInFlight).toBe(true);
    expect(stomp.client!.activate).toHaveBeenCalled();
  });

  it('should flush pending action on next reconnect', async () => {
    mockGet.mockResolvedValue(makeEventResponse());

    const { result } = renderHook(() => useLiveSessionControl('BAT142'));

    await waitFor(() => expect(result.current.activeSession).not.toBeNull());
    await waitFor(() => expect(stomp.onConnect).not.toBeNull(), { timeout: 3000 });

    // Queue while disconnected
    stomp.client!.connected = false;
    act(() => result.current.sendExtend(10));

    // Reconnect — pending action flushed in onConnect
    stomp.client!.connected = true;
    act(() => stomp.onConnect!());

    const actionCalls = stomp.client!.publish.mock.calls.filter((c) =>
      (c[0] as { body?: string })?.body?.includes('EXTEND_SESSION')
    );
    expect(actionCalls.length).toBeGreaterThan(0);
  });

  // ── Cleanup ─────────────────────────────────────────────────────────────────

  it('should deactivate client on unmount', async () => {
    const { unmount } = renderHook(() => useLiveSessionControl('BAT142'));

    await waitFor(() => expect(stomp.client).not.toBeNull(), { timeout: 3000 });

    unmount();

    expect(stomp.client!.deactivate).toHaveBeenCalled();
  });

  it('should publish leave message on unmount when connected', async () => {
    const { unmount } = renderHook(() => useLiveSessionControl('BAT142'));

    await waitFor(() => expect(stomp.onConnect).not.toBeNull(), { timeout: 3000 });

    stomp.client!.connected = true;
    act(() => stomp.onConnect!());

    unmount();

    const leaveCalls = stomp.client!.publish.mock.calls.filter((c) =>
      (c[0] as { destination?: string })?.destination?.includes('/leave')
    );
    expect(leaveCalls.length).toBeGreaterThan(0);
  });
});
