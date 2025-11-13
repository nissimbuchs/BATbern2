/**
 * usePartnerDetail Hook
 *
 * React Query hook for fetching partner detail with resource expansion
 * Story 2.8.2: Partner Detail View
 *
 * Features:
 * - Resource expansion via include parameter (company,contacts,votes,meetings,activity)
 * - 5-minute cache for partner detail data
 * - Automatic refetching on stale data
 *
 * AC: 1 (Partner Detail Header), 13 (Integration Tests)
 */

import { useQuery, type UseQueryResult } from '@tanstack/react-query';
import { getPartnerDetail } from '@/services/api/partnerApi';
import type { PartnerResponse } from '@/services/api/partnerApi';

/**
 * usePartnerDetail - Fetch partner detail with optional resource expansion
 *
 * @param companyName - Company name (meaningful ID)
 * @param include - Optional comma-separated resources to expand
 * @returns UseQueryResult with partner detail data
 *
 * Cache: 5 minutes (partner data is moderately volatile)
 */
export const usePartnerDetail = (
  companyName: string,
  include?: string
): UseQueryResult<PartnerResponse, Error> => {
  return useQuery({
    queryKey: ['partner', companyName, include],
    queryFn: () => getPartnerDetail(companyName, include),
    enabled: !!companyName, // Only fetch if companyName is provided
    staleTime: 5 * 60 * 1000, // 5 minutes
  });
};
