/**
 * Partner Topics API Client Tests
 * Story 8.2: Topic Suggestions & Voting — AC1–6
 *
 * Tests for all topic API functions:
 * - getTopics: List all topic suggestions
 * - suggestTopic: Submit a new topic suggestion
 * - castVote: Vote on a topic
 * - removeVote: Remove vote from a topic
 * - updateTopicStatus: Update topic status (ORGANIZER)
 */

import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import apiClient from '@/services/api/apiClient';
import {
  getTopics,
  suggestTopic,
  castVote,
  removeVote,
  updateTopicStatus,
  type TopicDTO,
  type TopicSuggestionRequest,
  type TopicStatusUpdateRequest,
} from './partnerTopicsApi';

describe('Partner Topics API Client - Story 8.2', () => {
  beforeEach(() => {
    vi.spyOn(apiClient, 'get').mockResolvedValue({ data: [] });
    vi.spyOn(apiClient, 'post').mockResolvedValue({ data: {} });
    vi.spyOn(apiClient, 'patch').mockResolvedValue({ data: {} });
    vi.spyOn(apiClient, 'delete').mockResolvedValue({ data: undefined });
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  const mockTopic: TopicDTO = {
    id: 'topic-1',
    title: 'Cloud Architecture Patterns',
    description: 'Discussion about modern cloud patterns',
    suggestedByCompany: 'Acme Corp',
    voteCount: 5,
    currentPartnerHasVoted: false,
    status: 'PROPOSED',
    plannedEvent: null,
    createdAt: '2026-01-15T10:00:00Z',
  };

  describe('getTopics', () => {
    it('should_callGetEndpoint_when_getTopicsInvoked', async () => {
      const topics = [mockTopic];
      vi.mocked(apiClient.get).mockResolvedValue({ data: topics });

      const result = await getTopics();

      expect(apiClient.get).toHaveBeenCalledWith('/partners/topics');
      expect(result).toEqual(topics);
    });

    it('should_returnEmptyArray_when_noTopicsExist', async () => {
      vi.mocked(apiClient.get).mockResolvedValue({ data: [] });

      const result = await getTopics();

      expect(result).toEqual([]);
    });

    it('should_propagateError_when_apiCallFails', async () => {
      vi.mocked(apiClient.get).mockRejectedValue(new Error('Network Error'));

      await expect(getTopics()).rejects.toThrow('Network Error');
    });
  });

  describe('suggestTopic', () => {
    it('should_callPostEndpoint_when_suggestTopicInvoked', async () => {
      const request: TopicSuggestionRequest = {
        title: 'New Topic',
        description: 'A great topic for discussion',
      };
      vi.mocked(apiClient.post).mockResolvedValue({ data: mockTopic });

      const result = await suggestTopic(request);

      expect(apiClient.post).toHaveBeenCalledWith('/partners/topics', request);
      expect(result).toEqual(mockTopic);
    });

    it('should_handleNullDescription_when_noDescriptionProvided', async () => {
      const request: TopicSuggestionRequest = {
        title: 'Topic without description',
      };
      vi.mocked(apiClient.post).mockResolvedValue({ data: { ...mockTopic, description: null } });

      const result = await suggestTopic(request);

      expect(apiClient.post).toHaveBeenCalledWith('/partners/topics', request);
      expect(result.description).toBeNull();
    });

    it('should_propagateError_when_suggestTopicFails', async () => {
      vi.mocked(apiClient.post).mockRejectedValue(new Error('Forbidden'));

      await expect(suggestTopic({ title: 'x' })).rejects.toThrow('Forbidden');
    });
  });

  describe('castVote', () => {
    it('should_callPostEndpoint_when_castVoteInvoked', async () => {
      vi.mocked(apiClient.post).mockResolvedValue({ data: undefined });

      await castVote('topic-1');

      expect(apiClient.post).toHaveBeenCalledWith('/partners/topics/topic-1/vote');
    });

    it('should_returnVoid_when_voteSuccessful', async () => {
      vi.mocked(apiClient.post).mockResolvedValue({ data: undefined });

      const result = await castVote('topic-1');

      expect(result).toBeUndefined();
    });

    it('should_propagateError_when_castVoteFails', async () => {
      vi.mocked(apiClient.post).mockRejectedValue(new Error('Already voted'));

      await expect(castVote('topic-1')).rejects.toThrow('Already voted');
    });
  });

  describe('removeVote', () => {
    it('should_callDeleteEndpoint_when_removeVoteInvoked', async () => {
      vi.mocked(apiClient.delete).mockResolvedValue({ data: undefined });

      await removeVote('topic-1');

      expect(apiClient.delete).toHaveBeenCalledWith('/partners/topics/topic-1/vote');
    });

    it('should_returnVoid_when_removeVoteSuccessful', async () => {
      vi.mocked(apiClient.delete).mockResolvedValue({ data: undefined });

      const result = await removeVote('topic-1');

      expect(result).toBeUndefined();
    });

    it('should_propagateError_when_removeVoteFails', async () => {
      vi.mocked(apiClient.delete).mockRejectedValue(new Error('Not found'));

      await expect(removeVote('topic-1')).rejects.toThrow('Not found');
    });
  });

  describe('updateTopicStatus', () => {
    it('should_callPatchEndpoint_when_updateTopicStatusInvoked', async () => {
      const request: TopicStatusUpdateRequest = {
        status: 'SELECTED',
        plannedEvent: 'BAT-42',
      };
      const updated = { ...mockTopic, status: 'SELECTED' as const, plannedEvent: 'BAT-42' };
      vi.mocked(apiClient.patch).mockResolvedValue({ data: updated });

      const result = await updateTopicStatus('topic-1', request);

      expect(apiClient.patch).toHaveBeenCalledWith('/partners/topics/topic-1/status', request);
      expect(result).toEqual(updated);
    });

    it('should_declineTopic_when_statusSetToDeclined', async () => {
      const request: TopicStatusUpdateRequest = { status: 'DECLINED' };
      const declined = { ...mockTopic, status: 'DECLINED' as const };
      vi.mocked(apiClient.patch).mockResolvedValue({ data: declined });

      const result = await updateTopicStatus('topic-1', request);

      expect(result.status).toBe('DECLINED');
    });

    it('should_propagateError_when_updateTopicStatusFails', async () => {
      vi.mocked(apiClient.patch).mockRejectedValue(new Error('Unauthorized'));

      await expect(
        updateTopicStatus('topic-1', { status: 'SELECTED' })
      ).rejects.toThrow('Unauthorized');
    });
  });
});
