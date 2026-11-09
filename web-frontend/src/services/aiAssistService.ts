/**
 * AI Assist Service
 *
 * The event and speaker AI endpoints of EMS (docs/api/event-ai-api.openapi.yml):
 *   - POST /events/{eventCode}/ai/description
 *   - POST /events/{eventCode}/ai/theme-image            (starts a job, 202)
 *   - GET  /events/{eventCode}/ai/theme-image/jobs/{jobId}
 *   - POST /events/{eventCode}/ai/theme-image/apply
 *   - POST /events/{eventCode}/speakers/{speakerId}/abstract-analysis
 *
 * Prompt administration (`/ai-prompts`) is a different resource: aiPromptService.
 * Errors are passed through unchanged; the hooks in useAiAssist map them to messages.
 */

import apiClient from '@/services/api/apiClient';
import type { components } from '@/types/generated/event-ai-api.types';

export type AiDescriptionResponse = components['schemas']['AiDescriptionResponse'];
export type AiThemeImageJob = components['schemas']['AiThemeImageJob'];
export type AiAbstractAnalysisResponse = components['schemas']['AbstractAnalysisResponse'];

export const generateEventDescription = async (
  eventCode: string
): Promise<AiDescriptionResponse> => {
  const response = await apiClient.post<AiDescriptionResponse>(
    `/events/${eventCode}/ai/description`
  );
  return response.data;
};

/** Starts a theme-image job; the optional seed goes into the query string as before. */
export const startThemeImageJob = async (
  eventCode: string,
  seed?: string
): Promise<AiThemeImageJob> => {
  const qs = seed ? `?seed=${encodeURIComponent(seed)}` : '';
  const response = await apiClient.post<AiThemeImageJob>(
    `/events/${eventCode}/ai/theme-image${qs}`
  );
  return response.data;
};

export const getThemeImageJob = async (
  eventCode: string,
  jobId: string
): Promise<AiThemeImageJob> => {
  const response = await apiClient.get<AiThemeImageJob>(
    `/events/${eventCode}/ai/theme-image/jobs/${jobId}`
  );
  return response.data;
};

export const applyThemeImage = async (eventCode: string, imageUrl: string): Promise<void> => {
  await apiClient.post(`/events/${eventCode}/ai/theme-image/apply`, { imageUrl });
};

/** Reviews the abstract of a speaker pool entry of this event (an entry of another event: 404). */
export const analyzeAbstract = async (
  eventCode: string,
  speakerPoolId: string
): Promise<AiAbstractAnalysisResponse> => {
  const response = await apiClient.post<AiAbstractAnalysisResponse>(
    `/events/${eventCode}/speakers/${speakerPoolId}/abstract-analysis`
  );
  return response.data;
};
