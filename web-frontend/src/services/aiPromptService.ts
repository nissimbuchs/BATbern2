/**
 * AI Prompt Management Service
 *
 * Provides CRUD access to the three organizer-editable OpenAI prompts:
 *   - event_description  — event description (chat model)
 *   - theme_image        — theme image (image model)
 *   - abstract_quality   — abstract quality review (chat model)
 *
 * CRITICAL: Always use this service — never call apiClient directly from components.
 */

import apiClient from '@/services/api/apiClient';
import type { components } from '@/types/generated/event-ai-api.types';

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

/**
 * Removes the organizer's override of a prompt, so its text is the default again
 * (`DELETE /ai-prompts/{promptKey}/override`, Story 4.10b; was `POST …/reset`).
 */
export const deleteAiPromptOverride = async (promptKey: string): Promise<AiPromptResponse> => {
  const response = await apiClient.delete<AiPromptResponse>(`/ai-prompts/${promptKey}/override`);
  return response.data;
};
