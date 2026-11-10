/**
 * useTopics Hook Tests (Story 5.2)
 *
 * One file for the hooks (the former useTopics.test.ts mocked the same service a second time).
 * The hooks run with the real topicService and apiClient against the MSW harness (Epic 17 test
 * fidelity): every request is checked against topics-api, and each test answers it with a typed
 * override or reads what went over the wire. A pending answer is a handler that waits for a
 * promise the test settles itself, so no answer arrives after the test has ended.
 */

import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import { HttpResponse } from 'msw';
import { isAxiosError } from 'axios';
import { api } from '@/test/msw/api';
import {
  useTopics,
  useTopic,
  useSimilarTopics,
  useTopicUsageHistory,
  useCreateTopic,
  useSelectTopicForEvent,
  topicKeys,
} from './useTopics';
import type { Topic, TopicListResponse, TopicUsageHistory } from '@/types/topic.types';
import { aTopic, aTopicUsage } from '@/test/fixtures';

const LIST = 'topics-api/listTopics';

const topic: Topic = {
  ...aTopic(),
  topicCode: 'cloud-native-architecture',
  title: 'Cloud Native Architecture',
  description: 'Modern cloud patterns',
  category: 'technical',
  stalenessScore: 85,
  usageCount: 3,
  lastUsedDate: '2024-01-15T00:00:00Z',
  createdDate: '2023-01-01T00:00:00Z',
  active: true,
};

const topicList: TopicListResponse = {
  data: [topic],
  pagination: { page: 1, limit: 20, totalItems: 1, totalPages: 1, hasNext: false, hasPrev: false },
};

const problem = (status: number, message: string) =>
  HttpResponse.json(
    { timestamp: '2026-11-09T08:00:00Z', status, error: 'Error', message },
    { status }
  );

const statusOf = (error: unknown) => (isAxiosError(error) ? error.response?.status : undefined);

