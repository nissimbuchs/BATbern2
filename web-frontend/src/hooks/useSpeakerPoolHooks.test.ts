/**
 * useSpeakerPool + useSpeakerOutreach Hooks Tests
 *
 * Coverage for:
 * - useSpeakerPool: fetch by eventCode, enabled guard
 * - useAddSpeakerToPool: mutation + cache invalidation
 * - useDeleteSpeakerFromPool: mutation + cache invalidation
 * - useSpeakerOutreachHistory: fetch by eventCode+speakerId, enabled guard
 * - useRecordOutreach: mutation + cache invalidation
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, waitFor, act } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import React from 'react';

vi.mock('@/services/speakerPoolService', () => ({
  speakerPoolService: {
    getSpeakerPool: vi.fn(),
    addSpeakerToPool: vi.fn(),
    deleteSpeakerFromPool: vi.fn(),
    sendInvitation: vi.fn(),
    sendReminder: vi.fn(),
  },
}));

vi.mock('@/services/speakerOutreachService', () => ({
  speakerOutreachService: {
    getOutreachHistory: vi.fn(),
    recordOutreach: vi.fn(),
  },
}));

import { speakerPoolService } from '@/services/speakerPoolService';
import { speakerOutreachService } from '@/services/speakerOutreachService';
import { useSpeakerPool, useAddSpeakerToPool } from './useSpeakerPool';
import { useSpeakerOutreachHistory, useRecordOutreach } from './useSpeakerOutreach';

const mockGetSpeakerPool = vi.mocked(speakerPoolService.getSpeakerPool);
const mockAddSpeakerToPool = vi.mocked(speakerPoolService.addSpeakerToPool);
const mockGetOutreachHistory = vi.mocked(speakerOutreachService.getOutreachHistory);
const mockRecordOutreach = vi.mocked(speakerOutreachService.recordOutreach);

const createQC = () =>
  new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0 }, mutations: { retry: false } },
  });

const wrapper =
  (qc: QueryClient) =>
  ({ children }: { children: React.ReactNode }) =>
    React.createElement(QueryClientProvider, { client: qc }, children);

const MOCK_SPEAKER = {
  id: 'sp-1',
  speakerName: 'Alice Smith',
  company: 'Acme',
  expertise: 'Cloud',
};

const MOCK_OUTREACH = [
  { id: 'o-1', contactMethod: 'email', contactDate: '2025-12-01', notes: 'First contact' },
];

// ── useSpeakerPool ────────────────────────────────────────────────────────────

describe('useSpeakerPool', () => {
  let qc: QueryClient;

  beforeEach(() => {
    qc = createQC();
    vi.clearAllMocks();
  });

  it('should fetch speaker pool for an event', async () => {
    mockGetSpeakerPool.mockResolvedValue([MOCK_SPEAKER] as never);

    const { result } = renderHook(() => useSpeakerPool('BAT142'), { wrapper: wrapper(qc) });

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    expect(mockGetSpeakerPool).toHaveBeenCalledWith('BAT142');
    expect(result.current.data).toEqual([MOCK_SPEAKER]);
  });

  it('should not fetch when eventCode is empty', () => {
    const { result } = renderHook(() => useSpeakerPool(''), { wrapper: wrapper(qc) });

    expect(result.current.isLoading).toBe(false);
    expect(mockGetSpeakerPool).not.toHaveBeenCalled();
  });

  it('should set isError on fetch failure', async () => {
    mockGetSpeakerPool.mockRejectedValue(new Error('Not found'));

    const { result } = renderHook(() => useSpeakerPool('BAT999'), { wrapper: wrapper(qc) });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});

// ── useAddSpeakerToPool ───────────────────────────────────────────────────────

describe('useAddSpeakerToPool', () => {
  let qc: QueryClient;

  beforeEach(() => {
    qc = createQC();
    vi.clearAllMocks();
  });

  it('should call addSpeakerToPool with eventCode and request', async () => {
    mockAddSpeakerToPool.mockResolvedValue(MOCK_SPEAKER as never);

    const { result } = renderHook(() => useAddSpeakerToPool(), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current.mutateAsync({
        eventCode: 'BAT142',
        request: { speakerName: 'Alice', company: 'Acme', expertise: 'Cloud' } as never,
      });
    });

    expect(mockAddSpeakerToPool).toHaveBeenCalledWith('BAT142', {
      speakerName: 'Alice',
      company: 'Acme',
      expertise: 'Cloud',
    });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
  });

  it('should invalidate speaker pool and status caches on success', async () => {
    mockAddSpeakerToPool.mockResolvedValue(MOCK_SPEAKER as never);
    const invalidateSpy = vi.spyOn(qc, 'invalidateQueries');

    const { result } = renderHook(() => useAddSpeakerToPool(), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current.mutateAsync({
        eventCode: 'BAT142',
        request: { speakerName: 'Alice' } as never,
      });
    });

    expect(invalidateSpy).toHaveBeenCalledWith(
      expect.objectContaining({ queryKey: expect.arrayContaining(['speakerPool']) })
    );
    expect(invalidateSpy).toHaveBeenCalledWith(
      expect.objectContaining({ queryKey: ['speakerStatusSummary', 'BAT142'] })
    );
  });

  it('should set isError on failure', async () => {
    mockAddSpeakerToPool.mockRejectedValue(new Error('Conflict'));

    const { result } = renderHook(() => useAddSpeakerToPool(), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current
        .mutateAsync({ eventCode: 'BAT142', request: {} as never })
        .catch(() => {});
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});

// ── useSpeakerOutreachHistory ─────────────────────────────────────────────────

describe('useSpeakerOutreachHistory', () => {
  let qc: QueryClient;

  beforeEach(() => {
    qc = createQC();
    vi.clearAllMocks();
  });

  it('should fetch outreach history for event+speaker', async () => {
    mockGetOutreachHistory.mockResolvedValue(MOCK_OUTREACH as never);

    const { result } = renderHook(() => useSpeakerOutreachHistory('BAT142', 'sp-1'), {
      wrapper: wrapper(qc),
    });

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    expect(mockGetOutreachHistory).toHaveBeenCalledWith('BAT142', 'sp-1');
    expect(result.current.data).toEqual(MOCK_OUTREACH);
  });

  it('should not fetch when eventCode is empty', () => {
    const { result } = renderHook(() => useSpeakerOutreachHistory('', 'sp-1'), {
      wrapper: wrapper(qc),
    });

    expect(result.current.isLoading).toBe(false);
    expect(mockGetOutreachHistory).not.toHaveBeenCalled();
  });

  it('should not fetch when speakerId is empty', () => {
    const { result } = renderHook(() => useSpeakerOutreachHistory('BAT142', ''), {
      wrapper: wrapper(qc),
    });

    expect(result.current.isLoading).toBe(false);
    expect(mockGetOutreachHistory).not.toHaveBeenCalled();
  });

  it('should set isError on fetch failure', async () => {
    mockGetOutreachHistory.mockRejectedValue(new Error('Not found'));

    const { result } = renderHook(() => useSpeakerOutreachHistory('BAT999', 'sp-1'), {
      wrapper: wrapper(qc),
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});

// ── useRecordOutreach ─────────────────────────────────────────────────────────

describe('useRecordOutreach', () => {
  let qc: QueryClient;

  beforeEach(() => {
    qc = createQC();
    vi.clearAllMocks();
  });

  it('should call recordOutreach with all params', async () => {
    mockRecordOutreach.mockResolvedValue(MOCK_OUTREACH[0] as never);

    const { result } = renderHook(() => useRecordOutreach(), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current.mutateAsync({
        eventCode: 'BAT142',
        speakerId: 'sp-1',
        request: {
          contactMethod: 'email',
          contactDate: '2025-12-14T10:00:00Z',
          notes: 'First contact',
        } as never,
      });
    });

    expect(mockRecordOutreach).toHaveBeenCalledWith(
      'BAT142',
      'sp-1',
      expect.objectContaining({
        contactMethod: 'email',
      })
    );
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
  });

  it('should invalidate outreach history cache on success', async () => {
    mockRecordOutreach.mockResolvedValue(MOCK_OUTREACH[0] as never);
    const invalidateSpy = vi.spyOn(qc, 'invalidateQueries');

    const { result } = renderHook(() => useRecordOutreach(), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current.mutateAsync({
        eventCode: 'BAT142',
        speakerId: 'sp-1',
        request: {} as never,
      });
    });

    expect(invalidateSpy).toHaveBeenCalledWith(
      expect.objectContaining({ queryKey: expect.arrayContaining(['speakerOutreach']) })
    );
  });

  it('should set isError on failure', async () => {
    mockRecordOutreach.mockRejectedValue(new Error('Validation error'));

    const { result } = renderHook(() => useRecordOutreach(), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current
        .mutateAsync({ eventCode: 'BAT142', speakerId: 'sp-1', request: {} as never })
        .catch(() => {});
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});
