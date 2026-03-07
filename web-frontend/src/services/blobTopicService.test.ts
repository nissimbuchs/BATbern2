/**
 * blobTopicService Tests
 *
 * Coverage for:
 * - getSessionData: GET /events/{eventCode}/topic-session-data
 * - getSimilarity: POST /events/{eventCode}/topic-similarity with topic body
 * - acceptTopic: PATCH /events/{eventCode} with topicCode and topicSelectionNote
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';

vi.mock('@/services/api/apiClient', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
    patch: vi.fn(),
  },
}));

import apiClient from '@/services/api/apiClient';
import { blobTopicService } from './blobTopicService';

const mockGet = vi.mocked(apiClient.get);
const mockPost = vi.mocked(apiClient.post);
const mockPatch = vi.mocked(apiClient.patch);

beforeEach(() => {
  vi.clearAllMocks();
});

describe('blobTopicService.getSessionData', () => {
  it('should GET /events/{eventCode}/topic-session-data and return data', async () => {
    const sessionData = { topics: ['Cloud', 'AI'], currentTopic: 'Cloud' };
    mockGet.mockResolvedValue({ data: sessionData });

    const result = await blobTopicService.getSessionData('BAT142');

    expect(mockGet).toHaveBeenCalledWith('/events/BAT142/topic-session-data');
    expect(result).toEqual(sessionData);
  });

  it('should propagate errors from apiClient', async () => {
    mockGet.mockRejectedValue(new Error('Not found'));

    await expect(blobTopicService.getSessionData('BAT142')).rejects.toThrow('Not found');
  });
});

describe('blobTopicService.getSimilarity', () => {
  it('should POST /events/{eventCode}/topic-similarity with topic body and return data', async () => {
    const similarity = { score: 0.85, relatedTopics: ['Machine Learning'] };
    mockPost.mockResolvedValue({ data: similarity });

    const result = await blobTopicService.getSimilarity('BAT142', 'Artificial Intelligence');

    expect(mockPost).toHaveBeenCalledWith('/events/BAT142/topic-similarity', {
      topic: 'Artificial Intelligence',
    });
    expect(result).toEqual(similarity);
  });

  it('should propagate errors from apiClient', async () => {
    mockPost.mockRejectedValue(new Error('Service unavailable'));

    await expect(blobTopicService.getSimilarity('BAT142', 'Cloud')).rejects.toThrow(
      'Service unavailable'
    );
  });
});

describe('blobTopicService.acceptTopic', () => {
  it('should PATCH /events/{eventCode} with topicCode and topicSelectionNote', async () => {
    mockPatch.mockResolvedValue({});

    await blobTopicService.acceptTopic('BAT142', 'topic-cloud-native', 'Great fit for the event');

    expect(mockPatch).toHaveBeenCalledWith('/events/BAT142', {
      topicCode: 'topic-cloud-native',
      topicSelectionNote: 'Great fit for the event',
    });
  });

  it('should accept an empty note', async () => {
    mockPatch.mockResolvedValue({});

    await blobTopicService.acceptTopic('BAT142', 'topic-arch', '');

    expect(mockPatch).toHaveBeenCalledWith('/events/BAT142', {
      topicCode: 'topic-arch',
      topicSelectionNote: '',
    });
  });

  it('should propagate errors from apiClient', async () => {
    mockPatch.mockRejectedValue(new Error('Forbidden'));

    await expect(blobTopicService.acceptTopic('BAT142', 'topic-x', 'note')).rejects.toThrow(
      'Forbidden'
    );
  });
});
