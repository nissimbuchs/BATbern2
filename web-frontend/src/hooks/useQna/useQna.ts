/**
 * useQna Hook (Story 7.5 — "The Apéro Continues")
 *
 * React Query hooks for per-session Q&A.
 */

import {
  useMutation,
  useQuery,
  useQueryClient,
  type UseMutationResult,
  type UseQueryResult,
} from '@tanstack/react-query';
import axios from 'axios';
import * as qnaService from '@/services/qnaService';
import type { QnaWindowResponse, QnaPostResponse } from '@/services/qnaService';

export const QNA_QUERY_KEYS = {
  thread: (eventCode: string, sessionSlug: string) => ['qna', eventCode, sessionSlug] as const,
};

/**
 * Read a session's Q&A thread. A 404 (no window — event not completed) is NOT retried and surfaces
 * as `isError`, which the UI treats as "no Q&A for this session" (renders nothing).
 */
export function useSessionQna(
  eventCode: string,
  sessionSlug: string,
  enabled: boolean
): UseQueryResult<QnaWindowResponse, Error> {
  return useQuery({
    queryKey: QNA_QUERY_KEYS.thread(eventCode, sessionSlug),
    queryFn: () => qnaService.getThread(eventCode, sessionSlug),
    enabled: enabled && !!eventCode && !!sessionSlug,
    staleTime: 60 * 1000,
    retry: (failureCount, error) => {
      if (axios.isAxiosError(error) && error.response?.status === 404) {
        return false;
      }
      return failureCount < 2;
    },
  });
}

export interface AddPostVars {
  body: string;
  parentPostId?: string | null;
}

/** Post a question/answer; refreshes the thread on success. */
export function useAddQnaPost(
  eventCode: string,
  sessionSlug: string
): UseMutationResult<QnaPostResponse, Error, AddPostVars> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ body, parentPostId }) =>
      qnaService.addPost(eventCode, sessionSlug, body, parentPostId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: QNA_QUERY_KEYS.thread(eventCode, sessionSlug) });
    },
  });
}

/** Organizer takedown; refreshes the thread on success. */
export function useRemoveQnaPost(
  eventCode: string,
  sessionSlug: string
): UseMutationResult<void, Error, string> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (postId) => qnaService.removePost(eventCode, sessionSlug, postId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: QNA_QUERY_KEYS.thread(eventCode, sessionSlug) });
    },
  });
}
