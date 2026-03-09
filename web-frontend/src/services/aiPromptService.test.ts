/**
 * aiPromptService Tests
 *
 * Coverage for AI Prompt Management Service:
 * - listAiPrompts: GET /ai-prompts
 * - getAiPrompt: GET /ai-prompts/:key
 * - updateAiPrompt: PUT /ai-prompts/:key
 * - resetAiPrompt: POST /ai-prompts/:key/reset
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { listAiPrompts, getAiPrompt, updateAiPrompt, resetAiPrompt } from './aiPromptService';

vi.mock('@/services/api/apiClient', () => ({
  default: {
    get: vi.fn(),
    put: vi.fn(),
    post: vi.fn(),
  },
}));

import apiClient from '@/services/api/apiClient';

const mockGet = vi.mocked(apiClient.get);
const mockPut = vi.mocked(apiClient.put);
const mockPost = vi.mocked(apiClient.post);

const PROMPT_DESCRIPTION = {
  promptKey: 'event_description',
  promptText: 'Describe the event in detail.',
};
const PROMPT_THEME = { promptKey: 'theme_image', promptText: 'Generate a theme image.' };
const PROMPT_QUALITY = { promptKey: 'abstract_quality', promptText: 'Review abstract quality.' };

describe('aiPromptService', () => {
  beforeEach(() => vi.clearAllMocks());

  describe('listAiPrompts', () => {
    it('should GET /ai-prompts and return array of prompts', async () => {
      const prompts = [PROMPT_DESCRIPTION, PROMPT_THEME, PROMPT_QUALITY];
      mockGet.mockResolvedValue({ data: prompts });

      const result = await listAiPrompts();

      expect(mockGet).toHaveBeenCalledWith('/ai-prompts');
      expect(result).toEqual(prompts);
    });

    it('should return empty array when no prompts exist', async () => {
      mockGet.mockResolvedValue({ data: [] });

      const result = await listAiPrompts();

      expect(result).toEqual([]);
    });

    it('should propagate errors from apiClient', async () => {
      mockGet.mockRejectedValue(new Error('Forbidden'));

      await expect(listAiPrompts()).rejects.toThrow('Forbidden');
    });
  });

  describe('getAiPrompt', () => {
    it('should GET /ai-prompts/:key and return single prompt', async () => {
      mockGet.mockResolvedValue({ data: PROMPT_DESCRIPTION });

      const result = await getAiPrompt('event_description');

      expect(mockGet).toHaveBeenCalledWith('/ai-prompts/event_description');
      expect(result).toEqual(PROMPT_DESCRIPTION);
    });

    it('should construct correct URL for theme_image key', async () => {
      mockGet.mockResolvedValue({ data: PROMPT_THEME });

      await getAiPrompt('theme_image');

      expect(mockGet).toHaveBeenCalledWith('/ai-prompts/theme_image');
    });

    it('should propagate 404 errors', async () => {
      mockGet.mockRejectedValue(new Error('Not found'));

      await expect(getAiPrompt('nonexistent_key')).rejects.toThrow('Not found');
    });
  });

  describe('updateAiPrompt', () => {
    it('should PUT /ai-prompts/:key with promptText body', async () => {
      const updated = { ...PROMPT_DESCRIPTION, promptText: 'Updated description text.' };
      mockPut.mockResolvedValue({ data: updated });

      const result = await updateAiPrompt('event_description', 'Updated description text.');

      expect(mockPut).toHaveBeenCalledWith('/ai-prompts/event_description', {
        promptText: 'Updated description text.',
      });
      expect(result).toEqual(updated);
    });

    it('should pass the exact promptText to the request body', async () => {
      const longText = 'A'.repeat(500);
      mockPut.mockResolvedValue({ data: { promptKey: 'theme_image', promptText: longText } });

      await updateAiPrompt('theme_image', longText);

      expect(mockPut).toHaveBeenCalledWith('/ai-prompts/theme_image', { promptText: longText });
    });

    it('should propagate validation errors', async () => {
      mockPut.mockRejectedValue(new Error('Invalid prompt text'));

      await expect(updateAiPrompt('event_description', '')).rejects.toThrow('Invalid prompt text');
    });
  });

  describe('resetAiPrompt', () => {
    it('should POST /ai-prompts/:key/reset and return reset prompt', async () => {
      mockPost.mockResolvedValue({ data: PROMPT_DESCRIPTION });

      const result = await resetAiPrompt('event_description');

      expect(mockPost).toHaveBeenCalledWith('/ai-prompts/event_description/reset');
      expect(result).toEqual(PROMPT_DESCRIPTION);
    });

    it('should construct correct URL for abstract_quality key', async () => {
      mockPost.mockResolvedValue({ data: PROMPT_QUALITY });

      await resetAiPrompt('abstract_quality');

      expect(mockPost).toHaveBeenCalledWith('/ai-prompts/abstract_quality/reset');
    });

    it('should propagate server errors', async () => {
      mockPost.mockRejectedValue(new Error('Internal server error'));

      await expect(resetAiPrompt('event_description')).rejects.toThrow('Internal server error');
    });
  });
});
