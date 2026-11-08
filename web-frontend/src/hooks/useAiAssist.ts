import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import {
  analyzeAbstract,
  applyThemeImage,
  generateEventDescription,
  getThemeImageJob,
  startThemeImageJob,
  type AiAbstractAnalysisResponse,
  type AiDescriptionResponse,
} from '@/services/aiAssistService';

type DescriptionResponse = AiDescriptionResponse;
type AbstractResponse = AiAbstractAnalysisResponse;

interface ThemeImageRequest {
  seed?: string;
}
interface ThemeImageResponse {
  imageUrl: string;
  s3Key: string;
}

export function useAiGenerateDescription(eventCode: string) {
  const { t } = useTranslation('organizer');

  return useMutation<DescriptionResponse, Error, void>({
    mutationFn: async () => {
      try {
        return await generateEventDescription(eventCode);
      } catch (err: unknown) {
        const axiosErr = err as { response?: { status?: number } };
        if (axiosErr?.response?.status === 503)
          throw new Error(t('aiAssist.disabled'), { cause: err });
        throw new Error(t('aiAssist.error'), { cause: err });
      }
    },
  });
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
        const started = await startThemeImageJob(eventCode, req.seed);
        const jobId = started.jobId;
        const deadline = Date.now() + maxWaitMs;
        while (Date.now() < deadline) {
          await new Promise((resolve) => setTimeout(resolve, pollMs));
          const job = await getThemeImageJob(eventCode, jobId);
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
        await applyThemeImage(eventCode, imageUrl);
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
        return await analyzeAbstract(speakerPoolId);
      } catch {
        throw new Error(t('aiAssist.error'));
      }
    },
  });
}
