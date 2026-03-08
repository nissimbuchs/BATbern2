/**
 * AI Prompt React Query Hooks
 *
 * Custom hooks for AI prompt data fetching and mutations.
 */

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { listAiPrompts, updateAiPrompt, resetAiPrompt } from '@/services/aiPromptService';

const QUERY_KEY = 'aiPrompts' as const;

export function useAiPrompts() {
  return useQuery({
    queryKey: [QUERY_KEY],
    queryFn: listAiPrompts,
  });
}

export function useUpdateAiPrompt() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ promptKey, promptText }: { promptKey: string; promptText: string }) =>
      updateAiPrompt(promptKey, promptText),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [QUERY_KEY] });
    },
  });
}

export function useResetAiPrompt() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (promptKey: string) => resetAiPrompt(promptKey),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [QUERY_KEY] });
    },
  });
}
