/**
 * usePartnerVotes Hook
 *
 * React Query hook for fetching partner topic votes
 * Story 2.8.2: Partner Detail View
 *
 * Features:
 * - Fetches partner topic votes for Overview tab
 * - 5-minute cache for vote data
 * - Lazy loading (only fetches when tab activated)
 *
 * AC: 4 (Overview Tab), 13 (Integration Tests)
 */

import { useQuery, type UseQueryResult } from '@tanstack/react-query';
import { getPartnerVotes } from '@/services/api/partnerApi';
import type { TopicVoteResponse } from '@/services/api/partnerApi';

/**
 * usePartnerVotes - Fetch partner topic votes
 *
 * @param companyName - Company name (meaningful ID)
 * @returns UseQueryResult with array of topic votes
 *
 * Cache: 5 minutes (vote data is moderately volatile)
 */
export const usePartnerVotes = (
  companyName: string
): UseQueryResult<TopicVoteResponse[], Error> => {
  return useQuery({
    queryKey: ['partner', companyName, 'votes'],
    queryFn: () => getPartnerVotes(companyName),
    enabled: !!companyName,
    staleTime: 5 * 60 * 1000, // 5 minutes
  });
};
