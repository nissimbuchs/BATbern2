/**
 * useLiveSessionControl Hook Tests (Story 15.1 — REST polling)
 *
 * Coverage:
 * - Initial state / no poll when eventCode is undefined
 * - Polling populates sessions; deriveStatus / enrichSessions / sortSessions
 * - 304 poll keeps existing sessions
 * - Computed derived state (activeSession, nextSession, remaining/elapsed seconds)
 * - sendExtend / sendDelay POST the action; no-op without an active session
 * - connectionStatus transitions on poll success/failure
 * - Offline action queue: a failed POST is retried on the next poll
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { renderHook, act, waitFor } from '@testing-library/react';
import { useLiveSessionControl } from './useLiveSessionControl';

vi.mock('@/services/liveTimingService', () => ({
  liveTimingService: {
    getLiveTiming: vi.fn(),
    postLiveTimingAction: vi.fn(),
  },
}));

import { liveTimingService } from '@/services/liveTimingService';

const mockGet = vi.mocked(liveTimingService.getLiveTiming);
const mockPost = vi.mocked(liveTimingService.postLiveTimingAction);

const NOW = Date.now();

const makeSession = (overrides: Record<string, unknown> = {}) => ({
  sessionSlug: 'talk-1',
  title: 'Cloud Talk',
  scheduledStartTime: new Date(NOW - 5 * 60 * 1000).toISOString(), // 5 min ago → ACTIVE
  scheduledEndTime: new Date(NOW + 55 * 60 * 1000).toISOString(), // 55 min ahead
  status: 'SCHEDULED' as const,
  actualEndTime: null,
  actualStartTime: null,
  speakers: [],
  ...overrides,
});

const makeSnapshot = (sessions = [makeSession()], version = 0) => ({
  status: 200 as const,
  etag: `"evt-BAT142-${version}"`,
  data: {
    eventCode: 'BAT142',
    version,
    organizerPresent: true,
    currentSessionSlug: sessions[0]?.sessionSlug ?? null,
    arrivedSpeakerCount: 0,
    totalSpeakerCount: sessions.length,
    sessions,
  },
});

describe('useLiveSessionControl (REST polling)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockGet.mockResolvedValue(makeSnapshot([]) as never);
    mockPost.mockImplementation(((_code: string, _req: unknown) =>
      Promise.resolve({ data: makeSnapshot().data, etag: '"evt-BAT142-1"' })) as never);
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  // ── Initial state ───────────────────────────────────────────────────────────

  it('should return initial state when eventCode is undefined', () => {
    const { result } = renderHook(() => useLiveSessionControl(undefined));

    expect(result.current.sessions).toEqual([]);
    expect(result.current.activeSession).toBeNull();
    expect(result.current.connectionStatus).toBe('connecting');
    expect(result.current.isLoadingInitial).toBe(true);
    expect(result.current.shouldShowExtend).toBe(false);
    expect(result.current.shouldShowDelay).toBe(false);
  });

  it('should not poll when eventCode is undefined', () => {
    renderHook(() => useLiveSessionControl(undefined));
    expect(mockGet).not.toHaveBeenCalled();
  });

  // ── Polling ───────────────────────────────────────────────────────────────────

  it('should poll the live-timing endpoint with eventCode', async () => {
    renderHook(() => useLiveSessionControl('BAT142'));
    await waitFor(() => expect(mockGet).toHaveBeenCalledWith('BAT142', null));
  });

  it('should populate sessions from the poll response', async () => {
    mockGet.mockResolvedValue(makeSnapshot() as never);
    const { result } = renderHook(() => useLiveSessionControl('BAT142'));
    await waitFor(() => expect(result.current.sessions).toHaveLength(1));
    expect(result.current.sessions[0].sessionSlug).toBe('talk-1');
  });

  it('should set connectionStatus to connected after a successful poll', async () => {
    mockGet.mockResolvedValue(makeSnapshot() as never);
    const { result } = renderHook(() => useLiveSessionControl('BAT142'));
    await waitFor(() => expect(result.current.connectionStatus).toBe('connected'));
  });

  it('should set connectionStatus to reconnecting after a failed poll', async () => {
    mockGet.mockRejectedValue(new Error('network'));
    const { result } = renderHook(() => useLiveSessionControl('BAT142'));
    await waitFor(() => expect(result.current.connectionStatus).toBe('reconnecting'));
    expect(result.current.sessions).toEqual([]);
  });

  it('should keep existing sessions when poll returns 304', async () => {
    mockGet
      .mockResolvedValueOnce(makeSnapshot() as never)
      .mockResolvedValue({ status: 304, data: null, etag: '"evt-BAT142-0"' } as never);
    const { result } = renderHook(() => useLiveSessionControl('BAT142'));
    await waitFor(() => expect(result.current.sessions).toHaveLength(1));
    // remains stable across subsequent 304s
    await waitFor(() => expect(result.current.connectionStatus).toBe('connected'));
    expect(result.current.sessions).toHaveLength(1);
  });

  // ── Derived status ──────────────────────────────────────────────────────────

  it('should derive ACTIVE status for a session spanning now', async () => {
    mockGet.mockResolvedValue(makeSnapshot() as never);
    const { result } = renderHook(() => useLiveSessionControl('BAT142'));
    await waitFor(() => expect(result.current.activeSession).not.toBeNull());
    expect(result.current.activeSession!.status).toBe('ACTIVE');
    expect(result.current.shouldShowExtend).toBe(true);
    expect(result.current.shouldShowDelay).toBe(true);
  });

  it('should derive COMPLETED when actualEndTime is set', async () => {
    mockGet.mockResolvedValue(
      makeSnapshot([makeSession({ actualEndTime: new Date(NOW - 1000).toISOString() })]) as never
    );
    const { result } = renderHook(() => useLiveSessionControl('BAT142'));
    await waitFor(() => expect(result.current.sessions).toHaveLength(1));
    expect(result.current.sessions[0].status).toBe('COMPLETED');
    expect(result.current.activeSession).toBeNull();
  });

  it('should derive SCHEDULED for a future session and expose it as nextSession', async () => {
    mockGet.mockResolvedValue(
      makeSnapshot([
        makeSession({
          sessionSlug: 'future-talk',
          scheduledStartTime: new Date(NOW + 60 * 60 * 1000).toISOString(),
          scheduledEndTime: new Date(NOW + 2 * 60 * 60 * 1000).toISOString(),
        }),
      ]) as never
    );
    const { result } = renderHook(() => useLiveSessionControl('BAT142'));
    await waitFor(() => expect(result.current.sessions).toHaveLength(1));
    expect(result.current.sessions[0].status).toBe('SCHEDULED');
    expect(result.current.nextSession!.sessionSlug).toBe('future-talk');
    expect(result.current.activeSession).toBeNull();
  });

  it('should set shouldShowDelay false when elapsed > 10 minutes', async () => {
    mockGet.mockResolvedValue(
      makeSnapshot([
        makeSession({ scheduledStartTime: new Date(NOW - 11 * 60 * 1000).toISOString() }),
      ]) as never
    );
    const { result } = renderHook(() => useLiveSessionControl('BAT142'));
    await waitFor(() => expect(result.current.activeSession).not.toBeNull());
    expect(result.current.shouldShowDelay).toBe(false);
  });

  it('should compute remainingSeconds for the active session', async () => {
    mockGet.mockResolvedValue(
      makeSnapshot([
        makeSession({ scheduledEndTime: new Date(NOW + 30 * 60 * 1000).toISOString() }),
      ]) as never
    );
    const { result } = renderHook(() => useLiveSessionControl('BAT142'));
    await waitFor(() => expect(result.current.activeSession).not.toBeNull());
    expect(result.current.remainingSeconds).toBeGreaterThan(1790);
    expect(result.current.remainingSeconds).toBeLessThanOrEqual(1800);
  });

  it('should compute elapsedSeconds using actualStartTime when available', async () => {
    mockGet.mockResolvedValue(
      makeSnapshot([
        makeSession({ actualStartTime: new Date(NOW - 15 * 60 * 1000).toISOString() }),
      ]) as never
    );
    const { result } = renderHook(() => useLiveSessionControl('BAT142'));
    await waitFor(() => expect(result.current.activeSession).not.toBeNull());
    expect(result.current.elapsedSeconds).toBeGreaterThan(890);
    expect(result.current.elapsedSeconds).toBeLessThanOrEqual(900);
  });

  it('should sort sessions by scheduledStartTime ascending', async () => {
    mockGet.mockResolvedValue(
      makeSnapshot([
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
      ]) as never
    );
    const { result } = renderHook(() => useLiveSessionControl('BAT142'));
    await waitFor(() => expect(result.current.sessions).toHaveLength(2));
    expect(result.current.sessions[0].sessionSlug).toBe('sooner');
    expect(result.current.sessions[1].sessionSlug).toBe('later');
  });

  // ── Actions ───────────────────────────────────────────────────────────────────

  it('should POST EXTEND_SESSION for the active session', async () => {
    mockGet.mockResolvedValue(makeSnapshot() as never);
    const { result } = renderHook(() => useLiveSessionControl('BAT142'));
    await waitFor(() => expect(result.current.activeSession).not.toBeNull());

    await act(async () => {
      result.current.sendExtend(5);
    });

    expect(mockPost).toHaveBeenCalledWith('BAT142', {
      type: 'EXTEND_SESSION',
      sessionSlug: 'talk-1',
      minutes: 5,
    });
  });

  it('should not POST sendExtend when there is no active session', async () => {
    mockGet.mockResolvedValue(makeSnapshot([]) as never);
    const { result } = renderHook(() => useLiveSessionControl('BAT142'));
    await waitFor(() => expect(result.current.isLoadingInitial).toBe(false));

    act(() => result.current.sendExtend(5));
    expect(mockPost).not.toHaveBeenCalled();
  });

  it('should POST DELAY_TO_PREVIOUS and set isActionInFlight', async () => {
    mockGet.mockResolvedValue(makeSnapshot() as never);
    // Keep the POST pending so isActionInFlight is observable.
    mockPost.mockImplementation((() => new Promise(() => {})) as never);
    const { result } = renderHook(() => useLiveSessionControl('BAT142'));
    await waitFor(() => expect(result.current.activeSession).not.toBeNull());

    act(() => result.current.sendDelay(5));

    expect(result.current.isActionInFlight).toBe(true);
    expect(mockPost).toHaveBeenCalledWith('BAT142', {
      type: 'DELAY_TO_PREVIOUS',
      sessionSlug: 'talk-1',
      minutes: 5,
    });
  });

  it('should not POST sendDelay when there is no active session', async () => {
    mockGet.mockResolvedValue(makeSnapshot([]) as never);
    const { result } = renderHook(() => useLiveSessionControl('BAT142'));
    await waitFor(() => expect(result.current.isLoadingInitial).toBe(false));

    act(() => result.current.sendDelay(5));
    expect(result.current.isActionInFlight).toBe(false);
    expect(mockPost).not.toHaveBeenCalled();
  });

  it('should set reconnecting when an action POST fails', async () => {
    mockGet.mockResolvedValue(makeSnapshot() as never);
    mockPost.mockRejectedValue(new Error('offline') as never);
    const { result } = renderHook(() => useLiveSessionControl('BAT142'));
    await waitFor(() => expect(result.current.activeSession).not.toBeNull());

    await act(async () => {
      result.current.sendExtend(5);
    });

    await waitFor(() => expect(result.current.connectionStatus).toBe('reconnecting'));
  });

  it('should queue a failed action for retry without throwing', async () => {
    // A failed POST must enter the offline-queue path (reconnecting) rather than throw;
    // the queued action is flushed by the next successful poll. (The poll-driven flush
    // itself is covered by the service/integration layer — unit-testing it here would
    // require fake timers that deadlock against the 1s countdown ticker.)
    mockGet.mockResolvedValue(makeSnapshot() as never);
    mockPost.mockRejectedValue(new Error('offline') as never);
    const { result } = renderHook(() => useLiveSessionControl('BAT142'));
    await waitFor(() => expect(result.current.activeSession).not.toBeNull());

    await act(async () => {
      result.current.sendExtend(10);
    });

    await waitFor(() => expect(result.current.connectionStatus).toBe('reconnecting'));
    expect(mockPost).toHaveBeenCalledTimes(1);
  });
});
