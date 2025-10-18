/**
 * useCompanies Hook
 *
 * React Query hook for fetching paginated company list with filters
 * AC 1 (Company List Display), AC 2 (Search & Filters), AC 10 (Performance), AC 15 (Service Integration)
 */

import { useQuery } from '@tanstack/react-query';
import { companyApiClient } from '@/services/api/companyApi';
import type { CompanyFilters, PaginationParams } from '@/types/company.types';

/**
 * Fetch companies with pagination and optional filters
 *
 * Features:
 * - 5 minute stale time (AC 10 - Performance)
 * - Automatic refetch on window focus
 * - Cache invalidation on mutations
 * - Unique query keys based on pagination + filters
 *
 * @param pagination - Page and limit parameters
 * @param filters - Optional filters (partner status, verified, industry, search query)
 * @param options - Optional resource expansion (e.g., { expand: ['logo', 'statistics'] })
 * @returns React Query result with company list data
 */
export const useCompanies = (
  pagination: PaginationParams,
  filters?: CompanyFilters,
  options?: { expand?: string[] }
) => {
  return useQuery({
    queryKey: ['companies', pagination, filters, options?.expand],
    queryFn: () => companyApiClient.getCompanies(pagination, filters, options),
    staleTime: 5 * 60 * 1000, // 5 minutes (AC 10 - Performance requirement)
    gcTime: 10 * 60 * 1000, // 10 minutes garbage collection
    refetchOnWindowFocus: true, // AC 14 - State Management requirement
  });
};
