/**
 * useCompany Hook
 *
 * React Query hook for fetching single company detail with optional resource expansion
 * AC 8 (Company Detail View), AC 10 (Performance), AC 15 (Service Integration)
 */

import { useQuery } from '@tanstack/react-query';
import { companyApiClient } from '@/services/api/companyApi';

interface UseCompanyOptions {
  expand?: string[];
}

/**
 * Fetch single company by ID with optional resource expansion
 *
 * Features:
 * - 10 minute stale time (AC 10 - Performance)
 * - Resource expansion support (?expand=statistics,logo)
 * - Disabled query when ID is empty/undefined
 * - Unique cache keys based on ID + expansion options
 *
 * @param id - Company UUID
 * @param options - Optional expansion parameters (statistics, logo, etc.)
 * @returns React Query result with company detail data
 */
export const useCompany = (id: string, options?: UseCompanyOptions) => {
  return useQuery({
    queryKey: ['company', id, options],
    queryFn: () => companyApiClient.getCompany(id, options),
    staleTime: 10 * 60 * 1000, // 10 minutes (AC 10 - Performance requirement)
    gcTime: 15 * 60 * 1000, // 15 minutes garbage collection
    enabled: !!id && id.trim().length > 0, // Only fetch when valid ID provided
    refetchOnWindowFocus: true, // AC 14 - State Management requirement
  });
};
