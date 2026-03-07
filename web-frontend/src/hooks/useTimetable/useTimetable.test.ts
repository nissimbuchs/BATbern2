/**
 * useTimetable Hook Tests
 *
 * Coverage for:
 * - useTimetable: fetch timetable for eventCode, enabled guard
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import React from 'react';

vi.mock('@/services/timetableService/timetableService', () => ({
  timetableService: {
    getTimetable: vi.fn(),
  },
}));

import { timetableService } from '@/services/timetableService/timetableService';
import { useTimetable } from './useTimetable';

const mockGetTimetable = vi.mocked(timetableService.getTimetable);

const createQC = () =>
  new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0 } },
  });

const wrapper =
  (qc: QueryClient) =>
  ({ children }: { children: React.ReactNode }) =>
    React.createElement(QueryClientProvider, { client: qc }, children);

const MOCK_TIMETABLE = { slots: [{ id: 'slot-1', startTime: '09:00' }] };

describe('useTimetable', () => {
  let qc: QueryClient;

  beforeEach(() => {
    qc = createQC();
    vi.clearAllMocks();
  });

  it('should fetch timetable for the given eventCode', async () => {
    mockGetTimetable.mockResolvedValue(MOCK_TIMETABLE as never);

    const { result } = renderHook(() => useTimetable('BAT142'), { wrapper: wrapper(qc) });

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    expect(mockGetTimetable).toHaveBeenCalledWith('BAT142');
    expect(result.current.data).toEqual(MOCK_TIMETABLE);
  });

  it('should not fetch when eventCode is undefined', () => {
    const { result } = renderHook(() => useTimetable(undefined), { wrapper: wrapper(qc) });

    expect(result.current.isLoading).toBe(false);
    expect(mockGetTimetable).not.toHaveBeenCalled();
  });

  it('should not fetch when eventCode is empty string', () => {
    const { result } = renderHook(() => useTimetable(''), { wrapper: wrapper(qc) });

    expect(result.current.isLoading).toBe(false);
    expect(mockGetTimetable).not.toHaveBeenCalled();
  });

  it('should set isError on failure', async () => {
    mockGetTimetable.mockRejectedValue(new Error('Not found'));

    const { result } = renderHook(() => useTimetable('BAT142'), { wrapper: wrapper(qc) });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});
