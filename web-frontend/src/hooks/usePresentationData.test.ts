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
}));

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
});
