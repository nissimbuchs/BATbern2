/**
 * blobTopicService Tests
 *
 * The real service and apiClient run against the MSW harness (Epic 17 test fidelity): every
 * request is checked against the specs and the assertions read what went over the wire.
 * getSessionData's composition of the partner topics is covered with the hook in
 * `components/BlobTopicSelector/useTopicSessionData.test.tsx`.
 *
 * - classifyTopics: POST /topics/classifications, split above the spec's maxItems
 * - getSimilarity: POST /events/{eventCode}/topic-similarity with topic body
 * - acceptTopic: PATCH /events/{eventCode} with topicCode and topicSelectionNote
 */

import { describe, it, expect } from 'vitest';
import { HttpResponse } from 'msw';
import { api } from '@/test/msw/api';
import { blobTopicService, MAX_CLASSIFY_TITLES } from './blobTopicService';

const problem = { error: 'X', message: 'boom', status: 500, timestamp: '2026-11-11T00:00:00Z' };

describe('blobTopicService.classifyTopics', () => {
  it('should POST the titles and return the classifications in order', async () => {
    const result = await blobTopicService.classifyTopics(['AI in Operations', 'Zero Trust']);

    expect(api.calls('classifyTopics')[0].body).toEqual({
      topics: ['AI in Operations', 'Zero Trust'],
    });
    // The spec example answers for exactly this request.
    expect(result.map((c) => [c.topic, c.cluster])).toEqual([
      ['AI in Operations', 'AI_ML'],
      ['Zero Trust', 'SECURITY'],
    ]);
  });

  it('should split a list longer than maxItems into several calls and keep the order', async () => {
    api.on('classifyTopics', ({ body }) => ({
      classifications: (body?.topics ?? []).map((topic) => ({
        topic,
        cluster: 'BUSINESS_OTHER',
        similarityScore: 0.5,
        relatedPastEventNumbers: [],
      })),
    }));
    const titles = Array.from({ length: MAX_CLASSIFY_TITLES + 1 }, (_, i) => `Topic ${i}`);

    const result = await blobTopicService.classifyTopics(titles);

    const calls = api.calls('classifyTopics');
    // The chunks go out concurrently, so their recorded order is not fixed; the result order is.
    expect(calls.map((c) => c.body?.topics.length).sort((a, b) => (a ?? 0) - (b ?? 0))).toEqual([
      1,
      MAX_CLASSIFY_TITLES,
    ]);
    expect(result.map((c) => c.topic)).toEqual(titles);
  });

  it('should propagate errors', async () => {
    api.on('classifyTopics', () => HttpResponse.json(problem, { status: 500 }));

    await expect(blobTopicService.classifyTopics(['Zero Trust'])).rejects.toBeTruthy();
  });
});

describe('blobTopicService.getSimilarity', () => {
  it('should POST /events/{eventCode}/topic-similarity with topic body and return data', async () => {
    const result = await blobTopicService.getSimilarity('BATbern142', 'Artificial Intelligence');

    const [call] = api.calls('getTopicSimilarity');
    expect(call.params.eventCode).toBe('BATbern142');
    expect(call.body).toEqual({ topic: 'Artificial Intelligence' });
    expect(result.cluster).toBe('AI_ML');
  });

  it('should propagate errors', async () => {
    api.on('getTopicSimilarity', () => HttpResponse.json(problem, { status: 500 }));

    await expect(blobTopicService.getSimilarity('BATbern142', 'Cloud')).rejects.toBeTruthy();
  });
});

describe('blobTopicService.acceptTopic', () => {
  it('should PATCH /events/{eventCode} with topicCode and topicSelectionNote', async () => {
    await blobTopicService.acceptTopic(
      'BATbern142',
      'topic-cloud-native',
      'Great fit for the event'
    );

    const [call] = api.calls('patchEvent');
    expect(call.params.eventCode).toBe('BATbern142');
    expect(call.body).toEqual({
      topicCode: 'topic-cloud-native',
      topicSelectionNote: 'Great fit for the event',
    });
  });

  it('should accept an empty note', async () => {
    await blobTopicService.acceptTopic('BATbern142', 'topic-arch', '');

    expect(api.calls('patchEvent')[0].body).toEqual({
      topicCode: 'topic-arch',
      topicSelectionNote: '',
    });
  });

  it('should propagate errors', async () => {
    api.on('patchEvent', () => HttpResponse.json(problem, { status: 403 }));

    await expect(
      blobTopicService.acceptTopic('BATbern142', 'topic-x', 'note')
    ).rejects.toBeTruthy();
  });
});
