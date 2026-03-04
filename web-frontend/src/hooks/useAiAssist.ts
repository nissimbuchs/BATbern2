import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import apiClient from '@/services/api/apiClient';

interface DescriptionRequest {
  topicTitle: string;
  topicCategory: string;
  eventTitle?: string;
  eventDate?: string;
}
interface DescriptionResponse {
  description: string;
}
interface ThemeImageRequest {
  topicTitle: string;
  topicCategory: string;
  eventTitle?: string;
  eventDescription?: string;
  seed?: string;
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
  noPromotionScore: number;
  noPromotionFeedback: string;
  lessonsLearnedScore: number;
  lessonsLearnedFeedback: string;
  wordCount: number;
  shortenedAbstract: string | null;
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
        const { seed, eventDescription, ...body } = req;
        const params = new URLSearchParams();
        if (seed) params.set('seed', seed);
        if (eventDescription) params.set('description', eventDescription);
        const qs = params.toString();
        const response = await apiClient.post<ThemeImageResponse>(
          `/events/${eventCode}/ai/theme-image${qs ? `?${qs}` : ''}`,
          body
        );
        return response.data;
      } catch {
        throw new Error(t('aiAssist.error'));
      }
    },
  });
}

export function useAiApplyThemeImage(eventCode: string) {
  const queryClient = useQueryClient();
  const { t } = useTranslation('organizer');

  return useMutation<void, Error, { imageUrl: string }>({
    mutationFn: async ({ imageUrl }) => {
      try {
        await apiClient.post(`/events/${eventCode}/ai/theme-image/apply`, { imageUrl });
      } catch {
        throw new Error(t('aiAssist.error'));
      }
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['event', eventCode] });
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