describe('useTopics hooks', () => {
  let queryClient: QueryClient;

  const wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );

  beforeEach(() => {
    queryClient = new QueryClient({
      defaultOptions: {
        queries: { retry: false, gcTime: 0 },
        mutations: { retry: false },
      },
    });
  });

  afterEach(() => {
    queryClient.clear();
  });

  describe('topicKeys', () => {
    it('should generate correct query keys for all operations', () => {
      expect(topicKeys.all).toEqual(['topics']);
      expect(topicKeys.lists()).toEqual(['topics', 'list']);
      expect(topicKeys.list({ category: 'technical' })).toEqual([
        'topics',
        'list',
        { category: 'technical' },
      ]);
      expect(topicKeys.details()).toEqual(['topics', 'detail']);
      expect(topicKeys.detail('topic-123')).toEqual(['topics', 'detail', 'topic-123']);
      expect(topicKeys.similar('topic-123')).toEqual(['topics', 'similar', 'topic-123']);
    });
  });

  describe('useTopics', () => {
    it('should fetch topics without filters', async () => {
      api.on(LIST, () => topicList);

      const { result } = renderHook(() => useTopics(), { wrapper });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));
      expect(result.current.data).toEqual(topicList);
      expect([...api.calls(LIST)[0].query.keys()]).toEqual([]);
    });

    it('should send the filters as the spec names them', async () => {
      api.on(LIST, () => topicList);

      const { result } = renderHook(
        () => useTopics({ category: 'technical', sort: '-stalenessScore', page: 2, limit: 50 }),
        { wrapper }
      );

      await waitFor(() => expect(result.current.isSuccess).toBe(true));
      const query = api.calls(LIST)[0].query;
      expect(JSON.parse(query.get('filter') ?? '{}')).toEqual({ category: 'technical' });
      expect(query.get('sort')).toBe('-stalenessScore');
      expect(query.get('page')).toBe('2');
      expect(query.get('limit')).toBe('50');
    });

    it('should be loading until the answer arrives', async () => {
      let answer: () => void = () => {};
      const answered = new Promise<void>((resolve) => {
        answer = resolve;
      });
      api.on(LIST, async () => {
        await answered;
        return topicList;
      });

      const { result } = renderHook(() => useTopics(), { wrapper });

      expect(result.current.isLoading).toBe(true);
      expect(result.current.data).toBeUndefined();

      answer();
      await waitFor(() => expect(result.current.isSuccess).toBe(true));
      expect(result.current.isLoading).toBe(false);
      expect(result.current.data).toEqual(topicList);
    });

    it('should report a server error', async () => {
      api.on(LIST, () => problem(500, 'Network failure'));

      const { result } = renderHook(() => useTopics(), { wrapper });

      await waitFor(() => expect(result.current.isError).toBe(true));
      expect(statusOf(result.current.error)).toBe(500);
      expect(result.current.data).toBeUndefined();
    });

    it('should serve a second reader from the cache (5 minute stale time)', async () => {
      api.on(LIST, () => topicList);

      const { result } = renderHook(() => useTopics(), { wrapper });
      await waitFor(() => expect(result.current.isSuccess).toBe(true));

      const { result: second } = renderHook(() => useTopics(), { wrapper });

      expect(second.current.data).toEqual(topicList);
      expect(api.calls(LIST)).toHaveLength(1);
    });
  });

  describe('useTopic', () => {
    it('should fetch a topic by its code without includes', async () => {
      api.on('getTopicByCode', () => topic);

      const { result } = renderHook(() => useTopic(topic.topicCode), { wrapper });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));
      expect(result.current.data).toEqual(topic);
      const [call] = api.calls('getTopicByCode');
      expect(call.params.topicCode).toBe(topic.topicCode);
      expect(call.query.has('include')).toBe(false);
    });

    it('should send the include parameter', async () => {
      const withSimilarity: Topic = {
        ...topic,
        similarityScores: [{ topicCode: 'cloud-security', score: 0.75 }],
      };
      api.on('getTopicByCode', () => withSimilarity);

      const { result } = renderHook(() => useTopic(topic.topicCode, 'similarity,history'), {
        wrapper,
      });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));
      expect(api.calls('getTopicByCode')[0].query.get('include')).toBe('similarity,history');
      expect(result.current.data?.similarityScores).toEqual(withSimilarity.similarityScores);
    });

    it('should not fetch while the code is empty, and fetch once it is set', async () => {
      api.on('getTopicByCode', () => topic);

      const { result, rerender } = renderHook(({ code }) => useTopic(code), {
        wrapper,
        initialProps: { code: '' },
      });

      expect(result.current.isFetching).toBe(false);
      expect(api.calls('getTopicByCode')).toHaveLength(0);

      rerender({ code: topic.topicCode });
      await waitFor(() => expect(result.current.isSuccess).toBe(true));
      expect(api.calls('getTopicByCode')).toHaveLength(1);
    });

    it('should report 404 for a topic that does not exist', async () => {
      api.on('getTopicByCode', () => problem(404, 'Topic not found'));

      const { result } = renderHook(() => useTopic('non-existent'), { wrapper });

      await waitFor(() => expect(result.current.isError).toBe(true));
      expect(statusOf(result.current.error)).toBe(404);
    });
  });

  describe('useSimilarTopics', () => {
    const similar: Topic[] = [
      {
        ...topic,
        topicCode: 'cloud-native-design-patterns',
        title: 'Cloud Native Design Patterns',
        stalenessScore: 90,
      },
    ];

    it('should fetch similar topics for a code', async () => {
      api.on('getSimilarTopics', () => similar);

      const { result } = renderHook(() => useSimilarTopics(topic.topicCode), { wrapper });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));
      expect(result.current.data).toEqual(similar);
      expect(api.calls('getSimilarTopics')[0].params.topicCode).toBe(topic.topicCode);
    });

    it('should not fetch while the code is empty, and fetch once it is set', async () => {
      api.on('getSimilarTopics', () => similar);

      const { result, rerender } = renderHook(({ code }) => useSimilarTopics(code), {
        wrapper,
        initialProps: { code: '' },
      });

      expect(result.current.isFetching).toBe(false);
      expect(api.calls('getSimilarTopics')).toHaveLength(0);

      rerender({ code: topic.topicCode });
      await waitFor(() => expect(result.current.isSuccess).toBe(true));
      expect(api.calls('getSimilarTopics')).toHaveLength(1);
    });

    it('should serve a second reader from the cache (1 hour stale time)', async () => {
      api.on('getSimilarTopics', () => similar);

      const { result } = renderHook(() => useSimilarTopics(topic.topicCode), { wrapper });
      await waitFor(() => expect(result.current.isSuccess).toBe(true));

      const { result: second } = renderHook(() => useSimilarTopics(topic.topicCode), { wrapper });

      expect(second.current.data).toEqual(similar);
      expect(api.calls('getSimilarTopics')).toHaveLength(1);
    });

    it('should return an empty list when nothing is similar', async () => {
      api.on('getSimilarTopics', () => []);

      const { result } = renderHook(() => useSimilarTopics('topic-unique'), { wrapper });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));
      expect(result.current.data).toEqual([]);
    });
  });

  describe('useTopicUsageHistory', () => {
    const history: TopicUsageHistory[] = [
      aTopicUsage({
        eventNumber: 58,
        eventDate: '2025-11-14T16:00:00Z',
        usedDate: '2025-11-14',
        attendance: 182,
      }),
      aTopicUsage({
        eventNumber: 41,
        eventDate: '2017-05-05T16:00:00Z',
        usedDate: '2017-05-05',
        attendance: null,
      }),
    ];

    it('should fetch the usage history of a code', async () => {
      api.on('getTopicUsageHistory', () => history);

      const { result } = renderHook(() => useTopicUsageHistory(topic.topicCode), { wrapper });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));
      expect(result.current.data).toEqual(history);
      expect(api.calls('getTopicUsageHistory')[0].params.topicCode).toBe(topic.topicCode);
    });

    it('should not fetch while the code is empty, and fetch once it is set', async () => {
      api.on('getTopicUsageHistory', () => history);

      const { result, rerender } = renderHook(({ code }) => useTopicUsageHistory(code), {
        wrapper,
        initialProps: { code: '' },
      });

      expect(result.current.isFetching).toBe(false);
      expect(api.calls('getTopicUsageHistory')).toHaveLength(0);

      rerender({ code: topic.topicCode });
      await waitFor(() => expect(result.current.isSuccess).toBe(true));
      expect(api.calls('getTopicUsageHistory')).toHaveLength(1);
    });
  });

  describe('useCreateTopic', () => {
    const created: Topic = {
      ...aTopic(),
      topicCode: 'ai-powered-devops',
      title: 'AI-Powered DevOps',
      description: 'Exploring AI in DevOps',
      category: 'technical',
      stalenessScore: 100,
      usageCount: 0,
      createdDate: '2025-12-13T00:00:00Z',
      lastUsedDate: null,
      active: true,
    };

    it('should create a topic with the request body', async () => {
      api.on('createTopic', () => created, { status: 201 });

      const { result } = renderHook(() => useCreateTopic(), { wrapper });
      const request = {
        title: 'AI-Powered DevOps',
        description: 'Exploring AI in DevOps',
        category: 'technical',
      };

      result.current.mutate(request);

      await waitFor(() => expect(result.current.isSuccess).toBe(true));
      expect(api.calls('createTopic')[0].body).toEqual(request);
      expect(result.current.data).toEqual(created);
    });

    it('should refetch the topic lists after a creation', async () => {
      api.on(LIST, () => topicList);
      api.on('createTopic', () => created, { status: 201 });

      const { result } = renderHook(() => ({ list: useTopics(), create: useCreateTopic() }), {
        wrapper,
      });
      await waitFor(() => expect(result.current.list.isSuccess).toBe(true));
      expect(api.calls(LIST)).toHaveLength(1);

      result.current.create.mutate({ title: 'New Topic', category: 'technical' });

      await waitFor(() => expect(result.current.create.isSuccess).toBe(true));
      await waitFor(() => expect(api.calls(LIST)).toHaveLength(2));
    });

    it('should report a validation error', async () => {
      api.on('createTopic', () => problem(400, 'Validation failed: title is required'));

      const { result } = renderHook(() => useCreateTopic(), { wrapper });

      result.current.mutate({ title: '', description: 'Invalid', category: 'technical' });

      await waitFor(() => expect(result.current.isError).toBe(true));
      expect(statusOf(result.current.error)).toBe(400);
    });
  });

  describe('useSelectTopicForEvent', () => {
    const selected = {
      eventCode: 'BATbern56',
      topicCode: 'mobile-iot',
      workflowState: 'TOPIC_SELECTION',
      message: 'Topic selected',
    };

    it('should select the topic for the event', async () => {
      api.on('selectTopicForEvent', () => selected);

      const { result } = renderHook(() => useSelectTopicForEvent(), { wrapper });

      result.current.mutate({ eventCode: 'BATbern56', request: { topicCode: 'mobile-iot' } });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));
      const [call] = api.calls('selectTopicForEvent');
      expect(call.params.eventCode).toBe('BATbern56');
      expect(call.body).toEqual({ topicCode: 'mobile-iot' });
    });

    it('should send the justification override', async () => {
      api.on('selectTopicForEvent', () => selected);

      const { result } = renderHook(() => useSelectTopicForEvent(), { wrapper });
      const request = {
        topicCode: 'mobile-iot',
        justification: 'Partner explicitly requested despite high similarity',
      };

      result.current.mutate({ eventCode: 'BATbern56', request });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));
      expect(api.calls('selectTopicForEvent')[0].body).toEqual(request);
    });

    it('should invalidate the event and topic caches after the selection', async () => {
      api.on('selectTopicForEvent', () => selected);
      const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');

      const { result } = renderHook(() => useSelectTopicForEvent(), { wrapper });

      result.current.mutate({ eventCode: 'BATbern56', request: { topicCode: 'mobile-iot' } });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));
      expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['event', 'BATbern56'] });
      expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['events'] });
      expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: topicKeys.detail('mobile-iot') });
    });

    it('should report a refused workflow state', async () => {
      api.on('selectTopicForEvent', () =>
        problem(400, 'Invalid workflow state: Event must be in CREATED state')
      );

      const { result } = renderHook(() => useSelectTopicForEvent(), { wrapper });

      result.current.mutate({ eventCode: 'BATbern56', request: { topicCode: 'mobile-iot' } });

      await waitFor(() => expect(result.current.isError).toBe(true));
      expect(statusOf(result.current.error)).toBe(400);
    });

    it('should report an event that does not exist', async () => {
      api.on('selectTopicForEvent', () => problem(404, 'Event not found: BATbern999'));

      const { result } = renderHook(() => useSelectTopicForEvent(), { wrapper });

      result.current.mutate({ eventCode: 'BATbern999', request: { topicCode: 'mobile-iot' } });

      await waitFor(() => expect(result.current.isError).toBe(true));
      expect(statusOf(result.current.error)).toBe(404);
    });
  });
});
