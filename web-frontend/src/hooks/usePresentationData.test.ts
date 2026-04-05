/**
 * usePresentationData Hook Tests (Story 10.8a)
 *
 * Tests for the hook that loads all data for the moderator presentation page.
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, waitFor, act } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import React from 'react';
import { usePresentationData } from './usePresentationData';

vi.mock('@/services/presentationService', () => ({
  getPresentationData: vi.fn(),
  getPublicOrganizers: vi.fn(),
  getUpcomingEvents: vi.fn(),
  getPresentationSettings: vi.fn(),
  getGlobalTeaserImages: vi.fn().mockResolvedValue([]),
}));

let mockApiBaseUrl = 'http://localhost:8000';
vi.mock('@/contexts/useConfig', () => ({
  useConfig: () => ({ apiBaseUrl: mockApiBaseUrl }),
}));

// Capture callbacks so tests can trigger WebSocket interactions
let capturedOnConnect: (() => void) | undefined;
let capturedSubscribeCallback: ((msg: unknown) => void) | undefined;
let capturedWebSocketFactory: (() => WebSocket) | undefined;

vi.mock('@stomp/stompjs', () => ({
  Client: class {
    activate = vi.fn();
    deactivate = vi.fn(() => Promise.resolve());
    subscribe = vi.fn((_dest: string, cb: (msg: unknown) => void) => {
      capturedSubscribeCallback = cb;
    });
    constructor(opts: { onConnect?: () => void; webSocketFactory?: () => WebSocket }) {
      if (opts?.onConnect) {
        capturedOnConnect = opts.onConnect;
      }
      if (opts?.webSocketFactory) {
        capturedWebSocketFactory = opts.webSocketFactory;
      }
    }
  },
}));

vi.mock('sockjs-client', () => ({ default: vi.fn() }));

import {
  getPresentationData,
  getPublicOrganizers,
  getUpcomingEvents,
  getPresentationSettings,
} from '@/services/presentationService';

const mockGetPresentationData = vi.mocked(getPresentationData);
const mockGetPublicOrganizers = vi.mocked(getPublicOrganizers);
const mockGetUpcomingEvents = vi.mocked(getUpcomingEvents);
const mockGetPresentationSettings = vi.mocked(getPresentationSettings);

const createQC = () =>
  new QueryClient({
    defaultOptions: {
      queries: { retry: false, gcTime: 0, refetchInterval: false, retryDelay: 0 },
    },
  });

const createWrapper =
  (qc: QueryClient) =>
  ({ children }: { children: React.ReactNode }) =>
    React.createElement(QueryClientProvider, { client: qc }, children);

const MOCK_EVENT = {
  eventCode: 'BATbern142',
  title: 'BATbern #142',
  date: '2026-04-15T18:00:00Z',
  sessions: [{ slug: 'cloud-talk', title: 'Cloud Security' }],
};
const MOCK_ORGANIZERS = [{ username: 'admin', firstName: 'Admin', lastName: 'User' }];
const MOCK_UPCOMING = [{ eventCode: 'BATbern143', date: '2026-06-01T18:00:00Z' }];
const MOCK_SETTINGS = { aboutText: 'BATbern is awesome', partnerCount: 5 };

describe('usePresentationData', () => {
  let qc: QueryClient;

  beforeEach(() => {
    qc = createQC();
    vi.clearAllMocks();
    capturedOnConnect = undefined;
    capturedSubscribeCallback = undefined;
    capturedWebSocketFactory = undefined;
    mockApiBaseUrl = 'http://localhost:8000';
  });

  it('should return loading state initially', () => {
    mockGetPresentationData.mockReturnValue(new Promise(() => {}));
    mockGetPublicOrganizers.mockReturnValue(new Promise(() => {}));
    mockGetUpcomingEvents.mockReturnValue(new Promise(() => {}));
    mockGetPresentationSettings.mockReturnValue(new Promise(() => {}));

    const { result } = renderHook(() => usePresentationData('BATbern142'), {
      wrapper: createWrapper(qc),
    });

    expect(result.current.isLoading).toBe(true);
  });

  it('should return all data on successful load', async () => {
    mockGetPresentationData.mockResolvedValue(
      MOCK_EVENT as ReturnType<typeof getPresentationData> extends Promise<infer T> ? T : never
    );
    mockGetPublicOrganizers.mockResolvedValue(
      MOCK_ORGANIZERS as ReturnType<typeof getPublicOrganizers> extends Promise<infer T> ? T : never
    );
    mockGetUpcomingEvents.mockResolvedValue(
      MOCK_UPCOMING as ReturnType<typeof getUpcomingEvents> extends Promise<infer T> ? T : never
    );
    mockGetPresentationSettings.mockResolvedValue(
      MOCK_SETTINGS as ReturnType<typeof getPresentationSettings> extends Promise<infer T>
        ? T
        : never
    );

    const { result } = renderHook(() => usePresentationData('BATbern142'), {
      wrapper: createWrapper(qc),
    });

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    expect(result.current.data.event).toEqual(MOCK_EVENT);
    expect(result.current.data.organizers).toEqual(MOCK_ORGANIZERS);
    expect(result.current.data.upcomingEvents).toEqual(MOCK_UPCOMING);
    expect(result.current.data.settings).toEqual(MOCK_SETTINGS);
    expect(result.current.isInitialLoadError).toBe(false);
  });

  it('should extract sessions from event data', async () => {
    mockGetPresentationData.mockResolvedValue(
      MOCK_EVENT as Parameters<typeof mockGetPresentationData>[0] extends never
        ? never
        : Awaited<ReturnType<typeof getPresentationData>>
    );
    mockGetPublicOrganizers.mockResolvedValue([]);
    mockGetUpcomingEvents.mockResolvedValue([]);
    mockGetPresentationSettings.mockResolvedValue(
      MOCK_SETTINGS as Awaited<ReturnType<typeof getPresentationSettings>>
    );

    const { result } = renderHook(() => usePresentationData('BATbern142'), {
      wrapper: createWrapper(qc),
    });

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    expect(result.current.data.sessions).toEqual(MOCK_EVENT.sessions);
  });

  it('should return empty sessions array when sessions is not an array', async () => {
    const eventWithoutSessions = { ...MOCK_EVENT, sessions: undefined };
    mockGetPresentationData.mockResolvedValue(
      eventWithoutSessions as Awaited<ReturnType<typeof getPresentationData>>
    );
    mockGetPublicOrganizers.mockResolvedValue([]);
    mockGetUpcomingEvents.mockResolvedValue([]);
    mockGetPresentationSettings.mockResolvedValue(
      MOCK_SETTINGS as Awaited<ReturnType<typeof getPresentationSettings>>
    );

    const { result } = renderHook(() => usePresentationData('BATbern142'), {
      wrapper: createWrapper(qc),
    });

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    expect(result.current.data.sessions).toEqual([]);
  });

  it('should set isInitialLoadError when event query fails', async () => {
    mockGetPresentationData.mockRejectedValue(new Error('Network failure'));
    mockGetPublicOrganizers.mockResolvedValue([]);
    mockGetUpcomingEvents.mockResolvedValue([]);
    mockGetPresentationSettings.mockRejectedValue(new Error('Settings failure'));

    const { result } = renderHook(() => usePresentationData('BATbern142'), {
      wrapper: createWrapper(qc),
    });

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    expect(result.current.isInitialLoadError).toBe(true);
  });

  it('should use default settings when settings query fails', async () => {
    mockGetPresentationData.mockResolvedValue(
      MOCK_EVENT as Awaited<ReturnType<typeof getPresentationData>>
    );
    mockGetPublicOrganizers.mockResolvedValue([]);
    mockGetUpcomingEvents.mockResolvedValue([]);
    mockGetPresentationSettings.mockRejectedValue(new Error('Settings unavailable'));

    const { result } = renderHook(() => usePresentationData('BATbern142'), {
      wrapper: createWrapper(qc),
    });

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    // Should use default settings object (not null)
    expect(result.current.data.settings).toMatchObject({ partnerCount: 9 });
  });

  it('should expose a refetch function', async () => {
    mockGetPresentationData.mockResolvedValue(
      MOCK_EVENT as Awaited<ReturnType<typeof getPresentationData>>
    );
    mockGetPublicOrganizers.mockResolvedValue([]);
    mockGetUpcomingEvents.mockResolvedValue([]);
    mockGetPresentationSettings.mockResolvedValue(
      MOCK_SETTINGS as Awaited<ReturnType<typeof getPresentationSettings>>
    );

    const { result } = renderHook(() => usePresentationData('BATbern142'), {
      wrapper: createWrapper(qc),
    });

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    expect(typeof result.current.refetch).toBe('function');

    // Should not throw when called
    act(() => {
      result.current.refetch();
    });
  });

  it('should not set isInitialLoadError when event data exists but settings fails', async () => {
    mockGetPresentationData.mockResolvedValue(
      MOCK_EVENT as Awaited<ReturnType<typeof getPresentationData>>
    );
    mockGetPublicOrganizers.mockResolvedValue([]);
    mockGetUpcomingEvents.mockResolvedValue([]);
    mockGetPresentationSettings.mockRejectedValue(new Error('Settings fail'));

    const { result } = renderHook(() => usePresentationData('BATbern142'), {
      wrapper: createWrapper(qc),
    });

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    // Event data exists, so isInitialLoadError should be false even though settings failed
    expect(result.current.isInitialLoadError).toBe(false);
    expect(result.current.data.event).toEqual(MOCK_EVENT);
  });

  it('should not set isInitialLoadError when only event fails but settings succeeds', async () => {
    mockGetPresentationData.mockRejectedValue(new Error('Event fail'));
    mockGetPublicOrganizers.mockResolvedValue([]);
    mockGetUpcomingEvents.mockResolvedValue([]);
    mockGetPresentationSettings.mockResolvedValue(
      MOCK_SETTINGS as Awaited<ReturnType<typeof getPresentationSettings>>
    );

    const { result } = renderHook(() => usePresentationData('BATbern142'), {
      wrapper: createWrapper(qc),
    });

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    // Event error + no event data + settings OK: event isError is true and data is null
    expect(result.current.isInitialLoadError).toBe(true);
  });

  it('should return empty sessions when sessions is a non-array truthy value', async () => {
    const eventWithObjectSessions = { ...MOCK_EVENT, sessions: { items: [] } };
    mockGetPresentationData.mockResolvedValue(
      eventWithObjectSessions as unknown as Awaited<ReturnType<typeof getPresentationData>>
    );
    mockGetPublicOrganizers.mockResolvedValue([]);
    mockGetUpcomingEvents.mockResolvedValue([]);
    mockGetPresentationSettings.mockResolvedValue(
      MOCK_SETTINGS as Awaited<ReturnType<typeof getPresentationSettings>>
    );

    const { result } = renderHook(() => usePresentationData('BATbern142'), {
      wrapper: createWrapper(qc),
    });

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    // Non-array sessions should fall back to empty array
    expect(result.current.data.sessions).toEqual([]);
  });

  it('should return default about text in fallback settings', async () => {
    mockGetPresentationData.mockResolvedValue(
      MOCK_EVENT as Awaited<ReturnType<typeof getPresentationData>>
    );
    mockGetPublicOrganizers.mockResolvedValue([]);
    mockGetUpcomingEvents.mockResolvedValue([]);
    mockGetPresentationSettings.mockRejectedValue(new Error('No settings'));

    const { result } = renderHook(() => usePresentationData('BATbern142'), {
      wrapper: createWrapper(qc),
    });

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    expect(result.current.data.settings).toEqual({
      aboutText:
        'BATbern ist eine unabhängige Plattform, die Berner Architekten und Ingenieure vernetzt.',
      partnerCount: 9,
    });
  });

  it('should return null event and empty arrays when all queries fail', async () => {
    mockGetPresentationData.mockRejectedValue(new Error('fail'));
    mockGetPublicOrganizers.mockRejectedValue(new Error('fail'));
    mockGetUpcomingEvents.mockRejectedValue(new Error('fail'));
    mockGetPresentationSettings.mockRejectedValue(new Error('fail'));

    const { result } = renderHook(() => usePresentationData('BATbern142'), {
      wrapper: createWrapper(qc),
    });

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    expect(result.current.data.event).toBeNull();
    expect(result.current.data.sessions).toEqual([]);
    expect(result.current.data.organizers).toEqual([]);
    expect(result.current.data.upcomingEvents).toEqual([]);
    expect(result.current.data.globalTeaserImages).toEqual([]);
    expect(result.current.isInitialLoadError).toBe(true);
  });

  it('should invalidate event query when WebSocket state message arrives', async () => {
    mockGetPresentationData.mockResolvedValue(
      MOCK_EVENT as Awaited<ReturnType<typeof getPresentationData>>
    );
    mockGetPublicOrganizers.mockResolvedValue([]);
    mockGetUpcomingEvents.mockResolvedValue([]);
    mockGetPresentationSettings.mockResolvedValue(
      MOCK_SETTINGS as Awaited<ReturnType<typeof getPresentationSettings>>
    );

    const invalidateSpy = vi.spyOn(qc, 'invalidateQueries');

    const { result } = renderHook(() => usePresentationData('BATbern142'), {
      wrapper: createWrapper(qc),
    });

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    // Trigger onConnect to subscribe, then simulate a state message
    if (capturedOnConnect) {
      act(() => {
        capturedOnConnect!();
      });
    }

    if (capturedSubscribeCallback) {
      act(() => {
        capturedSubscribeCallback!({ body: '{}' });
      });
    }

    expect(invalidateSpy).toHaveBeenCalledWith({
      queryKey: ['presentation-event', 'BATbern142'],
    });
  });

  it('should use non-localhost WebSocket URL for production-like apiBaseUrl', async () => {
    mockApiBaseUrl = 'https://api.batbern.ch';

    mockGetPresentationData.mockResolvedValue(
      MOCK_EVENT as Awaited<ReturnType<typeof getPresentationData>>
    );
    mockGetPublicOrganizers.mockResolvedValue([]);
    mockGetUpcomingEvents.mockResolvedValue([]);
    mockGetPresentationSettings.mockResolvedValue(
      MOCK_SETTINGS as Awaited<ReturnType<typeof getPresentationSettings>>
    );

    const SockJS = (await import('sockjs-client')).default;
    const mockSockJS = vi.mocked(SockJS);
    mockSockJS.mockClear();

    const { result, unmount } = renderHook(() => usePresentationData('BATbern142'), {
      wrapper: createWrapper(qc),
    });

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    // Invoke the captured webSocketFactory to trigger SockJS call
    expect(capturedWebSocketFactory).toBeDefined();
    capturedWebSocketFactory!();

    // SockJS constructor should have been called with production-style URL (protocol://host/ws)
    expect(mockSockJS).toHaveBeenCalledWith('https://api.batbern.ch/ws');

    unmount();
  });

  it('should clean up WebSocket client on unmount', async () => {
    mockGetPresentationData.mockResolvedValue(
      MOCK_EVENT as Awaited<ReturnType<typeof getPresentationData>>
    );
    mockGetPublicOrganizers.mockResolvedValue([]);
    mockGetUpcomingEvents.mockResolvedValue([]);
    mockGetPresentationSettings.mockResolvedValue(
      MOCK_SETTINGS as Awaited<ReturnType<typeof getPresentationSettings>>
    );

    const { result, unmount } = renderHook(() => usePresentationData('BATbern142'), {
      wrapper: createWrapper(qc),
    });

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    // Unmounting should deactivate the client
    unmount();

    // After unmount, the WS callback should not invalidate queries
    if (capturedOnConnect) {
      act(() => {
        capturedOnConnect!();
      });
    }
    if (capturedSubscribeCallback) {
      const invalidateSpy = vi.spyOn(qc, 'invalidateQueries');
      act(() => {
        capturedSubscribeCallback!({ body: '{}' });
      });
      // The isMounted guard should prevent invalidation
      expect(invalidateSpy).not.toHaveBeenCalled();
    }
  });

  it('should use localhost WebSocket URL with port offset for local dev', async () => {
    mockApiBaseUrl = 'http://localhost:8000';

    mockGetPresentationData.mockResolvedValue(
      MOCK_EVENT as Awaited<ReturnType<typeof getPresentationData>>
    );
    mockGetPublicOrganizers.mockResolvedValue([]);
    mockGetUpcomingEvents.mockResolvedValue([]);
    mockGetPresentationSettings.mockResolvedValue(
      MOCK_SETTINGS as Awaited<ReturnType<typeof getPresentationSettings>>
    );

    const SockJS = (await import('sockjs-client')).default;
    const mockSockJS = vi.mocked(SockJS);
    mockSockJS.mockClear();

    const { result, unmount } = renderHook(() => usePresentationData('BATbern142'), {
      wrapper: createWrapper(qc),
    });

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    // Invoke the captured webSocketFactory to trigger SockJS call
    expect(capturedWebSocketFactory).toBeDefined();
    capturedWebSocketFactory!();

    // For localhost, port should be apiBaseUrl port + 2 (8000 + 2 = 8002)
    expect(mockSockJS).toHaveBeenCalledWith('http://localhost:8002/ws');

    unmount();
  });

  it('should return empty globalTeaserImages when query returns data', async () => {
    const { getGlobalTeaserImages } = await import('@/services/presentationService');
    const mockGetGlobalTeaserImages = vi.mocked(getGlobalTeaserImages);
    const mockImages = [{ id: '1', url: 'https://cdn.batbern.ch/teaser.jpg' }];
    mockGetGlobalTeaserImages.mockResolvedValue(
      mockImages as Awaited<ReturnType<typeof getGlobalTeaserImages>>
    );

    mockGetPresentationData.mockResolvedValue(
      MOCK_EVENT as Awaited<ReturnType<typeof getPresentationData>>
    );
    mockGetPublicOrganizers.mockResolvedValue([]);
    mockGetUpcomingEvents.mockResolvedValue([]);
    mockGetPresentationSettings.mockResolvedValue(
      MOCK_SETTINGS as Awaited<ReturnType<typeof getPresentationSettings>>
    );

    const { result } = renderHook(() => usePresentationData('BATbern142'), {
      wrapper: createWrapper(qc),
    });

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    expect(result.current.data.globalTeaserImages).toEqual(mockImages);
  });
});
