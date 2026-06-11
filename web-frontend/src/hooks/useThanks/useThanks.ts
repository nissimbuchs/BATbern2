/**
 * useThanks Hook (Story 7.4 — "Thank the Organizers")
 *
 * React Query hooks for the public thank-the-organizers count + submit.
 */

import {
  useMutation,
  useQuery,
  useQueryClient,
  type UseMutationResult,
  type UseQueryResult,
} from '@tanstack/react-query';
import * as thanksService from '@/services/thanksService';
import type { ThanksCountResponse } from '@/services/thanksService';

export const THANKS_QUERY_KEYS = {
  count: (eventCode: string) => ['thanks', eventCode] as const,
};

export interface SubmitThanksVars {
  note?: string | null;
  turnstileToken?: string | null;
}

/** Public aggregate count for an event. */
export function useThanksCount(eventCode: string): UseQueryResult<ThanksCountResponse, Error> {
  return useQuery({
    queryKey: THANKS_QUERY_KEYS.count(eventCode),
    queryFn: () => thanksService.getThanks(eventCode),
    enabled: !!eventCode,
    staleTime: 60 * 1000,
  });
}

/** Submit a thank-you. On success the count query is updated with the returned aggregate. */
export function useSubmitThanks(
  eventCode: string
): UseMutationResult<ThanksCountResponse, Error, SubmitThanksVars> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ note, turnstileToken }) =>
      thanksService.submitThanks(eventCode, note, turnstileToken),
    onSuccess: (data) => {
      queryClient.setQueryData(THANKS_QUERY_KEYS.count(eventCode), data);
    },
  });
}
