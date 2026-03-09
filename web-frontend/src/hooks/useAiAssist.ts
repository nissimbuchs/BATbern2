import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import apiClient from '@/services/api/apiClient';

interface DescriptionResponse {
  description: string;
}
interface ThemeImageRequest {
  seed?: string;
}
interface ThemeImageResponse {
  imageUrl: string;
  s3Key: string;
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

  return useMutation<DescriptionResponse, Error, void>({
    mutationFn: async () => {
      try {
        const response = await apiClient.post<DescriptionResponse>(
          `/events/${eventCode}/ai/description`
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
        const qs = req.seed ? `?seed=${encodeURIComponent(req.seed)}` : '';
        const response = await apiClient.post<ThemeImageResponse>(
          `/events/${eventCode}/ai/theme-image${qs}`
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

export function useAiAnalyzeAbstract(speakerPoolId: string) {
  const { t } = useTranslation('organizer');

  return useMutation<AbstractResponse, Error, void>({
    mutationFn: async () => {
      try {
        const response = await apiClient.post<AbstractResponse>(
          `/speakers/${speakerPoolId}/ai/analyze-abstract`
        );
        return response.data;
      } catch {
        throw new Error(t('aiAssist.error'));
      }
    },
  });
}
