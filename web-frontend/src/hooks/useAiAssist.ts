import { useMutation } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import apiClient from '@/services/api/apiClient';

interface DescriptionRequest {
  topicTitle: string;
  topicCategory: string;
}
interface DescriptionResponse {
  description: string;
}
interface ThemeImageRequest {
  topicTitle: string;
  topicCategory: string;
}
interface ThemeImageResponse {
  imageUrl: string;
  s3Key: string;
}
interface AbstractRequest {
  abstract: string;
  speakerName?: string;
}
interface AbstractResponse {
  qualityScore: number;
  suggestion: string;
  improvedAbstract: string;
  keyThemes: string[];
}

export function useAiGenerateDescription(eventCode: string) {
  const { t } = useTranslation('organizer');

  return useMutation<DescriptionResponse, Error, DescriptionRequest>({
    mutationFn: async (req) => {
      try {
        const response = await apiClient.post<DescriptionResponse>(
          `/events/${eventCode}/ai/description`,
          req
        );
        return response.data;
      } catch (err: unknown) {
        const axiosErr = err as { response?: { status?: number } };
        if (axiosErr?.response?.status === 503) throw new Error(t('aiAssist.disabled'));
        throw new Error(t('aiAssist.error'));
      }
    },
  });
}

export function useAiGenerateThemeImage(eventCode: string) {
  const { t } = useTranslation('organizer');

  return useMutation<ThemeImageResponse, Error, ThemeImageRequest>({
    mutationFn: async (req) => {
      try {
        const response = await apiClient.post<ThemeImageResponse>(
          `/events/${eventCode}/ai/theme-image`,
          req
        );
        return response.data;
      } catch {
        throw new Error(t('aiAssist.error'));
      }
    },
  });
}

export function useAiAnalyzeAbstract(speakerId: string) {
  const { t } = useTranslation('organizer');

  return useMutation<AbstractResponse, Error, AbstractRequest>({
    mutationFn: async (req) => {
      try {
        const response = await apiClient.post<AbstractResponse>(
          `/speakers/${speakerId}/ai/analyze-abstract`,
          req
        );
        return response.data;
      } catch {
        throw new Error(t('aiAssist.error'));
      }
    },
  });
}
