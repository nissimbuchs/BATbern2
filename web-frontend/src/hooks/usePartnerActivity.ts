/**
 * usePartnerActivity Hook
 *
 * React Query hook for fetching partner activity timeline
 * Story 2.8.2: Partner Detail View
 *
 * Features:
 * - Fetches partner activity timeline for Activity tab
 * - Supports filtering by activity type
 * - 2-minute cache for activity data (more volatile)
 * - Lazy loading (only fetches when tab activated)
 *
 * AC: 6 (Activity Tab), 13 (Integration Tests)
 */

import { useQuery, type UseQueryResult } from '@tanstack/react-query';
import { getPartnerActivity, type ActivityFilters } from '@/services/api/partnerApi';
import type { ActivityResponse } from '@/services/api/partnerApi';

/**
 * usePartnerActivity - Fetch partner activity timeline with optional filters
 *
 * @param companyName - Company name (meaningful ID)
 * @param filters - Optional activity filters (type)
 * @returns UseQueryResult with array of activity entries
 *
 * Cache: 2 minutes (activity data is volatile)
 */
export const usePartnerActivity = (
  companyName: string,
  filters?: ActivityFilters
): UseQueryResult<ActivityResponse[], Error> => {
  return useQuery({
    queryKey: ['partner', companyName, 'activity', filters],
    queryFn: () => getPartnerActivity(companyName, filters),
    enabled: !!companyName,
    staleTime: 2 * 60 * 1000, // 2 minutes
  });
};
