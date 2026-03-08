/**
 * useEventTypes Hooks Tests
 *
 * Coverage for:
 * - useEventTypes: fetch all event types
 * - useEventType: fetch single type with enabled guard
 * - useUpdateEventType: mutation + cache invalidation
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, waitFor, act } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import React from 'react';

vi.mock('@/services/eventTypeService', () => ({
  eventTypeService: {
    getAllEventTypes: vi.fn(),
    getEventType: vi.fn(),
    updateEventType: vi.fn(),
  },
}));

import { eventTypeService } from '@/services/eventTypeService';
import { useEventTypes, useEventType, useUpdateEventType } from './useEventTypes';

const mockGetAllEventTypes = vi.mocked(eventTypeService.getAllEventTypes);
const mockGetEventType = vi.mocked(eventTypeService.getEventType);
const mockUpdateEventType = vi.mocked(eventTypeService.updateEventType);

const createQC = () =>
  new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0 }, mutations: { retry: false } },
  });

const wrapper =
  (qc: QueryClient) =>
  ({ children }: { children: React.ReactNode }) =>
    React.createElement(QueryClientProvider, { client: qc }, children);

const MOCK_EVENT_TYPES = [
  { type: 'EVENING', defaultSlots: 2, defaultDuration: 180 },
  { type: 'FULL_DAY', defaultSlots: 4, defaultDuration: 480 },
];

describe('useEventTypes', () => {
  let qc: QueryClient;

  beforeEach(() => {
    qc = createQC();
    vi.clearAllMocks();
  });

  it('should fetch all event types', async () => {
    mockGetAllEventTypes.mockResolvedValue(MOCK_EVENT_TYPES as never);

    const { result } = renderHook(() => useEventTypes(), { wrapper: wrapper(qc) });

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    expect(mockGetAllEventTypes).toHaveBeenCalled();
    expect(result.current.data).toEqual(MOCK_EVENT_TYPES);
  });

  it('should set isError on failure', async () => {
    mockGetAllEventTypes.mockRejectedValue(new Error('Forbidden'));

    const { result } = renderHook(() => useEventTypes(), { wrapper: wrapper(qc) });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});

describe('useEventType', () => {
  let qc: QueryClient;

  beforeEach(() => {
    qc = createQC();
    vi.clearAllMocks();
  });

  it('should fetch event type by type key', async () => {
    mockGetEventType.mockResolvedValue(MOCK_EVENT_TYPES[0] as never);

    const { result } = renderHook(() => useEventType('EVENING' as never), { wrapper: wrapper(qc) });

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    expect(mockGetEventType).toHaveBeenCalledWith('EVENING');
    expect(result.current.data).toEqual(MOCK_EVENT_TYPES[0]);
  });

  it('should not fetch when type is falsy', () => {
    const { result } = renderHook(() => useEventType('' as never), { wrapper: wrapper(qc) });

    expect(result.current.isLoading).toBe(false);
    expect(mockGetEventType).not.toHaveBeenCalled();
  });
});

describe('useUpdateEventType', () => {
  let qc: QueryClient;

  beforeEach(() => {
    qc = createQC();
    vi.clearAllMocks();
  });

  it('should call updateEventType with type and config', async () => {
    mockUpdateEventType.mockResolvedValue(MOCK_EVENT_TYPES[0] as never);

    const { result } = renderHook(() => useUpdateEventType(), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current.mutateAsync({
        type: 'EVENING' as never,
        config: { defaultSlots: 3 } as never,
      });
    });

    expect(mockUpdateEventType).toHaveBeenCalledWith('EVENING', { defaultSlots: 3 });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
  });

  it('should invalidate eventTypes cache on success', async () => {
    mockUpdateEventType.mockResolvedValue(MOCK_EVENT_TYPES[0] as never);
    const invalidateSpy = vi.spyOn(qc, 'invalidateQueries');

    const { result } = renderHook(() => useUpdateEventType(), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current.mutateAsync({ type: 'EVENING' as never, config: {} as never });
    });

    expect(invalidateSpy).toHaveBeenCalledWith(
      expect.objectContaining({ queryKey: ['eventTypes'] })
    );
  });

  it('should set isError on failure', async () => {
    mockUpdateEventType.mockRejectedValue(new Error('Forbidden'));

    const { result } = renderHook(() => useUpdateEventType(), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current
        .mutateAsync({ type: 'EVENING' as never, config: {} as never })
        .catch(() => {});
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});
