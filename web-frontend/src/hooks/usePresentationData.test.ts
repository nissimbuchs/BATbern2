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

// Story 15.1: presenter now polls live-timing over REST instead of subscribing to STOMP.
vi.mock('@/services/liveTimingService', () => ({
  liveTimingService: { getLiveTiming: vi.fn() },
}));

import { liveTimingService } from '@/services/liveTimingService';

const mockGetLiveTiming = vi.mocked(liveTimingService.getLiveTiming);

const makeLiveTiming = (version: number) => ({
  status: 200 as const,
  etag: `"evt-BATbern142-${version}"`,
  data: {
    eventCode: 'BATbern142',
    version,
    organizerPresent: false,
    currentSessionSlug: null,
    arrivedSpeakerCount: 0,
    totalSpeakerCount: 0,
    sessions: [],
  },
});

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
    // Default: live-timing poll returns an unchanging version 0 snapshot.
    mockGetLiveTiming.mockResolvedValue(makeLiveTiming(0) as never);
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

  it('should poll the live-timing endpoint anonymously (skipAuth)', async () => {
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

    await waitFor(() => expect(mockGetLiveTiming).toHaveBeenCalled());
    // eventCode, etag(null on first poll), skipAuth=true
    expect(mockGetLiveTiming).toHaveBeenCalledWith('BATbern142', null, true);
    expect(result.current).toBeDefined();
  });

  it('should invalidate the event query when the live-timing version changes', async () => {
    mockGetPresentationData.mockResolvedValue(
      MOCK_EVENT as Awaited<ReturnType<typeof getPresentationData>>
    );
    mockGetPublicOrganizers.mockResolvedValue([]);
    mockGetUpcomingEvents.mockResolvedValue([]);
    mockGetPresentationSettings.mockResolvedValue(
      MOCK_SETTINGS as Awaited<ReturnType<typeof getPresentationSettings>>
    );

    // First poll establishes version 0; the second reports a bumped version 1.
    mockGetLiveTiming
      .mockResolvedValueOnce(makeLiveTiming(0) as never)
      .mockResolvedValue(makeLiveTiming(1) as never);

    const invalidateSpy = vi.spyOn(qc, 'invalidateQueries');

    const { result } = renderHook(() => usePresentationData('BATbern142'), {
      wrapper: createWrapper(qc),
    });

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    await waitFor(
      () =>
        expect(invalidateSpy).toHaveBeenCalledWith({
          queryKey: ['presentation-event', 'BATbern142'],
        }),
      { timeout: 8000 }
    );
  });

  it('should not invalidate the event query while the version is unchanged', async () => {
    mockGetPresentationData.mockResolvedValue(
      MOCK_EVENT as Awaited<ReturnType<typeof getPresentationData>>
    );
    mockGetPublicOrganizers.mockResolvedValue([]);
    mockGetUpcomingEvents.mockResolvedValue([]);
    mockGetPresentationSettings.mockResolvedValue(
      MOCK_SETTINGS as Awaited<ReturnType<typeof getPresentationSettings>>
    );
    mockGetLiveTiming.mockResolvedValue(makeLiveTiming(0) as never);

    const { result } = renderHook(() => usePresentationData('BATbern142'), {
      wrapper: createWrapper(qc),
    });

    await waitFor(() => expect(result.current.isLoading).toBe(false));
    const invalidateSpy = vi.spyOn(qc, 'invalidateQueries');
    await waitFor(() => expect(mockGetLiveTiming).toHaveBeenCalled());

    expect(invalidateSpy).not.toHaveBeenCalledWith({
      queryKey: ['presentation-event', 'BATbern142'],
    });
  });

  it('should stop polling live-timing after unmount', async () => {
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
    await waitFor(() => expect(mockGetLiveTiming).toHaveBeenCalled());

    unmount();
    const callsAfterMount = mockGetLiveTiming.mock.calls.length;
    // No further polls should be scheduled after unmount.
    await new Promise((r) => setTimeout(r, 50));
    expect(mockGetLiveTiming.mock.calls.length).toBe(callsAfterMount);
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
