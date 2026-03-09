/**
 * useEvents Hooks Tests
 *
 * Coverage for:
 * - useEvents: paginated list with optional filters/expand
 * - useEvent: detail with eventCode guard + include
 * - useEventWorkflow: workflow state with guard
 * - useCriticalTasks: stub returning empty list, guard
 * - useTeamActivity: stub returning empty list, guard
 * - useCreateEvent: mutation + cache invalidation
 * - useUpdateEvent: mutation + invalidation
 * - useDeleteEvent: mutation + cache invalidation
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, waitFor, act } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import React from 'react';

vi.mock('@/services/eventApiClient', () => ({
  eventApiClient: {
    getEvents: vi.fn(),
    getEvent: vi.fn(),
    getEventWorkflow: vi.fn(),
    createEvent: vi.fn(),
    patchEvent: vi.fn(),
    deleteEvent: vi.fn(),
  },
}));

import { eventApiClient } from '@/services/eventApiClient';
import {
  useEvents,
  useEvent,
  useEventWorkflow,
  useCriticalTasks,
  useTeamActivity,
  useCreateEvent,
  useUpdateEvent,
  useDeleteEvent,
} from './useEvents';

const mockGetEvents = vi.mocked(eventApiClient.getEvents);
const mockGetEvent = vi.mocked(eventApiClient.getEvent);
const mockGetEventWorkflow = vi.mocked(eventApiClient.getEventWorkflow);
const mockCreateEvent = vi.mocked(eventApiClient.createEvent);
const mockPatchEvent = vi.mocked(eventApiClient.patchEvent);
const mockDeleteEvent = vi.mocked(eventApiClient.deleteEvent);

const createQC = () =>
  new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0 }, mutations: { retry: false } },
  });

const wrapper =
  (qc: QueryClient) =>
  ({ children }: { children: React.ReactNode }) =>
    React.createElement(QueryClientProvider, { client: qc }, children);

const MOCK_EVENT = { eventCode: 'BAT142', title: 'BATbern #142', status: 'PUBLISHED' };
const MOCK_EVENTS_PAGE = { data: [MOCK_EVENT], total: 1, page: 1, limit: 20 };
const MOCK_WORKFLOW = { state: 'IN_PROGRESS', transitions: [] };

// ── useEvents ─────────────────────────────────────────────────────────────────

describe('useEvents', () => {
  let qc: QueryClient;

  beforeEach(() => {
    qc = createQC();
    vi.clearAllMocks();
  });

  it('should fetch events with pagination', async () => {
    mockGetEvents.mockResolvedValue(MOCK_EVENTS_PAGE as never);

    const { result } = renderHook(() => useEvents({ page: 1, limit: 20 }), {
      wrapper: wrapper(qc),
    });

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    expect(mockGetEvents).toHaveBeenCalledWith({ page: 1, limit: 20 }, undefined, undefined);
    expect(result.current.data).toEqual(MOCK_EVENTS_PAGE);
  });

  it('should pass filters and expand options', async () => {
    mockGetEvents.mockResolvedValue(MOCK_EVENTS_PAGE as never);

    const filters = { status: 'PUBLISHED' };
    const options = { expand: ['registrations'] };

    const { result } = renderHook(
      () => useEvents({ page: 1, limit: 10 }, filters as never, options),
      { wrapper: wrapper(qc) }
    );

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    expect(mockGetEvents).toHaveBeenCalledWith({ page: 1, limit: 10 }, filters, options);
  });

  it('should set isError on fetch failure', async () => {
    mockGetEvents.mockRejectedValue(new Error('Unauthorized'));

    const { result } = renderHook(() => useEvents({ page: 1, limit: 20 }), {
      wrapper: wrapper(qc),
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});

// ── useEvent ──────────────────────────────────────────────────────────────────

describe('useEvent', () => {
  let qc: QueryClient;

  beforeEach(() => {
    qc = createQC();
    vi.clearAllMocks();
  });

  it('should fetch event by eventCode', async () => {
    mockGetEvent.mockResolvedValue(MOCK_EVENT as never);

    const { result } = renderHook(() => useEvent('BAT142'), { wrapper: wrapper(qc) });

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    expect(mockGetEvent).toHaveBeenCalledWith('BAT142', undefined);
    expect(result.current.data).toEqual(MOCK_EVENT);
  });

  it('should pass include array as expand option', async () => {
    mockGetEvent.mockResolvedValue(MOCK_EVENT as never);

    const { result } = renderHook(() => useEvent('BAT142', ['speakers', 'sessions']), {
      wrapper: wrapper(qc),
    });

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    expect(mockGetEvent).toHaveBeenCalledWith('BAT142', { expand: ['speakers', 'sessions'] });
  });

  it('should not fetch when eventCode is undefined', () => {
    const { result } = renderHook(() => useEvent(undefined), { wrapper: wrapper(qc) });

    expect(result.current.isLoading).toBe(false);
    expect(mockGetEvent).not.toHaveBeenCalled();
  });

  it('should not fetch when eventCode is empty string', () => {
    const { result } = renderHook(() => useEvent(''), { wrapper: wrapper(qc) });

    expect(result.current.isLoading).toBe(false);
    expect(mockGetEvent).not.toHaveBeenCalled();
  });
});

// ── useEventWorkflow ──────────────────────────────────────────────────────────

describe('useEventWorkflow', () => {
  let qc: QueryClient;

  beforeEach(() => {
    qc = createQC();
    vi.clearAllMocks();
  });

  it('should fetch workflow state for event', async () => {
    mockGetEventWorkflow.mockResolvedValue(MOCK_WORKFLOW as never);

    const { result } = renderHook(() => useEventWorkflow('BAT142'), { wrapper: wrapper(qc) });

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    expect(mockGetEventWorkflow).toHaveBeenCalledWith('BAT142');
    expect(result.current.data).toEqual(MOCK_WORKFLOW);
  });

  it('should not fetch when eventCode is undefined', () => {
    const { result } = renderHook(() => useEventWorkflow(undefined), { wrapper: wrapper(qc) });

    expect(result.current.isLoading).toBe(false);
    expect(mockGetEventWorkflow).not.toHaveBeenCalled();
  });
});

// ── useCriticalTasks ──────────────────────────────────────────────────────────

describe('useCriticalTasks', () => {
  let qc: QueryClient;

  beforeEach(() => {
    qc = createQC();
    vi.clearAllMocks();
  });

  it('should return empty data stub when organizerUsername is provided', async () => {
    const { result } = renderHook(() => useCriticalTasks('alice'), { wrapper: wrapper(qc) });

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    expect(result.current.data).toEqual({ data: [], total: 0 });
  });

  it('should not fetch when organizerUsername is undefined', () => {
    const { result } = renderHook(() => useCriticalTasks(undefined), { wrapper: wrapper(qc) });

    expect(result.current.isLoading).toBe(false);
  });

  it('should use custom limit in query key', async () => {
    const { result } = renderHook(() => useCriticalTasks('alice', 5), { wrapper: wrapper(qc) });

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    const query = qc
      .getQueryCache()
      .findAll()
      .find((q) => Array.isArray(q.queryKey) && q.queryKey[0] === 'criticalTasks');
    expect(query?.queryKey).toContain(5);
  });
});

// ── useTeamActivity ───────────────────────────────────────────────────────────

describe('useTeamActivity', () => {
  let qc: QueryClient;

  beforeEach(() => {
    qc = createQC();
    vi.clearAllMocks();
  });

  it('should return empty data stub when organizerUsername is provided', async () => {
    const { result } = renderHook(() => useTeamActivity('alice'), { wrapper: wrapper(qc) });

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    expect(result.current.data?.data).toEqual([]);
    expect(result.current.data?.pagination.totalItems).toBe(0);
  });

  it('should not fetch when organizerUsername is undefined', () => {
    const { result } = renderHook(() => useTeamActivity(undefined), { wrapper: wrapper(qc) });

    expect(result.current.isLoading).toBe(false);
  });
});

// ── useCreateEvent ────────────────────────────────────────────────────────────

describe('useCreateEvent', () => {
  let qc: QueryClient;

  beforeEach(() => {
    qc = createQC();
    vi.clearAllMocks();
  });

  it('should call createEvent with data', async () => {
    mockCreateEvent.mockResolvedValue(MOCK_EVENT as never);

    const { result } = renderHook(() => useCreateEvent(), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current.mutateAsync({ title: 'New Event' } as never);
    });

    expect(mockCreateEvent).toHaveBeenCalledWith({ title: 'New Event' });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
  });

  it('should invalidate events query on success', async () => {
    mockCreateEvent.mockResolvedValue(MOCK_EVENT as never);
    const invalidateSpy = vi.spyOn(qc, 'invalidateQueries');

    const { result } = renderHook(() => useCreateEvent(), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current.mutateAsync({} as never);
    });

    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['events'] });
  });

  it('should set isError on failure', async () => {
    mockCreateEvent.mockRejectedValue(new Error('Validation error'));

    const { result } = renderHook(() => useCreateEvent(), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current.mutateAsync({} as never).catch(() => {});
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});

// ── useUpdateEvent ────────────────────────────────────────────────────────────

describe('useUpdateEvent', () => {
  let qc: QueryClient;

  beforeEach(() => {
    qc = createQC();
    vi.clearAllMocks();
  });

  it('should call patchEvent with eventCode and data', async () => {
    mockPatchEvent.mockResolvedValue(MOCK_EVENT as never);

    const { result } = renderHook(() => useUpdateEvent(), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current.mutateAsync({
        eventCode: 'BAT142',
        data: { title: 'Updated Title' } as never,
      });
    });

    expect(mockPatchEvent).toHaveBeenCalledWith('BAT142', { title: 'Updated Title' });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
  });

  it('should invalidate events and event caches on success (same eventCode)', async () => {
    mockPatchEvent.mockResolvedValue(MOCK_EVENT as never);
    const invalidateSpy = vi.spyOn(qc, 'invalidateQueries');

    const { result } = renderHook(() => useUpdateEvent(), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current.mutateAsync({ eventCode: 'BAT142', data: {} as never });
    });

    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['events'] });
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['event', 'BAT142'] });
  });

  it('should set isError on failure', async () => {
    mockPatchEvent.mockRejectedValue(new Error('Conflict'));

    const { result } = renderHook(() => useUpdateEvent(), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current.mutateAsync({ eventCode: 'BAT142', data: {} as never }).catch(() => {});
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});

// ── useDeleteEvent ────────────────────────────────────────────────────────────

describe('useDeleteEvent', () => {
  let qc: QueryClient;

  beforeEach(() => {
    qc = createQC();
    vi.clearAllMocks();
  });

  it('should call deleteEvent with eventCode', async () => {
    mockDeleteEvent.mockResolvedValue(undefined as never);

    const { result } = renderHook(() => useDeleteEvent(), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current.mutateAsync('BAT142');
    });

    expect(mockDeleteEvent).toHaveBeenCalledWith('BAT142');
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
  });

  it('should invalidate events query on success', async () => {
    mockDeleteEvent.mockResolvedValue(undefined as never);
    const invalidateSpy = vi.spyOn(qc, 'invalidateQueries');

    const { result } = renderHook(() => useDeleteEvent(), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current.mutateAsync('BAT142');
    });

    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['events'] });
  });

  it('should set isError on failure', async () => {
    mockDeleteEvent.mockRejectedValue(new Error('Not found'));

    const { result } = renderHook(() => useDeleteEvent(), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current.mutateAsync('GHOST').catch(() => {});
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});
