/**
 * useFeaturedThanks Hook (Story 7.7 — curated thank-you marquee)
 *
 * React Query hook for the public curated featured thank-you notes. The server randomizes the
 * selection; a generous staleTime keeps the shuffle stable across renders within a visit.
 */

import { useQuery, type UseQueryResult } from '@tanstack/react-query';
import * as featuredThanksService from '@/services/featuredThanksService';
import type { FeaturedThanksResponse } from '@/services/featuredThanksService';

export const FEATURED_THANKS_QUERY_KEY = ['thanks', 'featured'] as const;

/** Up to `limit` random featured thank-you notes (public, cross-event). */
export function useFeaturedThanks(limit = 9): UseQueryResult<FeaturedThanksResponse[], Error> {
  return useQuery({
    queryKey: [...FEATURED_THANKS_QUERY_KEY, limit],
    queryFn: () => featuredThanksService.getFeaturedThanks(limit),
    staleTime: 5 * 60 * 1000,
  });
}
