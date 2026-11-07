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
        if (axiosErr?.response?.status === 503)
          throw new Error(t('aiAssist.disabled'), { cause: err });
        throw new Error(t('aiAssist.error'), { cause: err });
      }
    },
  });
}

interface ThemeImageJob {
  jobId: string;
  status: 'PENDING' | 'DONE' | 'FAILED';
  imageUrl?: string | null;
  s3Key?: string | null;
}

/**
 * 2026-11-07: an image takes about 40 s; the AWS HTTP API ends every request at 30 s. The POST
 * starts a job (202) and this hook polls it until DONE or FAILED, giving up after `maxWaitMs`.
 */
export function useAiGenerateThemeImage(
  eventCode: string,
  { pollMs = 3000, maxWaitMs = 150_000 }: { pollMs?: number; maxWaitMs?: number } = {}
) {
  const { t } = useTranslation('organizer');

  return useMutation<ThemeImageResponse, Error, ThemeImageRequest>({
    mutationFn: async (req) => {
      try {
        const qs = req.seed ? `?seed=${encodeURIComponent(req.seed)}` : '';
        const started = await apiClient.post<ThemeImageJob>(
          `/events/${eventCode}/ai/theme-image${qs}`
        );
        const jobId = started.data.jobId;
        const deadline = Date.now() + maxWaitMs;
        while (Date.now() < deadline) {
          await new Promise((resolve) => setTimeout(resolve, pollMs));
          const { data: job } = await apiClient.get<ThemeImageJob>(
            `/events/${eventCode}/ai/theme-image/jobs/${jobId}`
          );
          if (job.status === 'DONE' && job.imageUrl) {
            return { imageUrl: job.imageUrl, s3Key: job.s3Key ?? '' };
          }
          if (job.status === 'FAILED') {
            break;
          }
        }
      } catch {
        // fall through to the one error message
      }
      throw new Error(t('aiAssist.error'));
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
