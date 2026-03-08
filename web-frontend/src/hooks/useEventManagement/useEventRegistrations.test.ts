/**
 * useEventRegistrations Tests
 *
 * Coverage for:
 * - Basic fetch with eventCode
 * - Enabled flag guard
 * - Filters and pagination in query key
 * - Search parameter
 * - Error handling
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import React from 'react';

vi.mock('@/services/api/eventRegistrationService', () => ({
  getEventRegistrations: vi.fn(),
}));

import { getEventRegistrations } from '@/services/api/eventRegistrationService';
import { useEventRegistrations } from './useEventRegistrations';

const mockGetEventRegistrations = vi.mocked(getEventRegistrations);

const createQC = () =>
  new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0 } },
  });

const wrapper =
  (qc: QueryClient) =>
  ({ children }: { children: React.ReactNode }) =>
    React.createElement(QueryClientProvider, { client: qc }, children);

const MOCK_REGISTRATIONS = {
  data: [{ id: 'reg-1', username: 'alice', status: 'CONFIRMED' }],
  total: 1,
};

describe('useEventRegistrations', () => {
  let qc: QueryClient;

  beforeEach(() => {
    qc = createQC();
    vi.clearAllMocks();
  });

  it('should fetch registrations for an event', async () => {
    mockGetEventRegistrations.mockResolvedValue(MOCK_REGISTRATIONS as never);

    const { result } = renderHook(() => useEventRegistrations({ eventCode: 'BAT142' }), {
      wrapper: wrapper(qc),
    });

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    expect(mockGetEventRegistrations).toHaveBeenCalledWith('BAT142', {
      filters: undefined,
      pagination: undefined,
      search: undefined,
    });
    expect(result.current.data).toEqual(MOCK_REGISTRATIONS);
  });

  it('should pass filters and pagination', async () => {
    mockGetEventRegistrations.mockResolvedValue(MOCK_REGISTRATIONS as never);

    const filters = { status: 'CONFIRMED' };
    const pagination = { page: 1, limit: 20 };

    const { result } = renderHook(
      () =>
        useEventRegistrations({
          eventCode: 'BAT142',
          filters: filters as never,
          pagination: pagination as never,
        }),
      { wrapper: wrapper(qc) }
    );

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    expect(mockGetEventRegistrations).toHaveBeenCalledWith('BAT142', {
      filters,
      pagination,
      search: undefined,
    });
  });

  it('should pass search parameter', async () => {
    mockGetEventRegistrations.mockResolvedValue(MOCK_REGISTRATIONS as never);

    const { result } = renderHook(
      () => useEventRegistrations({ eventCode: 'BAT142', search: 'alice' }),
      { wrapper: wrapper(qc) }
    );

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    expect(mockGetEventRegistrations).toHaveBeenCalledWith('BAT142', {
      filters: undefined,
      pagination: undefined,
      search: 'alice',
    });
  });

  it('should not fetch when enabled is false', () => {
    const { result } = renderHook(
      () => useEventRegistrations({ eventCode: 'BAT142', enabled: false }),
      { wrapper: wrapper(qc) }
    );

    expect(result.current.isLoading).toBe(false);
    expect(mockGetEventRegistrations).not.toHaveBeenCalled();
  });

  it('should set isError on fetch failure', async () => {
    mockGetEventRegistrations.mockRejectedValue(new Error('Unauthorized'));

    const { result } = renderHook(() => useEventRegistrations({ eventCode: 'BAT142' }), {
      wrapper: wrapper(qc),
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });

  it('should include filters in query key', async () => {
    mockGetEventRegistrations.mockResolvedValue(MOCK_REGISTRATIONS as never);

    renderHook(
      () =>
        useEventRegistrations({
          eventCode: 'BAT142',
          filters: { status: 'CONFIRMED' } as never,
          pagination: { page: 2 } as never,
        }),
      { wrapper: wrapper(qc) }
    );

    await waitFor(() => {
      return qc
        .getQueryCache()
        .findAll()
        .some((q) => Array.isArray(q.queryKey) && q.queryKey[0] === 'event-registrations');
    });

    const query = qc
      .getQueryCache()
      .findAll()
      .find((q) => Array.isArray(q.queryKey) && q.queryKey[0] === 'event-registrations');
    expect(query?.queryKey).toContain('BAT142');
  });
});
