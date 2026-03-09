/**
 * useTopics Hooks Tests
 *
 * Coverage for:
 * - useTopics: fetch with filters, always enabled
 * - useTopic: fetch by id, enabled guard
 * - useSimilarTopics: fetch similar, enabled guard
 * - useTopicUsageHistory: fetch usage history, enabled guard
 * - useCreateTopic: mutation + cache invalidation
 * - useOverrideStaleness: mutation + cache invalidation
 * - useSelectTopicForEvent: mutation + cache invalidation
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, waitFor, act } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import React from 'react';

vi.mock('@/services/topicService', () => ({
  topicService: {
    getTopics: vi.fn(),
    getTopicById: vi.fn(),
    getSimilarTopics: vi.fn(),
    getTopicUsageHistory: vi.fn(),
    createTopic: vi.fn(),
    overrideStaleness: vi.fn(),
    selectTopicForEvent: vi.fn(),
  },
}));

import { topicService } from '@/services/topicService';
import {
  useTopics,
  useTopic,
  useSimilarTopics,
  useTopicUsageHistory,
  useCreateTopic,
  useOverrideStaleness,
  useSelectTopicForEvent,
} from './useTopics';

const mockGetTopics = vi.mocked(topicService.getTopics);
const mockGetTopicById = vi.mocked(topicService.getTopicById);
const mockGetSimilarTopics = vi.mocked(topicService.getSimilarTopics);
const mockGetTopicUsageHistory = vi.mocked(topicService.getTopicUsageHistory);
const mockCreateTopic = vi.mocked(topicService.createTopic);
const mockOverrideStaleness = vi.mocked(topicService.overrideStaleness);
const mockSelectTopicForEvent = vi.mocked(topicService.selectTopicForEvent);

const createQC = () =>
  new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0 }, mutations: { retry: false } },
  });

const wrapper =
  (qc: QueryClient) =>
  ({ children }: { children: React.ReactNode }) =>
    React.createElement(QueryClientProvider, { client: qc }, children);

const MOCK_TOPIC = { id: 'topic-1', title: 'Cloud Native', category: 'Architecture' };
const MOCK_TOPIC_LIST = { data: [MOCK_TOPIC], total: 1, page: 1, limit: 20 };

// ── useTopics ─────────────────────────────────────────────────────────────────

describe('useTopics', () => {
  let qc: QueryClient;

  beforeEach(() => {
    qc = createQC();
    vi.clearAllMocks();
  });

  it('should fetch topics without filters', async () => {
    mockGetTopics.mockResolvedValue(MOCK_TOPIC_LIST as never);

    const { result } = renderHook(() => useTopics(), { wrapper: wrapper(qc) });

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    expect(mockGetTopics).toHaveBeenCalledWith(undefined);
    expect(result.current.data).toEqual(MOCK_TOPIC_LIST);
  });

  it('should fetch topics with filters', async () => {
    mockGetTopics.mockResolvedValue(MOCK_TOPIC_LIST as never);

    const filters = { category: 'Architecture', page: 1, limit: 10 };
    const { result } = renderHook(() => useTopics(filters), { wrapper: wrapper(qc) });

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    expect(mockGetTopics).toHaveBeenCalledWith(filters);
  });

  it('should set isError on fetch failure', async () => {
    mockGetTopics.mockRejectedValue(new Error('Service unavailable'));

    const { result } = renderHook(() => useTopics(), { wrapper: wrapper(qc) });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});

// ── useTopic ──────────────────────────────────────────────────────────────────

describe('useTopic', () => {
  let qc: QueryClient;

  beforeEach(() => {
    qc = createQC();
    vi.clearAllMocks();
  });

  it('should fetch topic by id', async () => {
    mockGetTopicById.mockResolvedValue(MOCK_TOPIC as never);

    const { result } = renderHook(() => useTopic('topic-1'), { wrapper: wrapper(qc) });

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    expect(mockGetTopicById).toHaveBeenCalledWith('topic-1', undefined);
    expect(result.current.data).toEqual(MOCK_TOPIC);
  });

  it('should fetch with include parameter', async () => {
    mockGetTopicById.mockResolvedValue(MOCK_TOPIC as never);

    const { result } = renderHook(() => useTopic('topic-1', 'similarity,history'), {
      wrapper: wrapper(qc),
    });

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    expect(mockGetTopicById).toHaveBeenCalledWith('topic-1', 'similarity,history');
  });

  it('should not fetch when id is empty', () => {
    const { result } = renderHook(() => useTopic(''), { wrapper: wrapper(qc) });

    expect(result.current.isLoading).toBe(false);
    expect(mockGetTopicById).not.toHaveBeenCalled();
  });
});

// ── useSimilarTopics ──────────────────────────────────────────────────────────

describe('useSimilarTopics', () => {
  let qc: QueryClient;

  beforeEach(() => {
    qc = createQC();
    vi.clearAllMocks();
  });

  it('should fetch similar topics by id', async () => {
    const similar = [{ id: 'topic-2', title: 'K8s', score: 0.85 }];
    mockGetSimilarTopics.mockResolvedValue(similar as never);

    const { result } = renderHook(() => useSimilarTopics('topic-1'), { wrapper: wrapper(qc) });

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    expect(mockGetSimilarTopics).toHaveBeenCalledWith('topic-1');
    expect(result.current.data).toEqual(similar);
  });

  it('should not fetch when id is empty', () => {
    const { result } = renderHook(() => useSimilarTopics(''), { wrapper: wrapper(qc) });

    expect(result.current.isLoading).toBe(false);
    expect(mockGetSimilarTopics).not.toHaveBeenCalled();
  });
});

// ── useTopicUsageHistory ──────────────────────────────────────────────────────

describe('useTopicUsageHistory', () => {
  let qc: QueryClient;

  beforeEach(() => {
    qc = createQC();
    vi.clearAllMocks();
  });

  it('should fetch usage history by id', async () => {
    const history = [{ eventCode: 'BAT142', usedAt: '2025-01-01' }];
    mockGetTopicUsageHistory.mockResolvedValue(history as never);

    const { result } = renderHook(() => useTopicUsageHistory('topic-1'), { wrapper: wrapper(qc) });

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    expect(mockGetTopicUsageHistory).toHaveBeenCalledWith('topic-1');
    expect(result.current.data).toEqual(history);
  });

  it('should not fetch when id is empty', () => {
    const { result } = renderHook(() => useTopicUsageHistory(''), { wrapper: wrapper(qc) });

    expect(result.current.isLoading).toBe(false);
    expect(mockGetTopicUsageHistory).not.toHaveBeenCalled();
  });
});

// ── useCreateTopic ────────────────────────────────────────────────────────────

describe('useCreateTopic', () => {
  let qc: QueryClient;

  beforeEach(() => {
    qc = createQC();
    vi.clearAllMocks();
  });

  it('should call createTopic with request data', async () => {
    mockCreateTopic.mockResolvedValue(MOCK_TOPIC as never);

    const { result } = renderHook(() => useCreateTopic(), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current.mutateAsync({
        title: 'Cloud Native',
        category: 'Architecture',
        description: 'Cloud patterns',
      } as never);
    });

    expect(mockCreateTopic).toHaveBeenCalledWith({
      title: 'Cloud Native',
      category: 'Architecture',
      description: 'Cloud patterns',
    });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
  });

  it('should invalidate topic lists on success', async () => {
    mockCreateTopic.mockResolvedValue(MOCK_TOPIC as never);
    const invalidateSpy = vi.spyOn(qc, 'invalidateQueries');

    const { result } = renderHook(() => useCreateTopic(), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current.mutateAsync({ title: 'New Topic' } as never);
    });

    expect(invalidateSpy).toHaveBeenCalledWith(
      expect.objectContaining({ queryKey: expect.arrayContaining(['topics']) })
    );
  });

  it('should set isError on failure', async () => {
    mockCreateTopic.mockRejectedValue(new Error('Validation failed'));

    const { result } = renderHook(() => useCreateTopic(), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current.mutateAsync({} as never).catch(() => {});
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});

// ── useOverrideStaleness ──────────────────────────────────────────────────────

describe('useOverrideStaleness', () => {
  let qc: QueryClient;

  beforeEach(() => {
    qc = createQC();
    vi.clearAllMocks();
  });

  it('should call overrideStaleness with id and request', async () => {
    mockOverrideStaleness.mockResolvedValue(MOCK_TOPIC as never);

    const { result } = renderHook(() => useOverrideStaleness(), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current.mutateAsync({
        id: 'topic-1',
        request: { stalenessScore: 80, justification: 'Confirmed fresh' } as never,
      });
    });

    expect(mockOverrideStaleness).toHaveBeenCalledWith('topic-1', {
      stalenessScore: 80,
      justification: 'Confirmed fresh',
    });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
  });

  it('should invalidate topic detail and lists on success', async () => {
    mockOverrideStaleness.mockResolvedValue(MOCK_TOPIC as never);
    const invalidateSpy = vi.spyOn(qc, 'invalidateQueries');

    const { result } = renderHook(() => useOverrideStaleness(), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current.mutateAsync({ id: 'topic-1', request: {} as never });
    });

    expect(invalidateSpy).toHaveBeenCalledTimes(2);
  });

  it('should set isError on failure', async () => {
    mockOverrideStaleness.mockRejectedValue(new Error('Forbidden'));

    const { result } = renderHook(() => useOverrideStaleness(), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current.mutateAsync({ id: 'topic-1', request: {} as never }).catch(() => {});
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});

// ── useSelectTopicForEvent ────────────────────────────────────────────────────

describe('useSelectTopicForEvent', () => {
  let qc: QueryClient;

  beforeEach(() => {
    qc = createQC();
    vi.clearAllMocks();
  });

  it('should call selectTopicForEvent with eventCode and request', async () => {
    mockSelectTopicForEvent.mockResolvedValue({ topicCode: 'cloud-native' } as never);

    const { result } = renderHook(() => useSelectTopicForEvent(), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current.mutateAsync({
        eventCode: 'BAT142',
        request: { topicCode: 'cloud-native', justification: 'Perfect fit' } as never,
      });
    });

    expect(mockSelectTopicForEvent).toHaveBeenCalledWith('BAT142', {
      topicCode: 'cloud-native',
      justification: 'Perfect fit',
    });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
  });

  it('should invalidate event, events, and topic detail on success', async () => {
    mockSelectTopicForEvent.mockResolvedValue({ topicCode: 'cloud-native' } as never);
    const invalidateSpy = vi.spyOn(qc, 'invalidateQueries');

    const { result } = renderHook(() => useSelectTopicForEvent(), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current.mutateAsync({
        eventCode: 'BAT142',
        request: { topicCode: 'cloud-native' } as never,
      });
    });

    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['event', 'BAT142'] });
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['events'] });
  });

  it('should set isError on failure', async () => {
    mockSelectTopicForEvent.mockRejectedValue(new Error('Not found'));

    const { result } = renderHook(() => useSelectTopicForEvent(), { wrapper: wrapper(qc) });

    await act(async () => {
      await result.current
        .mutateAsync({ eventCode: 'BAT999', request: {} as never })
        .catch(() => {});
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});
