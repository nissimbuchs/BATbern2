/**
 * Partner React Query Hooks (GREEN Phase - Story 2.8.1, Task 2b)
 *
 * React Query hooks for Partner Management:
 * - usePartners: List query with filters, sort, pagination (cache 2min)
 * - usePartnerStatistics: Statistics query (cache 5min)
 *
 * AC: 1 (Partner Directory Screen), 2 (Overview Statistics), 10 (Integration)
 */

import { useQuery, type UseQueryResult } from '@tanstack/react-query';
import {
  listPartners,
  getPartnerStatistics,
  type PartnerListResponse,
  type PartnerStatistics,
  type PartnerFilters,
  type PartnerSort,
  type PartnerPagination,
} from '@/services/api/partnerApi';

/**
 * usePartners - Fetch paginated list of partners with filters and sorting
 *
 * Query Key: ['partners', filters, sort, pagination]
 * Cache: 2 minutes (partner list is moderately volatile)
 *
 * @param filters - Tier and status filters
 * @param sort - Sort configuration (by engagement, name, tier, lastEvent)
 * @param pagination - Page and size parameters (zero-based indexing)
 * @returns UseQueryResult with PartnerListResponse
 */
export const usePartners = (
  filters: PartnerFilters,
  sort: PartnerSort,
  pagination: PartnerPagination
): UseQueryResult<PartnerListResponse, Error> => {
  return useQuery({
    queryKey: ['partners', filters, sort, pagination],
    queryFn: () => listPartners(filters, sort, pagination),
    staleTime: 2 * 60 * 1000, // 2 minutes (AC8: Performance)
    refetchOnWindowFocus: true, // Refetch when user returns to tab
  });
};

/**
 * usePartnerStatistics - Fetch partner overview statistics
 *
 * Query Key: ['partner-statistics']
 * Cache: 5 minutes (statistics change less frequently)
 *
 * Returns:
 * - Total partners count
 * - Active partners count
 * - Tier distribution (STRATEGIC, PLATINUM, GOLD, SILVER, BRONZE)
 * - Engaged percentage (Epic 8 - placeholder)
 *
 * @returns UseQueryResult with PartnerStatistics
 */
export const usePartnerStatistics = (): UseQueryResult<PartnerStatistics, Error> => {
  return useQuery({
    queryKey: ['partner-statistics'],
    queryFn: () => getPartnerStatistics(),
    staleTime: 5 * 60 * 1000, // 5 minutes (AC2: Overview Statistics)
    refetchOnWindowFocus: true,
  });
};
