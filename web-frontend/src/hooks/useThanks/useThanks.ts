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
import type { ThanksCountResponse, ThanksNoteResponse } from '@/services/thanksService';

export const THANKS_QUERY_KEYS = {
  count: (eventCode: string) => ['thanks', eventCode] as const,
  /** Organizer notes view (Story 7.7) — same endpoint, returns notes[] with id + featured state. */
  admin: (eventCode: string) => ['thanks', 'admin', eventCode] as const,
};

export interface SubmitThanksVars {
  note?: string | null;
  turnstileToken?: string | null;
}

export interface SetFeaturedVars {
  id: string;
  featured: boolean;
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

/**
 * Organizer notes view (Story 7.7). Same GET as the public count, but an organizer caller also
 * receives notes[] (with id + featured state). Used by the Appreciation panel.
 */
export function useEventThanks(eventCode: string): UseQueryResult<ThanksCountResponse, Error> {
  return useQuery({
    queryKey: THANKS_QUERY_KEYS.admin(eventCode),
    queryFn: () => thanksService.getThanks(eventCode),
    enabled: !!eventCode,
    staleTime: 30 * 1000,
  });
}

/** Organizer feature-toggle (Story 7.7). Refreshes the notes list + the public marquee pool. */
export function useSetThanksFeatured(
  eventCode: string
): UseMutationResult<ThanksNoteResponse, Error, SetFeaturedVars> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, featured }) => thanksService.setThanksFeatured(eventCode, id, featured),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: THANKS_QUERY_KEYS.admin(eventCode) });
      queryClient.invalidateQueries({ queryKey: ['thanks', 'featured'] });
    },
  });
}
