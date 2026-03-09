/**
 * Public Data Hooks Tests
 *
 * Coverage for simple React Query hooks that fetch public/common data:
 * - usePublicOrganizers
 * - useTimetable
 * - useCurrentEvent
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import React from 'react';

// ── Mocks ─────────────────────────────────────────────────────────────────────

vi.mock('@/services/publicOrganizerService', () => ({
  publicOrganizerService: { getPublicOrganizers: vi.fn() },
}));

vi.mock('@/services/timetableService/timetableService', () => ({
  timetableService: { getTimetable: vi.fn() },
}));

vi.mock('@/services/eventApiClient', () => ({
  eventApiClient: { getCurrentEvent: vi.fn() },
}));

import { publicOrganizerService } from '@/services/publicOrganizerService';
import { timetableService } from '@/services/timetableService/timetableService';
import { eventApiClient } from '@/services/eventApiClient';
import { usePublicOrganizers } from './usePublicOrganizers';
import { useTimetable } from './useTimetable/useTimetable';
import { useCurrentEvent } from './useCurrentEvent';

const mockGetOrganizers = vi.mocked(publicOrganizerService.getPublicOrganizers);
const mockGetTimetable = vi.mocked(timetableService.getTimetable);
const mockGetCurrentEvent = vi.mocked(eventApiClient.getCurrentEvent);

// ── Test helpers ──────────────────────────────────────────────────────────────

const createQC = () =>
  new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0 } },
  });

const wrapper =
  (qc: QueryClient) =>
  ({ children }: { children: React.ReactNode }) =>
    React.createElement(QueryClientProvider, { client: qc }, children);

// ── usePublicOrganizers ───────────────────────────────────────────────────────

describe('usePublicOrganizers', () => {
  let qc: QueryClient;

  beforeEach(() => {
    qc = createQC();
    vi.clearAllMocks();
  });

  it('should fetch organizers on mount', async () => {
    const organizers = [{ id: '1', firstName: 'Alice', lastName: 'Smith' }];
    mockGetOrganizers.mockResolvedValue(organizers as never);

    const { result } = renderHook(() => usePublicOrganizers(), { wrapper: wrapper(qc) });

    expect(result.current.isLoading).toBe(true);

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    expect(result.current.data).toEqual(organizers);
    expect(result.current.isError).toBe(false);
  });

  it('should return empty array when no organizers exist', async () => {
    mockGetOrganizers.mockResolvedValue([] as never);

    const { result } = renderHook(() => usePublicOrganizers(), { wrapper: wrapper(qc) });

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    expect(result.current.data).toEqual([]);
  });

  it('should use correct query key', async () => {
    mockGetOrganizers.mockResolvedValue([]);

    renderHook(() => usePublicOrganizers(), { wrapper: wrapper(qc) });

    await waitFor(() => {
      const queries = qc.getQueryCache().findAll();
      return queries.some(
        (q) =>
          Array.isArray(q.queryKey) && q.queryKey[0] === 'public' && q.queryKey[1] === 'organizers'
      );
    });

    const query = qc
      .getQueryCache()
      .findAll()
      .find((q) => Array.isArray(q.queryKey) && q.queryKey[0] === 'public');
    expect(query).toBeDefined();
  });
});

// ── useTimetable ──────────────────────────────────────────────────────────────

describe('useTimetable', () => {
  let qc: QueryClient;

  beforeEach(() => {
    qc = createQC();
    vi.clearAllMocks();
  });

  it('should fetch timetable when eventCode is provided', async () => {
    const timetable = { slots: [{ id: '1', time: '18:00' }] };
    mockGetTimetable.mockResolvedValue(timetable as never);

    const { result } = renderHook(() => useTimetable('BAT142'), { wrapper: wrapper(qc) });

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    expect(mockGetTimetable).toHaveBeenCalledWith('BAT142');
    expect(result.current.data).toEqual(timetable);
  });

  it('should not fetch when eventCode is undefined', () => {
    const { result } = renderHook(() => useTimetable(undefined), { wrapper: wrapper(qc) });

    expect(result.current.isLoading).toBe(false);
    expect(mockGetTimetable).not.toHaveBeenCalled();
  });

  it('should set isError on fetch failure', async () => {
    mockGetTimetable.mockRejectedValue(new Error('Not found'));

    const { result } = renderHook(() => useTimetable('BAT142'), { wrapper: wrapper(qc) });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });

  it('should use timetable+eventCode query key', async () => {
    mockGetTimetable.mockResolvedValue({ slots: [] } as never);

    renderHook(() => useTimetable('BAT142'), { wrapper: wrapper(qc) });

    await waitFor(() => {
      return qc
        .getQueryCache()
        .findAll()
        .some(
          (q) =>
            Array.isArray(q.queryKey) && q.queryKey[0] === 'timetable' && q.queryKey[1] === 'BAT142'
        );
    });

    const query = qc
      .getQueryCache()
      .findAll()
      .find((q) => Array.isArray(q.queryKey) && q.queryKey[0] === 'timetable');
    expect(query).toBeDefined();
  });
});

// ── useCurrentEvent ───────────────────────────────────────────────────────────

describe('useCurrentEvent', () => {
  let qc: QueryClient;

  beforeEach(() => {
    qc = createQC();
    vi.clearAllMocks();
  });

  it('should fetch current event on mount', async () => {
    const event = { eventCode: 'BAT142', title: 'BATbern #142', status: 'PUBLISHED' };
    mockGetCurrentEvent.mockResolvedValue(event as never);

    const { result } = renderHook(() => useCurrentEvent(), { wrapper: wrapper(qc) });

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    expect(mockGetCurrentEvent).toHaveBeenCalledWith({
      expand: ['topics', 'venue', 'speakers', 'sessions', 'registrations'],
    });
    expect(result.current.data).toEqual(event);
  });

  it('should accept retry option override', async () => {
    mockGetCurrentEvent.mockRejectedValue(new Error('Not found'));

    const { result } = renderHook(() => useCurrentEvent({ retry: false }), {
      wrapper: wrapper(qc),
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });

  it('should return null when no current event', async () => {
    mockGetCurrentEvent.mockResolvedValue(null as never);

    const { result } = renderHook(() => useCurrentEvent(), { wrapper: wrapper(qc) });

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    expect(result.current.data).toBeNull();
  });

  it('should use events:current query key', async () => {
    mockGetCurrentEvent.mockResolvedValue(null as never);

    renderHook(() => useCurrentEvent(), { wrapper: wrapper(qc) });

    await waitFor(() => {
      return qc
        .getQueryCache()
        .findAll()
        .some(
          (q) =>
            Array.isArray(q.queryKey) && q.queryKey[0] === 'events' && q.queryKey[1] === 'current'
        );
    });

    const query = qc
      .getQueryCache()
      .findAll()
      .find(
        (q) =>
          Array.isArray(q.queryKey) && q.queryKey[0] === 'events' && q.queryKey[1] === 'current'
      );
    expect(query).toBeDefined();
  });
});
