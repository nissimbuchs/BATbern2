/**
 * AI Prompt Management Service
 *
 * Provides CRUD access to the three organizer-editable OpenAI prompts:
 *   - event_description  — GPT-4o event description generation
 *   - theme_image        — DALL-E theme image generation
 *   - abstract_quality   — GPT-4o abstract quality review
 *
 * CRITICAL: Always use this service — never call apiClient directly from components.
 */

import apiClient from '@/services/api/apiClient';
import type { components } from '@/types/generated/events-api.types';

export type AiPromptResponse = components['schemas']['AiPromptResponse'];
export type UpdateAiPromptRequest = components['schemas']['UpdateAiPromptRequest'];

export const listAiPrompts = async (): Promise<AiPromptResponse[]> => {
  const response = await apiClient.get<AiPromptResponse[]>('/ai-prompts');
  return response.data;
};

export const getAiPrompt = async (promptKey: string): Promise<AiPromptResponse> => {
  const response = await apiClient.get<AiPromptResponse>(`/ai-prompts/${promptKey}`);
  return response.data;
};

export const updateAiPrompt = async (
  promptKey: string,
  promptText: string
): Promise<AiPromptResponse> => {
  const body: UpdateAiPromptRequest = { promptText };
  const response = await apiClient.put<AiPromptResponse>(`/ai-prompts/${promptKey}`, body);
  return response.data;
};

export const resetAiPrompt = async (promptKey: string): Promise<AiPromptResponse> => {
  const response = await apiClient.post<AiPromptResponse>(`/ai-prompts/${promptKey}/reset`);
  return response.data;
};
